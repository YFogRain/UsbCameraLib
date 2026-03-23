//
// Created on 2025/5/18.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".
#include "camera_constants.h"
#include "unistd.h"
#include "camera_device_usb.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"
#include "libuvc/libuvc_internal.h"

CameraDeviceUsbImpl::CameraDeviceUsbImpl(uvc_context_t *context,
                                         uvc_device_t *device,
                                         uvc_device_handle_t *deviceHandle,
                                         int fd)
        : mContext(context), mDevice(device), mDeviceHandle(deviceHandle), mFd(fd) {
    mCameraStream = new CameraStreamUsbImpl(deviceHandle);
}

CameraDeviceUsbImpl::~CameraDeviceUsbImpl() {
    mCameraStream->stopPreview();
    mCameraStream->releasePreviewFunc();
    releaseButtonListener(); // 释放button的监听
    delete mCameraStream;
    mCameraStream = nullptr;
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

bool CameraDeviceUsbImpl::setParameter(int type, int value) {
    LOG_D("---开始设置%d的参数信息", type);
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
        case CAMERA_PARAMETER_ORIENTATION:
            LOG_D("-设置预览方向参数");
            return mCameraStream->setDisplayOrientation(value);
        case CAMERA_PARAMETER_MIRROR:
            LOG_D("-设置预览方向参数");
            return mCameraStream->setJpegMirrorState(value == 1 ? 1 : 0);
    }
    return ret == UVC_SUCCESS;
}

std::variant<std::monostate, int, std::string> CameraDeviceUsbImpl::getParameter(int type) {
    switch (type) {
        case CAMERA_PARAMETER_PREVIEW_SIZE:
            return mCameraStream->getCurrentPreviewSize();
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
        case CAMERA_PARAMETER_ORIENTATION:
            return mCameraStream->getDisplayOrientation();

        case CAMERA_PARAMETER_MIRROR:
            return mCameraStream->getJpegMirrorState();
    }
    return std::monostate{};
}

