#include "camera_factory_v4l2_impl.h"
#include "Log.h"
#include <signal.h>
#include <sys/mman.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <android/native_window.h>
#include "camera_constants.h"
#include "img_util.h"
#include "linux/v4l2-subdev.h"
#include "video_v4l2_utils.h"


CameraFactoryV4L2Impl::CameraFactoryV4L2Impl(int fd) : mVideoFd(fd), mPreviewWindow(nullptr),
        mIsPreviewRunning(false), previewWidth(640), previewHeight(480),
        requestMode(UVC_DATA_FORMAT_BGR), previewFormat(PREVIEW_FORMAT_YUY2), captureWidth(640),
        captureHeight(480), captureFormat(PREVIEW_FORMAT_YUY2), lastFrame(nullptr),
        captureBuffers(nullptr), captureBufferLength(0), mDisplayTransformState(TRANSFORM_IDENTITY),
        theVM(nullptr), previewListener(nullptr), onFrameMethod(nullptr) {
    // 初始化互斥锁
    pthread_mutex_init(&surfaceMutex, nullptr);
    // 初始化互斥锁
    pthread_mutex_init(&previewMutex, nullptr);
    pthread_cond_init(&previewCond, nullptr);

    // 初始化互斥锁
    pthread_mutex_init(&callbackMutex, nullptr);
    pthread_cond_init(&callbackCond, nullptr);
}

CameraFactoryV4L2Impl::~CameraFactoryV4L2Impl() {
    pthread_mutex_destroy(&previewMutex);
    pthread_cond_destroy(&previewCond);

    pthread_mutex_destroy(&surfaceMutex);

    pthread_mutex_destroy(&callbackMutex);
    pthread_cond_destroy(&callbackCond);

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

    if (mVideoFd != -1) {
        close(mVideoFd);
    }
    mVideoFd = -1;
}

bool CameraFactoryV4L2Impl::setDisplaySurface(ANativeWindow *preview_window) {
    pthread_mutex_lock(&surfaceMutex);
    {
        if (mPreviewWindow != preview_window) {
            if (mPreviewWindow) {
                ANativeWindow_release(mPreviewWindow);
            }
            mPreviewWindow = preview_window;
            if (LIKELY(mPreviewWindow)) {
                ANativeWindow_setBuffersGeometry(mPreviewWindow, captureWidth, captureHeight, UVC_FORMAT_FRAME_WINDOW);
            }
            ImgUtils::setDisplayTransformState(mPreviewWindow, mDisplayTransformState);
        }
    }
    pthread_mutex_unlock(&surfaceMutex);
    return true;
}

bool CameraFactoryV4L2Impl::setPreviewSize(int width, int height, int format) {
    if (mVideoFd == -1) {
        return false;
    }
    bool result = VideoV4L2Utils::setStreamPreviewSize(mVideoFd, format, width, height);
    previewWidth = width;
    previewHeight = height;
    previewFormat = format;
    return result;
}

bool CameraFactoryV4L2Impl::startPreview() {
    if (mVideoFd == -1) {
        return false;
    }
    // 1. 获取宽高信息，设置到当前调用位置
    struct v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(mVideoFd, VIDIOC_G_FMT, &fmt) == 0) { // 获取当前的宽高配置信息
        captureWidth = fmt.fmt.pix.width;
        captureHeight = fmt.fmt.pix.height;
        captureFormat = VideoV4L2Utils::v4l2FormatToInt(fmt.fmt.pix.pixelformat);
    } else {
        captureWidth = previewWidth;
        captureHeight = previewHeight;
        captureFormat = previewFormat;
    }
    LOG_D("当前使用的分辨率信息:%d*%d ,format:%d", captureWidth, captureHeight, captureFormat);
    // 设置窗口的宽高
    if (mPreviewWindow) {
        ANativeWindow_setBuffersGeometry(mPreviewWindow, captureWidth, captureHeight, UVC_FORMAT_FRAME_WINDOW);
    }
    // 2. 设置缓冲区buffer
    if (!prepare_mmap()) { // 初始化缓冲区
        LOG_E("缓冲区准备失败");
        return false;
    }
    // 3. 打开流
    if (!startCameraStream()) { // 视频流启动失败
        stopPreview();
        return false;
    }
    // 4. 启动成功后，开启子线程，循环读取数据
    mIsPreviewRunning = true;
    int result = pthread_create(&captureThread, nullptr, capture_thread_func, (void *) this);
    if (result != 0) {
        stopPreview();
        return false;
    }
    // 5. 创建预览的读取线程
    result = pthread_create(&previewThread, nullptr, preview_thread_func, (void *) this);
    if (result != 0) {
        stopPreview();
        return false;
    }
    // 6. 创建回调线程
    pthread_create(&callbackThread, nullptr, callback_thread_func, (void *) this);
    return true;
}

