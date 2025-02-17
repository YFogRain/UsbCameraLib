#include "camera_factory_usb_impl.h"
#include "camera_constants.h"
#include "camera_usb_constants.h"
#include "libuvc/libuvc_internal.h"
#include "unistd.h"
#include <signal.h>
#include "img_util.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"
#include <iostream>
#include <variant>
#include "android/native_window.h"

CameraFactoryUsbImpl::CameraFactoryUsbImpl(uvc_context_t *context, uvc_device_t *device,
                                           uvc_device_handle_t *deviceHandle, int fd)
        : mContext(context), mDevice(device), mDeviceHandle(deviceHandle), mFd(fd),
          mIsPreviewRunning(false), mPreviewWindow(nullptr),
          mDisplayTransformState(TRANSFORM_IDENTITY),
          frameMode(PREVIEW_FORMAT_YUY2),
          onFrameMethod(nullptr), theVM(nullptr), previewListener(nullptr),
          requestWidth(DEFAULT_PREVIEW_WIDTH), requestHeight(DEFAULT_PREVIEW_HEIGHT),
          requestMode(UVC_DATA_FORMAT_BGR),
          frameBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * 2) {
    // 初始化互斥锁
    pthread_mutex_init(&captureMutex, nullptr);
    pthread_cond_init(&captureCond, nullptr);

    pthread_mutex_init(&surfaceMutex, nullptr);
}

CameraFactoryUsbImpl::~CameraFactoryUsbImpl() {
    pthread_mutex_destroy(&captureMutex);
    pthread_cond_destroy(&captureCond);

    pthread_mutex_destroy(&surfaceMutex);

    if (mPreviewWindow) {
        ANativeWindow_release(mPreviewWindow);
    }
    if (previewListener && theVM) {
        JNIEnv *env = nullptr;
        if (theVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
            // 在析构函数中删除全局引用
            env->DeleteGlobalRef(previewListener);  // 删除全局引用
        }
    }
    theVM = nullptr;
    previewListener = nullptr;
    onFrameMethod = nullptr;

    if (LIKELY(mDeviceHandle)) {
        // 关闭对应的设备
        LOG_D("uvc_close mDeviceHandle");
        uvc_close(mDeviceHandle);
        mDeviceHandle = nullptr;
    }
    if (mDevice) {
        uvc_unref_device(mDevice);
        mDevice = nullptr;
    }
    if (mContext) {
        LOG_D("uvc_exit mContext");
        uvc_exit(mContext);
        mContext = nullptr;
    }
    if (mFd != 0) {
        close(mFd);
        mFd = 0;
    }
    LOG_D("断开连接结束");
}

bool CameraFactoryUsbImpl::setPreviewSize(int width, int height, int format) {
    requestWidth = width;
    requestHeight = height;
    frameMode = format;
    uvc_stream_ctrl_t ctrl;
    // 设置完成后需要提前获取一次，否则打开时无法正常获取到流
    uvc_error_t ret = uvc_get_stream(mDeviceHandle, &ctrl, getPreviewFormat(), requestWidth,
                                     requestHeight);
    LOG_D("设置预览分辨率同步获取预览流-setPreviewSize-结果:%d", ret);
    return true;
}

bool CameraFactoryUsbImpl::setDisplaySurface(ANativeWindow *preview_window) {
    pthread_mutex_lock(&surfaceMutex);
    {
        if (mPreviewWindow != preview_window) {
            if (mPreviewWindow) {
                ANativeWindow_release(mPreviewWindow);
            }
            mPreviewWindow = preview_window;
            if (LIKELY(mPreviewWindow)) {
                ANativeWindow_setBuffersGeometry(mPreviewWindow, frameWidth, frameHeight,
                                                 UVC_FORMAT_FRAME_WINDOW);
            }
            ImgUtils::setDisplayTransformState(mPreviewWindow, mDisplayTransformState);
        }
    }
    pthread_mutex_unlock(&surfaceMutex);
    return UVC_SUCCESS;
}