std::variant<std::monostate, std::pair<int, int>, std::string, int>
CameraDeviceUsbImpl::getSupportParameters(int type) {
    if (!mDeviceHandle) {
        return std::monostate{};
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

std::string CameraDeviceUsbImpl::getSupportedPreviewSizes() {
    if (!mDeviceHandle) {
        LOG_E("==未获取到文件描述符");
        return std::string();
    }
    if (!mDeviceHandle->info || !mDeviceHandle->info->stream_ifs) {
        LOG_E("==没有流控信息");
        return std::string();
    }
    LOG_D("UVC版本: %x.%02x",
          mDeviceHandle->info->ctrl_if.bcdUVC >> 8,
          mDeviceHandle->info->ctrl_if.bcdUVC & 0xFF);

    LOG_D("开始获取当前的分辨率信息。。");
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);

    writer.StartArray();
    // 循环读取数据
    uvc_streaming_interface_t *stream_if;
    DL_FOREACH(mDeviceHandle->info->stream_ifs, stream_if) {
        uvc_format_desc_t *fmt_desc;
        uvc_frame_desc_t *frame_desc;
        LOG_E("当前的接口数量。。%d", stream_if->bInterfaceNumber);
        DL_FOREACH(stream_if->format_descs, fmt_desc) {
            int formatType = getFormatType(fmt_desc->bDescriptorSubtype);
            // 检查格式，如果不支持则直接跳过
            if (formatType == -1)
                continue;
            // 当前类型
            writer.StartObject();
            writer.String("format");
            writer.Uint64(formatType);

            writer.String("sizes");
            writer.StartArray();
            DL_FOREACH(fmt_desc->frame_descs, frame_desc) {
                writer.StartObject();
                // width
                writer.String("width");
                writer.Uint64(frame_desc->wWidth);

                // height
                writer.String("height");
                writer.Uint64(frame_desc->wHeight);


                writer.EndObject();
            }

            writer.EndArray();
            writer.EndObject();
        }
    }
    writer.EndArray();
    return {buffer.GetString()};
}

// 根据格式描述符的子类型返回格式类型
int CameraDeviceUsbImpl::getFormatType(uint8_t descriptorSubtype) {
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

bool CameraDeviceUsbImpl::setButtonListener(JavaVM *vm, JNIEnv *env, jobject listener) {
    LOG_D("开始初始化设置监听。。。。。。。。。。");
    std::lock_guard<std::mutex> lock(buttonMutex);
    // 先释放旧的（包含 callback）
    // 1️⃣ 释放旧的 Java listener（不动 UVC callback）
    if (buttonListener && theVM) {
        JNIEnv *envOld = nullptr;
        if (theVM->GetEnv((void **) &envOld, JNI_VERSION_1_6) == JNI_OK) {
            envOld->DeleteGlobalRef(buttonListener);
        } else if (theVM->AttachCurrentThread(&envOld, nullptr) == JNI_OK) {
            envOld->DeleteGlobalRef(buttonListener);
            theVM->DetachCurrentThread();
        }
    }
    buttonListener = nullptr;
    onButtonMethod = nullptr;
    theVM = nullptr;
    LOG_D("旧资源释放完毕");
    if (!vm || !env || !listener) {
        LOG_E("listener is null");
        return true;
    }
    this->theVM = vm;
    this->buttonListener = env->NewGlobalRef(listener);;
    if (!this->buttonListener) {
        LOG_E("监听设置失败，listener为null");
        return false;
    }
    jclass buttonClass = env->GetObjectClass(listener);
    if (buttonClass) {
        //宽高
        this->onButtonMethod = env->GetMethodID(buttonClass, "buttonClick", "(II)V");
    }
    env->ExceptionClear();
    if (!onButtonMethod) {
        env->DeleteGlobalRef(listener);
        this->buttonListener = nullptr;
        LOG_E("设置监听失败");
        return false;
    }
    if (!buttonCallbackRegistered && mDeviceHandle) {
        uvc_set_button_callback(mDeviceHandle, CameraDeviceUsbImpl::uvc_button_callback, this);
        buttonCallbackRegistered = true;
    }

    return true;
}

void CameraDeviceUsbImpl::releaseButtonListener() {
    std::lock_guard<std::mutex> lock(buttonMutex);
    // ✅ 先关闭 callback（非常关键）
    if (buttonCallbackRegistered && mDeviceHandle) {
        uvc_set_button_callback(mDeviceHandle, nullptr, nullptr);
        buttonCallbackRegistered = false;
    }
    if (buttonListener && theVM) {
        JNIEnv *env = nullptr;
        if (theVM->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(buttonListener);
        } else if (theVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            env->DeleteGlobalRef(buttonListener);
            theVM->DetachCurrentThread();
        }
    }
    buttonListener = nullptr;
    onButtonMethod = nullptr;
    theVM = nullptr;
    LOG_D("Button listener released");
}

/**
* 按钮回调
*/
void CameraDeviceUsbImpl::uvc_button_callback(int button, int state, void *user_ptr) {
    LOG_D("CameraDeviceButton = 收到按钮事件= {button=%d,state=%d}", button, state);
    if (!user_ptr) return;
    auto *device = static_cast<CameraDeviceUsbImpl *>(user_ptr);
    device->onButtonStateCallback(button, state);
}

void CameraDeviceUsbImpl::onButtonStateCallback(int type, int state) {
    std::lock_guard<std::mutex> lock(buttonMutex);
    if (!theVM || !buttonListener || !onButtonMethod) return;
    JNIEnv *env = nullptr;
    bool needDetach = false;
    // 获取 JNIEnv（当前线程是 libusb 线程）
    if (theVM->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if (theVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOG_E("AttachCurrentThread failed");
            return;
        }
        needDetach = true;
    }
    // 调用 Java
    env->CallVoidMethod(buttonListener, onButtonMethod, type, state);
    // 异常检查（很重要）
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOG_E("Java exception in onButton");
    }
    if (needDetach) {
        theVM->DetachCurrentThread();
    }
}