bool CameraFactoryV4L2Impl::prepare_mmap() {
    // 请求缓冲区，
    struct v4l2_requestbuffers req;
    memset(&req, 0, sizeof(req));
    req.count = 4;
    req.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    req.memory = V4L2_MEMORY_MMAP;

    if (ioctl(mVideoFd, VIDIOC_REQBUFS, &req) < 0) {
        LOG_E("请求缓冲区失败,错误码:%d", errno);
        return false;
    }
    captureBufferLength = req.count;
    captureBuffers = new Buffer[req.count];
    // 初始化缓冲区
    for (unsigned int i = 0; i < req.count; i++) {
        struct v4l2_buffer buf;
        memset(&buf, 0, sizeof(buf));
        buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = i;
        if (ioctl(mVideoFd, VIDIOC_QUERYBUF, &buf) < 0) {
            LOG_E("获取缓冲区数据,错误码:%d", errno);
            return false;
        }
        captureBuffers[i].length = buf.length;
        captureBuffers[i].start = mmap(NULL, buf.length,
                PROT_READ | PROT_WRITE, MAP_SHARED, mVideoFd, buf.m.offset);
        if (captureBuffers[i].start == MAP_FAILED) {
            LOG_E("获取缓冲区数据,错误码:%d", errno);
            return false;
        }

        // Queue the buffer
        if (ioctl(mVideoFd, VIDIOC_QBUF, &buf) < 0) {
            LOG_E("放回缓冲区数据失败,错误码:%d", errno);
            return false;
        }
    }
    return true;
}

bool CameraFactoryV4L2Impl::startCameraStream() {
    enum v4l2_buf_type bufType = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(mVideoFd, VIDIOC_STREAMON, &bufType) < 0) {
        LOG_E("视频流启动失败, 错误码: %d", errno);
        return false;
    }
    return true;
}

bool CameraFactoryV4L2Impl::stopPreview() {
    if (mIsPreviewRunning) {
        mIsPreviewRunning = false;
        LOG_D("开始停止线程。。。");
        enum v4l2_buf_type bufType = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        if (ioctl(mVideoFd, VIDIOC_STREAMOFF, &bufType) < 0) {
            LOG_E("停止视频流失败, 错误码: %d", errno);
        }
        // 使用pthread_kill来检查线程是否存活
        if (pthread_kill(captureThread, 0) == ESRCH || pthread_join(captureThread, nullptr) != 0) {
            LOG_E("captureThread::当前线程已经结束，或者等待结束线程失败");
        }
        // 使用pthread_kill来检查线程是否存活
        pthread_cond_signal(&previewCond);
        if (pthread_kill(previewThread, 0) == ESRCH || pthread_join(previewThread, nullptr) != 0) {
            LOG_E("captureThread::当前线程已经结束，或者等待结束线程失败");
        }
        pthread_cond_signal(&callbackCond);
        if (pthread_kill(callbackThread, 0) == ESRCH || pthread_join(callbackThread, nullptr) != 0) {
            LOG_E("callbackThread::当前线程已经结束，或者等待结束线程失败");
        }
        LOG_D("停止线程结束。。。");
    }
    cleanup_buffers();
    clearCallbackFrame();
    clearCaptureFrame();
    return true;
}

void CameraFactoryV4L2Impl::cleanup_buffers() {
    if (captureBufferLength > 0 && captureBuffers) {
        for (int i = 0; i < captureBufferLength; i++) {
            munmap(captureBuffers[i].start, captureBuffers[i].length);
        }
        delete[] captureBuffers;
    }
    // VIDIOC_REQBUFS: 清除buffer
    v4l2_requestbuffers req_buffers{};
    req_buffers.type = V4L2_BUF_TYPE_VIDEO_CAPTURE; // 支持的设备视频输入类型
    req_buffers.memory = V4L2_MEMORY_MMAP;
    req_buffers.count = 0;
    ioctl(mVideoFd, VIDIOC_REQBUFS, &req_buffers);
    captureBuffers = nullptr;
    captureBufferLength = 0;
}