bool
CameraFactoryUsbImpl::setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int mode) {
    this->requestMode = mode;
    pthread_mutex_lock(&captureMutex);
    this->theVM = vm;
    if (!env->IsSameObject(previewListener, listener)) {
        onFrameMethod = nullptr;
        if (previewListener) {
            env->DeleteGlobalRef(previewListener);
        }
        previewListener = listener;
        if (listener) {
            jclass clazz = env->GetObjectClass(listener);
            if (clazz) {
                //宽高
                onFrameMethod = env->GetMethodID(clazz, "onFrame", "(IILjava/nio/ByteBuffer;)V");
            }
            env->ExceptionClear();
            if (!onFrameMethod) {
                env->DeleteGlobalRef(listener);
                previewListener = nullptr;
                LOG_E("设置监听失败");
                return false;
            }
        } else {
            env->DeleteGlobalRef(listener);
            onFrameMethod = nullptr;
            previewListener = nullptr;
            LOG_E("监听设置失败，listener为null");
        }
    } else {
        LOG_E("当前为同一个对象，无需设置");
    }
    pthread_mutex_unlock(&captureMutex);
    return true;
}

std::variant<std::monostate, std::pair<int, int>, std::string, int>
CameraFactoryUsbImpl::getSupportParameters(int type) {
    if (!mDeviceHandle) {
        return nullptr;
    }
    if (type == CAMERA_PARAMETER_PREVIEW_SIZE) {
        return getSupportedPreviewSizes();
    } else if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        uint8_t max;
        if (uvc_get_ae_mode(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
            LOG_D("自动曝光最大值:%d", max);
            return max >= 8 ? 1 : 0;
        }
    } else if (type == CAMERA_PARAMETER_EXPOSURE) {
        uint32_t min;
        uint32_t max;
        if (uvc_get_exposure_abs(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_exposure_abs(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_BRIGHTNESS) {
        int16_t min;
        int16_t max;
        if (uvc_get_brightness(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            LOG_D("brightness-最小值:%d", min);
            if (uvc_get_brightness(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                LOG_D("brightness-最大值:%d", max);
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_CONTRAST) {
        uint16_t min;
        uint16_t max;
        if (uvc_get_contrast(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_contrast(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_GAIN) {
        uint16_t min;
        uint16_t max;
        if (uvc_get_gain(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_gain(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_SATURATION) {
        uint16_t min;
        uint16_t max;
        if (uvc_get_saturation(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_saturation(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_ZOOM) {
        uint16_t min;
        uint16_t max;
        if (uvc_get_zoom_abs(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_zoom_abs(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_AUTO_FOCUS) { // 查看是否支持自动对焦
        uint8_t max;
        if (uvc_get_focus_auto(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
            return max >= 1 ? 1 : 0;
        } else {
            return 0;
        }
    } else if (type == CAMERA_PARAMETER_FOCUS) { // 查看是否支持设置焦距
        uint16_t min;
        uint16_t max;
        if (uvc_get_focus_abs(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_focus_abs(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_IRIS) { // 光圈
        uint16_t min;
        uint16_t max;
        if (uvc_get_iris_abs(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_iris_abs(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_AUTO_HUE) { // 自动色调
        uint8_t max;
        if (uvc_get_hue_auto(mDeviceHandle, &max, UVC_GET_MAX)) {
            return max >= 1;
        } else {
            return 0;
        }
    } else if (type == CAMERA_PARAMETER_HUE) { // 色调
        int16_t min;
        int16_t max;
        if (uvc_get_hue(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_hue(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_AUTO_WHITE_BALANCE) { // 自动色调
        uint8_t max;
        if (uvc_get_white_balance_temperature_auto(mDeviceHandle, &max, UVC_GET_MAX)) {
            return max >= 1;
        } else {
            return 0;
        }
    } else if (type == CAMERA_PARAMETER_WHITE_BALANCE) { // 白平衡
        uint16_t min;
        uint16_t max;
        if (uvc_get_white_balance_temperature(mDeviceHandle, &min, UVC_GET_MIN) == UVC_SUCCESS) {
            if (uvc_get_white_balance_temperature(mDeviceHandle, &max, UVC_GET_MAX) ==
                UVC_SUCCESS) {
                return std::make_pair(min, max);
            }
        }
    } else if (type == CAMERA_PARAMETER_PRIVACY) { // 是否支持隐私模式
        uint8_t max;
        if (uvc_get_privacy(mDeviceHandle, &max, UVC_GET_MAX) == UVC_SUCCESS) {
            return max >= 1 ? 1 : 0;
        } else {
            return 0;
        }
    }
    return std::monostate{};
}

bool CameraFactoryUsbImpl::setParameter(int type, int value) {
    // 如果是int类型，则可以设置下面的所有参数
    uvc_error_t ret = UVC_ERROR_IO;
    switch (type) {
        case CAMERA_PARAMETER_AUTO_EXPOSURE:
            ret = uvc_set_ae_mode(mDeviceHandle, value == 1 ? 8 : 1);
            break;
        case CAMERA_PARAMETER_EXPOSURE:
            ret = uvc_set_exposure_abs(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_BRIGHTNESS:
            ret = uvc_set_brightness(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_CONTRAST:
            ret = uvc_set_contrast(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_GAIN:
            ret = uvc_set_gain(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_SATURATION:
            ret = uvc_set_saturation(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_ZOOM:
            ret = uvc_set_zoom_abs(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_AUTO_FOCUS:
            ret = uvc_set_focus_auto(mDeviceHandle, value == 1 ? 1 : 0);
            break;
        case CAMERA_PARAMETER_FOCUS:
            ret = uvc_set_focus_abs(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_IRIS:
            ret = uvc_set_iris_abs(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_AUTO_HUE:
            ret = uvc_set_hue_auto(mDeviceHandle, value == 1 ? 1 : 0);
            break;
        case CAMERA_PARAMETER_HUE:
            ret = uvc_set_hue(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_AUTO_WHITE_BALANCE:
            ret = uvc_set_white_balance_temperature_auto(mDeviceHandle, value == 1 ? 1 : 0);
            break;
        case CAMERA_PARAMETER_WHITE_BALANCE:
            ret = uvc_set_white_balance_temperature(mDeviceHandle, value);
            break;
        case CAMERA_PARAMETER_PRIVACY:
            ret = uvc_set_privacy(mDeviceHandle, value == 1 ? 1 : 0);
            break;
        case CAMERA_PARAMETER_DISPLAY_TRANSFORM:
            pthread_mutex_lock(&surfaceMutex);
            mDisplayTransformState = value;
            bool result = ImgUtils::setDisplayTransformState(mPreviewWindow, value);
            pthread_mutex_unlock(&surfaceMutex);
            ret = result ? UVC_SUCCESS : UVC_ERROR_OTHER;
            break;

    }
    return ret == UVC_SUCCESS;
}

std::variant<std::monostate, int, std::string> CameraFactoryUsbImpl::getParameter(int type) {
    switch (type) {
        case CAMERA_PARAMETER_PREVIEW_SIZE:
            return std::to_string(requestWidth) + ":" + std::to_string(requestHeight) + ":" +
                   std::to_string(requestMode);
        case CAMERA_PARAMETER_AUTO_EXPOSURE:
            uint8_t mode;
            if (uvc_get_ae_mode(mDeviceHandle, &mode, UVC_GET_CUR) == UVC_SUCCESS) {
                return mode == 8 ? 1 : 0;
            }
            break;
        case CAMERA_PARAMETER_EXPOSURE:
            uint32_t exposure;
            if (uvc_get_exposure_abs(mDeviceHandle, &exposure, UVC_GET_CUR) == UVC_SUCCESS) {
                return static_cast<int>(exposure);
            }
            break;
        case CAMERA_PARAMETER_BRIGHTNESS:
            int16_t brightness;
            if (uvc_get_brightness(mDeviceHandle, &brightness, UVC_GET_CUR) == UVC_SUCCESS) {
                return brightness;
            }
            break;
        case CAMERA_PARAMETER_CONTRAST:
            uint16_t contrast;
            if (uvc_get_contrast(mDeviceHandle, &contrast, UVC_GET_CUR) == UVC_SUCCESS) {
                return contrast;
            }
            break;
        case CAMERA_PARAMETER_GAIN:
            uint16_t gain;
            if (uvc_get_gain(mDeviceHandle, &gain, UVC_GET_CUR) == UVC_SUCCESS) {
                return gain;
            }
            break;
        case CAMERA_PARAMETER_SATURATION:
            uint16_t saturation;
            if (uvc_get_saturation(mDeviceHandle, &saturation, UVC_GET_CUR) == UVC_SUCCESS) {
                return saturation;
            }
            break;
        case CAMERA_PARAMETER_ZOOM:
            uint16_t zoom;
            if (uvc_get_zoom_abs(mDeviceHandle, &zoom, UVC_GET_CUR) == UVC_SUCCESS) {
                return zoom;
            }
            break;
        case CAMERA_PARAMETER_DISPLAY_TRANSFORM:
            return mDisplayTransformState;

        case CAMERA_PARAMETER_AUTO_FOCUS:
            uint8_t autoFocus;
            if (uvc_get_focus_auto(mDeviceHandle, &autoFocus, UVC_GET_CUR) == UVC_SUCCESS) {
                return autoFocus == 1 ? 1 : 0;
            }
            break;
        case CAMERA_PARAMETER_FOCUS:
            uint16_t focus;
            if (uvc_get_focus_abs(mDeviceHandle, &focus, UVC_GET_CUR) == UVC_SUCCESS) {
                return focus;
            }
            break;
        case CAMERA_PARAMETER_IRIS:
            uint16_t iris;
            if (uvc_get_iris_abs(mDeviceHandle, &iris, UVC_GET_CUR) == UVC_SUCCESS) {
                return iris;
            }
            break;
        case CAMERA_PARAMETER_AUTO_HUE:
            uint8_t autoHue;
            if (uvc_get_hue_auto(mDeviceHandle, &autoHue, UVC_GET_CUR) == UVC_SUCCESS) {
                return autoHue == 1 ? 1 : 0;
            }
            break;
        case CAMERA_PARAMETER_HUE:
            int16_t hue;
            if (uvc_get_hue(mDeviceHandle, &hue, UVC_GET_CUR) == UVC_SUCCESS) {
                return hue;
            }
            break;
        case CAMERA_PARAMETER_AUTO_WHITE_BALANCE:
            uint8_t autoWhiteBalance;
            if (uvc_get_white_balance_temperature_auto(mDeviceHandle, &autoWhiteBalance,
                                                       UVC_GET_CUR) == UVC_SUCCESS) {
                return autoWhiteBalance; // 说明开启的自动模式
            }
            break;
        case CAMERA_PARAMETER_WHITE_BALANCE:
            // 其他情况，返回当前的模式值
            uint16_t whiteBalance;
            if (uvc_get_white_balance_temperature(mDeviceHandle, &whiteBalance, UVC_GET_CUR) ==
                UVC_SUCCESS) {
                return whiteBalance;
            }
            break;
        case CAMERA_PARAMETER_PRIVACY:
            uint8_t privacy;
            if (uvc_get_privacy(mDeviceHandle, &privacy, UVC_GET_CUR) == UVC_SUCCESS) {
                return privacy == 1 ? 1 : 0;
            }
            break;
    }
    return std::monostate{};
}

bool CameraFactoryUsbImpl::startPreview() {
    uvc_stream_ctrl_t ctrl;
    int ret = prepare_preview(&ctrl);
    if (ret != UVC_SUCCESS) {
        return false;
    }
    ret = do_preview(&ctrl);
    return ret == UVC_SUCCESS;
}

bool CameraFactoryUsbImpl::stopPreview() {
    LOG_D("停止预览开始");
    LOG_D("mIsRunning:%d", mIsPreviewRunning);
    if (mIsPreviewRunning) {
        // 停止预览线程
        mIsPreviewRunning = false;
        uvc_stop_streaming(mDeviceHandle);
        pthread_cond_signal(&captureCond);
        // 使用pthread_kill来检查线程是否存活
        if (pthread_kill(captureThread, 0) == ESRCH || pthread_join(captureThread, nullptr) != 0) {
            LOG_E("UVCPreview::当前线程已经结束，或者等待结束线程失败");
        }
    }
    LOG_D("停止预览结束");
    clearCaptureFrame();
    return UVC_SUCCESS;
}

int CameraFactoryUsbImpl::prepare_preview(uvc_stream_ctrl_t *ctrl) {
    LOG_D("获取对应的流控制器-size:%d-%d", requestWidth, requestHeight);
    uvc_error_t ret = uvc_get_stream(mDeviceHandle, ctrl, getPreviewFormat(), requestWidth,
                                     requestHeight);
    LOG_D("获取对应的流控制器-结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    LOG_D("当前设置的fps为:%d", 10000000 / ctrl->dwFrameInterval);
    // 获取当前预览流需要设置的宽高等数据
    uvc_frame_desc_t *frameDesc = uvc_get_frame_desc(mDeviceHandle, ctrl);
    if (frameDesc) {
        frameWidth = frameDesc->wWidth;
        frameHeight = frameDesc->wHeight;
    } else {
        frameWidth = requestWidth;
        frameHeight = requestHeight;
    }
    frameBytes = getPreviewBytesSize();
    if (mPreviewWindow) {
        ANativeWindow_setBuffersGeometry(mPreviewWindow, frameWidth, frameHeight,
                                         UVC_FORMAT_FRAME_WINDOW);
    }
    return UVC_SUCCESS;
}

int CameraFactoryUsbImpl::do_preview(uvc_stream_ctrl_t *ctrl) {
    clearCaptureFrame();
    uvc_error_t ret = uvc_start_streaming(mDeviceHandle, ctrl, uvc_stream_callback, (void *) this,
                                          0);
    LOG_D("开启预览流-结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    // 开启线程，启动捕获流操作
    mIsPreviewRunning = true;
    int result = pthread_create(&captureThread, nullptr, capture_thread_func, (void *) this);
    LOG_D("创建捕获数据线程结果-结果:%d", result);
    return ret;
}

void CameraFactoryUsbImpl::uvc_stream_callback(uvc_frame_t *frame, void *vptr_args) {
    // 获取当前的uvcPreview对象
    auto *preview = reinterpret_cast<CameraFactoryUsbImpl *>(vptr_args);
    // 如果当前不是正在预览，或者当前数据返回的是null，则直接下一回合
    if (!preview->mIsPreviewRunning || !frame) {
        LOG_E("当前数据返回的是null，则直接下一回合：：%d", preview->mIsPreviewRunning);
        return;
    }
    if ((frame->frame_format != UVC_FRAME_FORMAT_MJPEG &&
         frame->data_bytes < preview->frameBytes) || !frame->data) {
        LOG_E("当前数据大小不符合；；data_bytes:%zu,frameBytes:%zu",
              frame->data_bytes,
              preview->frameBytes);
        return;
    }
    // 获取bgr类型的数据数组
    uvc_frame_t *bgrFrame = ImgUtils::any2BGR(frame);
    // 将数据转换成rgb格式
    if (!bgrFrame) {
        LOG_E("数据转换失败");
        return;
    }
    preview->drawFrame(bgrFrame);
    // 数据发送出去
    preview->putFrame(bgrFrame);
}

void CameraFactoryUsbImpl::clearCaptureFrame() {
    pthread_mutex_lock(&captureMutex);
    if (!previewFrames.isEmpty()) {
        for (int i = 0; i < previewFrames.size(); ++i) {
            uvc_frame_t *&pFrame = previewFrames[i];
            uvc_free_frame(pFrame);
        }
        previewFrames.clear();
    }
    pthread_mutex_unlock(&captureMutex);
}

void CameraFactoryUsbImpl::putFrame(uvc_frame_t *frame) {
    // 执行锁定
    pthread_mutex_lock(&captureMutex);
    // 如果缓存池的数据满了，则吧第一帧的数据删除掉
    if (mIsPreviewRunning && previewFrames.size() < MAX_FRAME) {
        previewFrames.put(frame);
        frame = nullptr;
    }
    pthread_cond_signal(&captureCond);
    pthread_mutex_unlock(&captureMutex);
    if (frame) {
        // 如果没有写入到缓存，则直接释放当前对象
        uvc_free_frame(frame);
    }
}

uvc_frame_t *CameraFactoryUsbImpl::waitPreviewFrame() {
    uvc_frame_t *frame = nullptr;
    pthread_mutex_lock(&captureMutex);
    {
        // 如果当前内容为空，则等待获取数据，被唤醒
        if (previewFrames.isEmpty()) {
            pthread_cond_wait(&captureCond, &captureMutex);
        }
        // 如果当前是正在预览，并且预览数据大于0
        if (mIsPreviewRunning && !previewFrames.isEmpty()) {
            frame = previewFrames.remove(0);
        }
    }
    pthread_mutex_unlock(&captureMutex);
    return frame;
}

void CameraFactoryUsbImpl::drawFrame(uvc_frame_t *frame) {
    pthread_mutex_lock(&surfaceMutex);
//    //获取bgr类型的数据数组
    if (frame && mPreviewWindow) {
        auto *src = (uint8_t *) frame->data;
        ANativeWindow_Buffer buffer;
        // 锁定缓冲区以获取可以写入的内存区域
        if (ANativeWindow_lock(mPreviewWindow, &buffer, nullptr) == 0) {
            auto *dst = (uint8_t *) buffer.bits;
            uint32_t height = frame->height;
            uint32_t width = frame->width;
            // 将RGB数据复制到RGBA图像，并设置alpha值为255
            for (int i = 0, j = 0; i < width * height; ++i, j += 4) {
                dst[j] = src[i * 3 + 2];     // R
                dst[j + 1] = src[i * 3 + 1]; // G
                dst[j + 2] = src[i * 3]; // B
                dst[j + 3] = 0xFF;                 // A
            }
            // 解锁缓冲区
            ANativeWindow_unlockAndPost(mPreviewWindow);
        }
    }
    pthread_mutex_unlock(&surfaceMutex);
}

void *CameraFactoryUsbImpl::capture_thread_func(void *vptr_args) {
    auto *preview = reinterpret_cast<CameraFactoryUsbImpl *>(vptr_args);
    if (preview) {
        JNIEnv *env = nullptr;
        while (preview->mIsPreviewRunning) {
            //等待获取预览的数据
            uvc_frame_t *pFrame = preview->waitPreviewFrame();
            if (!pFrame)continue;
            //将数据回到给上层
            if (preview->theVM && !env) {
                preview->theVM->AttachCurrentThread(&env, nullptr);
            }
            if (env) {
                preview->callbackFrame(pFrame, env);
            }
            //释放销毁当前的frame
            uvc_free_frame(pFrame);
        }
        //删除对应创建
        if (preview->theVM && env) {
            preview->theVM->DetachCurrentThread();

        }
    }
    pthread_exit(nullptr);
}

uvc_frame_format CameraFactoryUsbImpl::getPreviewFormat() {
    switch (frameMode) {
        case PREVIEW_FORMAT_MJPEG:
            return UVC_FRAME_FORMAT_MJPEG;
        case PREVIEW_FORMAT_YUY2:
            return UVC_FRAME_FORMAT_YUYV;
        case PREVIEW_FORMAT_NV12:
            return UVC_FRAME_FORMAT_NV12; // 扩展支持 NV12 格式
        case PREVIEW_FORMAT_RGB24:
            return UVC_FRAME_FORMAT_RGB; // 扩展支持 RGB 格式
        case PREVIEW_FORMAT_BGR24:
            return UVC_FRAME_FORMAT_BGR; // 扩展支持 BGR 格式
        default:
            return UVC_FRAME_FORMAT_YUYV;
    }
}

size_t CameraFactoryUsbImpl::getPreviewBytesSize() {
    size_t previewSizes = frameWidth * frameHeight;
    switch (frameMode) {
        case PREVIEW_FORMAT_MJPEG:
            return previewSizes;
        case PREVIEW_FORMAT_YUY2:
            return previewSizes * 2;
        case PREVIEW_FORMAT_NV12:
            return previewSizes * 3 / 2; // 扩展支持 NV12 格式
        case PREVIEW_FORMAT_RGB24:
            return previewSizes * 3; // 扩展支持 RGB 格式
        case PREVIEW_FORMAT_BGR24:
            return previewSizes * 3; // 扩展支持 BGR 格式
        default:
            return previewSizes * 2;
    }
}


std::string CameraFactoryUsbImpl::getSupportedPreviewSizes() {
    if (!mDeviceHandle) {
        return std::string();
    }
    if (!mDeviceHandle->info->stream_ifs) {
        return std::string();
    }
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);

    writer.StartArray();
    // 循环读取数据
    uvc_streaming_interface_t *stream_if;
    DL_FOREACH(mDeviceHandle->info->stream_ifs, stream_if) {
        uvc_format_desc_t *fmt_desc;
        uvc_frame_desc_t *frame_desc;
        DL_FOREACH(stream_if->format_descs, fmt_desc) {
            int formatType = getFormatType(fmt_desc->bDescriptorSubtype);
            // 检查格式，如果不支持则直接跳过
            if (formatType == -1)
                continue;
            DL_FOREACH(fmt_desc->frame_descs, frame_desc) {
                LOG_D("=======================分辨率%d=%d*%d====================",
                      formatType,
                      frame_desc->wWidth, frame_desc->wHeight);
                writer.StartObject();
                // width
                writer.String("width");
                writer.Uint64(frame_desc->wWidth);

                // height
                writer.String("height");
                writer.Uint64(frame_desc->wHeight);
                // 当前类型
                writer.String("format");
                writer.Uint64(formatType);

                writer.EndObject();
            }
        }
    }
    writer.EndArray();
    return std::string(buffer.GetString());
}

// 根据格式描述符的子类型返回格式类型
int CameraFactoryUsbImpl::getFormatType(uint8_t descriptorSubtype) {
    switch (descriptorSubtype) {
        case UVC_VS_FORMAT_UNCOMPRESSED:
            LOG_D("当前为YUV类型");
            return PREVIEW_FORMAT_YUY2;
        case UVC_VS_FORMAT_MJPEG:
            LOG_D("当前为MJPEG类型");
            return PREVIEW_FORMAT_MJPEG;
        default:
            LOG_D("不支持的格式类型");
            return -1;
    }
}

void CameraFactoryUsbImpl::callbackFrame(uvc_frame_t *frame, JNIEnv *env) {
    if (!env || !previewListener || !onFrameMethod) {
        LOG_D("callbackFrame-return");
        return;
    }
    auto outImg = ImgUtils::bgr2Any((uint8_t *) frame->data, frame->width, frame->height,
                                    requestMode);
    if (outImg.empty()) {
        return;
    }
    // 释放源数据
    int data_bytes = outImg.total() * outImg.elemSize();
    if (outImg.data && data_bytes > 0) {
        jobject buf = env->NewDirectByteBuffer(outImg.data, data_bytes);
        if (buf) {
            env->CallVoidMethod(previewListener, onFrameMethod, outImg.cols, outImg.rows, buf);
            if (env->ExceptionCheck()) {
                LOG_D("ExceptionCheck");
                env->ExceptionDescribe();
            }
            env->ExceptionClear();
            env->DeleteLocalRef(buf);
        }
    }
}