int CameraFactoryV4L2Impl::loadTypeToId(int type) {
    int id = -1;
    switch (type) {
        case CAMERA_PARAMETER_AUTO_EXPOSURE:
            id = V4L2_CID_EXPOSURE_AUTO;
            break;
        case CAMERA_PARAMETER_EXPOSURE:
            id = V4L2_CID_EXPOSURE_ABSOLUTE;
            break;
        case CAMERA_PARAMETER_BRIGHTNESS:
            id = V4L2_CID_BRIGHTNESS;
            break;
        case CAMERA_PARAMETER_CONTRAST:
            id = V4L2_CID_CONTRAST;
            break;
        case CAMERA_PARAMETER_SATURATION:
            id = V4L2_CID_SATURATION;
            break;
        case CAMERA_PARAMETER_GAIN:
            id = V4L2_CID_GAIN;
            break;
        case CAMERA_PARAMETER_ZOOM:
            id = V4L2_CID_ZOOM_ABSOLUTE;
            break;
        case CAMERA_PARAMETER_AUTO_FOCUS:
            id = V4L2_CID_FOCUS_AUTO;
            break;
        case CAMERA_PARAMETER_FOCUS:
            id = V4L2_CID_FOCUS_ABSOLUTE;
            break;
        case CAMERA_PARAMETER_IRIS: // 光圈
            id = V4L2_CID_IRIS_ABSOLUTE;
            break;
        case CAMERA_PARAMETER_AUTO_HUE: // 自动变化色调
            id = V4L2_CID_HUE_AUTO;
            break;
        case CAMERA_PARAMETER_HUE: // 色调
            id = V4L2_CID_HUE;
            break;
        case CAMERA_PARAMETER_WHITE_BALANCE: // 白平衡
            id = V4L2_CID_WHITE_BALANCE_TEMPERATURE;
            break;
        case CAMERA_PARAMETER_SCENE_MODE: // 场景模式
            id = V4L2_CID_SCENE_MODE;
            break;
        case CAMERA_PARAMETER_PRIVACY: // 隐私模式
            id = V4L2_CID_PRIVACY;
            break;
    }
    return id;
}

int CameraFactoryV4L2Impl::loadValueToPutValue(int type, int value) {
    // 如果是int类型，则可以设置下面的所有参数
    switch (type) {
        case CAMERA_PARAMETER_AUTO_EXPOSURE:
            return value == 1 ? V4L2_EXPOSURE_AUTO : V4L2_EXPOSURE_MANUAL;
        case CAMERA_PARAMETER_AUTO_HUE:
            return value == 1 ? 1 : 0;
        case CAMERA_PARAMETER_AUTO_FOCUS:
            return value == 1 ? 1 : 0;
        case CAMERA_PARAMETER_PRIVACY:
            return value == 1 ? 1 : 0;
        default:
            return value;
    }
}

std::variant<std::monostate, int, std::string> CameraFactoryV4L2Impl::getParameter(int type) {
    if (mVideoFd == -1) {
        return std::monostate{};
    }
    if (type == CAMERA_PARAMETER_PREVIEW_SIZE) {
        return std::to_string(captureWidth) + ":" + std::to_string(captureHeight) + ":" +
               std::to_string(captureFormat);
    }
    if (type == CAMERA_PARAMETER_DISPLAY_TRANSFORM) {
        return mDisplayTransformState;
    }
    int id = loadTypeToId(type);
    if (id == -1) {
        return std::monostate{};
    }
    auto result = VideoV4L2Utils::getParameter(mVideoFd, id);
    if (!std::holds_alternative<int>(result)) {
        return std::monostate{};
    }
    auto value = std::get<int>(result); // 获取到的范围信息
    LOG_D("当前%d参数的值为:%d", type, value);
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        return value == V4L2_EXPOSURE_AUTO ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_FOCUS) {
        return value == 1 ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_HUE) {
        return value == 1 ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_PRIVACY) {
        return value == 1 ? 1 : 0;
    } else {
        return value;
    }
}

bool CameraFactoryV4L2Impl::setParameter(int type, int value) {
    if (mVideoFd == -1) {
        return false;
    }
    // 如果是int类型，则可以设置下面的所有参数
    if (type == CAMERA_PARAMETER_DISPLAY_TRANSFORM) {
        pthread_mutex_lock(&surfaceMutex);
        mDisplayTransformState = value;
        bool result = ImgUtils::setDisplayTransformState(mPreviewWindow, value);
        pthread_mutex_unlock(&surfaceMutex);
        return result;
    }
    int id = loadTypeToId(type);
    if (id == -1) {
        return false;
    }
    return VideoV4L2Utils::setParameter(mVideoFd, id, loadValueToPutValue(type, value));
}

std::variant<std::monostate, std::pair<int, int>, std::string, int> CameraFactoryV4L2Impl::getSupportParameters(int type) {
    if (mVideoFd == -1) {
        return std::monostate{};
    }
    if (type == CAMERA_PARAMETER_PREVIEW_SIZE) {
        return VideoV4L2Utils::getSupportPreviewSize(mVideoFd);
    }
    int id = loadTypeToId(type);
    if (id == -1) {
        return std::monostate{};
    }
    auto result = VideoV4L2Utils::getSupportParameter(mVideoFd, id);
    if (!std::holds_alternative<std::pair<int, int>>(result)) {
        return std::monostate{};
    }
    auto autoExposurePair = std::get<std::pair<int, int>>(result); // 获取到的范围信息
    LOG_D("当前支持%d类型参数:[%d,%d]", type, autoExposurePair.first, autoExposurePair.second);
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        return autoExposurePair.second >= V4L2_EXPOSURE_MANUAL ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_FOCUS) {
        return autoExposurePair.second >= V4L2_AUTO_FOCUS_RANGE_NORMAL ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_HUE) {
        return autoExposurePair.second >= 1;
    } else if (type == CAMERA_PARAMETER_PRIVACY) {
        return autoExposurePair.second >= 1;
    } else if (type == CAMERA_PARAMETER_AUTO_WHITE_BALANCE) {
        return autoExposurePair.second >= 1;
    } else {
        return autoExposurePair;
    }
}

bool CameraFactoryV4L2Impl::setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int mode) {
    this->requestMode = mode;
    pthread_mutex_lock(&callbackMutex);
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
    pthread_mutex_unlock(&callbackMutex);
    return true;
}

void *CameraFactoryV4L2Impl::capture_thread_func(void *vptr_args) {
    CameraFactoryV4L2Impl *cameraFactory = static_cast<CameraFactoryV4L2Impl *>(vptr_args);
    struct v4l2_buffer buf;
    buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    buf.memory = V4L2_MEMORY_MMAP;
    while (cameraFactory->mIsPreviewRunning && cameraFactory->mVideoFd != -1) {
        // 从缓冲区队列中获取帧
        if (ioctl(cameraFactory->mVideoFd, VIDIOC_DQBUF, &buf) < 0) {
            LOG_E("从缓冲区队列中获取帧失败, 错误码: %d", errno);
            continue;
        }
        if (!(buf.flags & V4L2_BUF_FLAG_ERROR)) {
            if (cameraFactory->captureBuffers && buf.index >= 0 &&
                buf.index < cameraFactory->captureBufferLength) {
                uint8_t *frameData = (uint8_t *) cameraFactory->captureBuffers[buf.index].start;
                if (frameData) {
                    // 将数据复制到外部，然后释放当前缓冲区
                    cameraFactory->putPreviewFrame(frameData, buf.bytesused);
                }
            }
        } else {
            LOG_E("当前获取到的帧数据错误,flags:%d 错误码: %d", buf.flags, errno);
        }
        // 处理完毕后，将缓冲区重新放回队列
        if (ioctl(cameraFactory->mVideoFd, VIDIOC_QBUF, &buf) < 0) {
            LOG_E("将缓冲区放回队列失败, 错误码: %d", errno);
        }
    }
    return nullptr;
}

void *CameraFactoryV4L2Impl::preview_thread_func(void *vptr_args) {
    CameraFactoryV4L2Impl *cameraFactory = static_cast<CameraFactoryV4L2Impl *>(vptr_args);
    LOG_D("=====开启循环读取预览帧");
    if (cameraFactory) {
        LOG_E("=====mIsRunning：：：%d", cameraFactory->mIsPreviewRunning);
        while (cameraFactory->mIsPreviewRunning) {
            // 等待获取预览的数据
            video_frame_t *pFrame = cameraFactory->waitPreviewFrame();
            if (!pFrame) {
                continue;
            }
            // 先将数据转换为bgr格式，方便后续操作
            auto bgrImg = ImgUtils::any2BGR(pFrame->data, pFrame->dataSize, pFrame->format, pFrame->width, pFrame->height);
            if (!bgrImg.empty()) {                                       // 如果数据不为空
                size_t bytesLength = bgrImg.total() * bgrImg.elemSize(); // 计算字节数
                if (bytesLength > 0) {
                    // 绘制，绘制完成后发送数据
                    cameraFactory->drawFrame(bgrImg.data, pFrame->width, pFrame->height);
                    cameraFactory->putCallbackFrame(cameraFactory->copyVideoFrame(pFrame, bgrImg.data, bytesLength));
                }
            }
            video_free(pFrame);
        }
    }
    pthread_exit(nullptr);
}

void CameraFactoryV4L2Impl::drawFrame(uint8_t *frame, int width, int height) {
    // 释放源数据
    pthread_mutex_lock(&surfaceMutex);
    {
        if (frame && mPreviewWindow) {
            ANativeWindow_Buffer buffer;
            // 锁定缓冲区以获取可以写入的内存区域
            if (ANativeWindow_lock(mPreviewWindow, &buffer, nullptr) == 0) {
                auto *dst = (uint8_t *) buffer.bits;
                // 将RGB数据复制到RGBA图像，并设置alpha值为255
                for (int i = 0, j = 0; i < width * height; ++i, j += 4) {
                    dst[j] = frame[i * 3 + 2];     // R
                    dst[j + 1] = frame[i * 3 + 1]; // G
                    dst[j + 2] = frame[i * 3]; // B
                    dst[j + 3] = 0xFF;                 // A
                }
                // 解锁缓冲区
                ANativeWindow_unlockAndPost(mPreviewWindow);
            }
        }
    }
    pthread_mutex_unlock(&surfaceMutex);
}

void *CameraFactoryV4L2Impl::callback_thread_func(void *vptr_args) {
    auto *preview = reinterpret_cast<CameraFactoryV4L2Impl *>(vptr_args);
    if (preview) {
        JNIEnv *env = nullptr;
        while (preview->mIsPreviewRunning) {
            //等待获取预览的数据
            video_frame_t *pFrame = preview->waitCallbackFrame();
            if (!pFrame) {
                continue;
            }
            // 绘制，绘制完成后发送数据
            cv::Mat frameData = ImgUtils::bgr2Any(pFrame->data, preview->requestMode, pFrame->width, pFrame->height);
            // 释放源数据
            if (!frameData.empty()) {
                if (preview->theVM && !env) {
                    preview->theVM->AttachCurrentThread(&env, nullptr);
                }
                if (env) {
                    preview->callbackFrame(frameData.data, frameData.total() *
                                                           frameData.elemSize(), pFrame->width, pFrame->height, env);
                }
            }
            //释放销毁当前的frame
            video_free(pFrame);
        }
        //删除对应创建
        if (preview->theVM && env) {
            preview->theVM->DetachCurrentThread();

        }
    }
    pthread_exit(nullptr);

}

void CameraFactoryV4L2Impl::video_free(video_frame_t *frame) {
    if (!frame) {
        return;
    }
    if (frame->data) {
        free(frame->data);
    }
    free(frame);
}

void CameraFactoryV4L2Impl::clearCaptureFrame() {
    pthread_mutex_lock(&previewMutex);
    if (!previewFrames.isEmpty()) {
        for (int i = 0; i < previewFrames.size(); ++i) {
            video_frame_t *&pFrame = previewFrames[i];
            video_free(pFrame);
        }
        previewFrames.clear();
    }
    pthread_mutex_unlock(&previewMutex);
}

void CameraFactoryV4L2Impl::clearCallbackFrame() {
    pthread_mutex_lock(&callbackMutex);
    if (lastFrame) {
        video_free(lastFrame);
        lastFrame = nullptr;
    }
    pthread_mutex_unlock(&callbackMutex);
}


video_frame_t *CameraFactoryV4L2Impl::waitPreviewFrame() {
    video_frame_t *frame = nullptr;
    pthread_mutex_lock(&previewMutex);
    {
        // 如果当前内容为空，则等待获取数据，被唤醒
        if (previewFrames.isEmpty()) {
            pthread_cond_wait(&previewCond, &previewMutex);
        }
        // 如果当前是正在预览，并且预览数据大于0
        if (mIsPreviewRunning && !previewFrames.isEmpty()) {
            frame = previewFrames.remove(0);
        }
    }
    pthread_mutex_unlock(&previewMutex);
    return frame;
}

video_frame_t *CameraFactoryV4L2Impl::waitCallbackFrame() {
    video_frame_t *frame = nullptr;
    pthread_mutex_lock(&callbackMutex);
    {
        // 如果当前内容为空，则等待获取数据，被唤醒
        if (previewFrames.isEmpty()) {
            pthread_cond_wait(&callbackCond, &callbackMutex);
        }
        // 如果当前是正在预览，并且预览数据大于0
        if (mIsPreviewRunning && !previewFrames.isEmpty()) {
            frame = lastFrame;
            lastFrame = nullptr;
        }
    }
    pthread_mutex_unlock(&callbackMutex);
    return frame;
}

video_frame_t *CameraFactoryV4L2Impl::video_allocate_frame(size_t len) {
    video_frame_t *outFrame = (video_frame_t *) malloc(sizeof(*outFrame));
    if (!outFrame) {
        return nullptr;
    }
    outFrame->data = (uint8_t *) malloc(len);
    outFrame->dataSize = len;
    return outFrame;
}

video_frame_t *CameraFactoryV4L2Impl::copyVideoFrame(video_frame_t *inFrame, uint8_t *data, int length) {
    video_frame_t *outFrame = video_allocate_frame(length);
    if (!outFrame) {
        return nullptr;
    }
    outFrame->width = inFrame->width;
    outFrame->height = inFrame->height;
    outFrame->format = requestMode;
    std::memcpy(outFrame->data, data, length);
    return outFrame;
}


void CameraFactoryV4L2Impl::putPreviewFrame(uint8_t *data, uint64_t dataSize) {
    // 执行锁定
    pthread_mutex_lock(&previewMutex);
    // 如果缓存池的数据满了，则吧第一帧的数据删除掉
    if (mIsPreviewRunning && previewFrames.size() < MAX_FRAME) {
        video_frame_t *outFrame = video_allocate_frame(dataSize);
        if (outFrame) {
            outFrame->width = captureWidth;
            outFrame->height = captureHeight;
            outFrame->format = captureFormat;
            std::memcpy(outFrame->data, data, dataSize);
            previewFrames.put(outFrame);
        }
    }
    pthread_cond_signal(&previewCond);
    pthread_mutex_unlock(&previewMutex);
}

void CameraFactoryV4L2Impl::putCallbackFrame(video_frame_t *frame) {
    pthread_mutex_lock(&callbackMutex);
    // 如果缓存池的数据满了，则吧第一帧的数据删除掉
    if (mIsPreviewRunning && !lastFrame) {
        lastFrame = frame;
        frame = nullptr;
    }
    pthread_cond_signal(&callbackCond);
    pthread_mutex_unlock(&callbackMutex);
    if (frame) {
        video_free(frame);
    }
}

void CameraFactoryV4L2Impl::callbackFrame(uint8_t *frame, int len, int width, int height, JNIEnv *env) {
    if (!env || !previewListener || !onFrameMethod) {
        LOG_D("callbackFrame-return");
        return;
    }
    // 释放源数据
    if (frame && len > 0) {
        jobject buf = env->NewDirectByteBuffer(frame, len);
        if (buf) {
            env->CallVoidMethod(previewListener, onFrameMethod, width, height, buf);
            if (env->ExceptionCheck()) {
                LOG_D("ExceptionCheck");
                env->ExceptionDescribe();
            }
            env->ExceptionClear();
            env->DeleteLocalRef(buf);
        }
    }
}
