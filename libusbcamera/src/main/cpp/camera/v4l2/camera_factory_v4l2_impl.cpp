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


CameraFactoryV4L2Impl::CameraFactoryV4L2Impl(int fd)
        : mVideoFd(fd), mPreviewWindow(nullptr), mIsPreviewRunning(false), previewWidth(640),
          previewHeight(480), requestMode(UVC_DATA_FORMAT_BGR),
          previewFormat(PREVIEW_FORMAT_YUY2), captureWidth(640), captureHeight(480),
          captureFormat(PREVIEW_FORMAT_YUY2),
          captureBuffers(nullptr), captureBufferLength(0),
          mDisplayTransformState(TRANSFORM_IDENTITY),
          theVM(nullptr), previewListener(nullptr), onFrameMethod(nullptr) {
    // 初始化互斥锁
    pthread_mutex_init(&captureMutex, nullptr);
    pthread_cond_init(&captureCond, nullptr);


    // 初始化互斥锁
    pthread_mutex_init(&callbackMutex, nullptr);
    pthread_cond_init(&callbackCond, nullptr);
}

CameraFactoryV4L2Impl::~CameraFactoryV4L2Impl() {
    pthread_mutex_destroy(&captureMutex);
    pthread_cond_destroy(&captureCond);

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
    pthread_mutex_lock(&captureMutex);
    {
        if (mPreviewWindow != preview_window) {
            if (mPreviewWindow) {
                ANativeWindow_release(mPreviewWindow);
            }
            mPreviewWindow = preview_window;
            if (LIKELY(mPreviewWindow)) {
                ANativeWindow_setBuffersGeometry(mPreviewWindow, captureWidth, captureHeight,
                                                 UVC_FORMAT_FRAME_WINDOW);
            }
            ImgUtils::setDisplayTransformState(mPreviewWindow, mDisplayTransformState);
        }
    }
    pthread_mutex_unlock(&captureMutex);
    return true;
}

bool CameraFactoryV4L2Impl::setParameter(int type, int value) {
    if (mVideoFd == -1) {
        return false;
    }
    // 如果是int类型，则可以设置下面的所有参数
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        return VideoV4L2Utils::setParameter(mVideoFd, V4L2_CID_EXPOSURE_AUTO,
                                            value == 1 ? V4L2_EXPOSURE_AUTO : V4L2_EXPOSURE_MANUAL);
    }
    int id;
    switch (type) {
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
        default:
            return false;
    }
    return VideoV4L2Utils::setParameter(mVideoFd, id, value);
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
        ANativeWindow_setBuffersGeometry(mPreviewWindow, captureWidth, captureHeight,
                                         UVC_FORMAT_FRAME_WINDOW);
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
    // 5. 创建回调线程
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
        captureBuffers[i].start = mmap(NULL, buf.length, PROT_READ | PROT_WRITE, MAP_SHARED,
                                       mVideoFd, buf.m.offset);
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
        pthread_cond_signal(&captureCond);
        // 使用pthread_kill来检查线程是否存活
        if (pthread_kill(captureThread, 0) == ESRCH || pthread_join(captureThread, nullptr) != 0) {
            LOG_E("captureThread::当前线程已经结束，或者等待结束线程失败");
        }
        pthread_cond_signal(&callbackCond);
        // 使用pthread_kill来检查线程是否存活
        if (pthread_kill(callbackThread, 0) == ESRCH ||
            pthread_join(callbackThread, nullptr) != 0) {
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
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        auto result = VideoV4L2Utils::getParameter(mVideoFd, V4L2_CID_EXPOSURE_AUTO);
        if (std::holds_alternative<int>(result)) {
            auto autoExposure = std::get<int>(result); // 获取到的范围信息
            return autoExposure == V4L2_EXPOSURE_AUTO ? 1 : 0;
        }
        return std::monostate{};
    }
    int id;
    switch (type) {
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
        default:
            return std::monostate{};
    }

    auto result = VideoV4L2Utils::getParameter(mVideoFd, id);
    if (!std::holds_alternative<int>(result)) {
        return std::monostate{};
    }
    auto value = std::get<int>(result); // 获取到的范围信息
    LOG_D("当前%d参数的值为:%d", type, value);
    return value;
}

std::variant<std::monostate, std::pair<int, int>, std::string, int> CameraFactoryV4L2Impl::getSupportParameters(int type) {
    if (mVideoFd == -1) {
        return std::monostate{};
    }
    if (type == CAMERA_PARAMETER_PREVIEW_SIZE) {
        return VideoV4L2Utils::getSupportPreviewSize(mVideoFd);
    }
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        auto result = VideoV4L2Utils::getSupportParameter(mVideoFd, V4L2_CID_EXPOSURE_AUTO);
        if (std::holds_alternative < std::pair < int, int >> (result)) {
            auto autoExposurePair = std::get < std::pair < int,
            int >> (result); // 获取到的范围信息
            return autoExposurePair.second >= V4L2_EXPOSURE_MANUAL ? 1 : 0;
        }
        return std::monostate{};
    }
    int id;
    switch (type) {
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
        default:
            return std::monostate{};
    }

    auto result = VideoV4L2Utils::getSupportParameter(mVideoFd, id);
    if (!std::holds_alternative < std::pair < int, int >> (result)) {
        return std::monostate{};
    }
    auto autoExposurePair = std::get < std::pair < int,
    int >> (result); // 获取到的范围信息
    LOG_D("当前支持%d类型参数:[%d,%d]", type, autoExposurePair.first,
          autoExposurePair.second);
    return autoExposurePair;
}

bool CameraFactoryV4L2Impl::setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int mode) {
    this->requestMode = mode;
    pthread_mutex_lock(&captureMutex);
    theVM = vm;
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
                return false;
            }
        } else {
            env->DeleteGlobalRef(listener);
            onFrameMethod = nullptr;
            previewListener = nullptr;
        }
    } else {
        LOG_D("callbackFrame-IsSameObject-false");
    }
    pthread_mutex_unlock(&captureMutex);
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
                    cameraFactory->drawFrame(frameData, buf.bytesused); // 绘制
//                     cameraFactory->putCallbackFrame();
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

void CameraFactoryV4L2Impl::drawFrame(uint8_t *frame, int length) {
    auto outImg = ImgUtils::any2BGR(frame, captureFormat, length, captureWidth, captureHeight);
    if (outImg.empty()) {
        LOG_E("转换的格式错误，是空的数据");
        return;
    }
    // 释放源数据
    int data_bytes = outImg.total() * outImg.elemSize();
    if (outImg.data && data_bytes > 0) {
        pthread_mutex_lock(&captureMutex);
        {
            if (frame && mPreviewWindow) {
                auto *src = (uint8_t *) outImg.data;
                ANativeWindow_Buffer buffer;
                // 锁定缓冲区以获取可以写入的内存区域
                if (ANativeWindow_lock(mPreviewWindow, &buffer, nullptr) == 0) {
                    auto *dst = (uint8_t *) buffer.bits;
                    // 将RGB数据复制到RGBA图像，并设置alpha值为255
                    for (int i = 0, j = 0; i < captureWidth * captureHeight; ++i, j += 4) {
                        dst[j] = src[i * 3 + 2];     // R
                        dst[j + 1] = src[i * 3 + 1]; // G
                        dst[j + 2] = src[i * 3]; // B
                        dst[j + 3] = 0xFF;                 // A
                    }
                    // 解锁缓冲区
                    ANativeWindow_unlockAndPost(mPreviewWindow);
                }
            }
        }
        pthread_mutex_unlock(&captureMutex);
    }
}

void *CameraFactoryV4L2Impl::callback_thread_func(void *vptr_args) {
    auto *preview = reinterpret_cast<CameraFactoryV4L2Impl *>(vptr_args);
    if (preview) {
        JNIEnv *env = nullptr;
        while (preview->mIsPreviewRunning) {
            //等待获取预览的数据
            auto pFrame = preview->waitCallbackFrame();
            if (!pFrame)continue;
            //将数据回到给上层
            if (preview->theVM && !env) {
                preview->theVM->AttachCurrentThread(&env, nullptr);
            }
            if (env) {
                preview->callbackFrame(pFrame->data, pFrame->width, pFrame->height, env);
            }
            //释放销毁当前的frame
            delete pFrame;
        }
        //删除对应创建
        if (preview->theVM && env) {
            preview->theVM->DetachCurrentThread();

        }
    }
    pthread_exit(nullptr);

}


void CameraFactoryV4L2Impl::clearCaptureFrame() {

}

void CameraFactoryV4L2Impl::clearCallbackFrame() {
    pthread_mutex_lock(&callbackMutex);
    if (!callbackFrames.isEmpty()) {
        for (int i = 0; i < callbackFrames.size(); ++i) {
            auto pFrame = callbackFrames[i];
            delete (pFrame);
        }
        callbackFrames.clear();
    }
    pthread_mutex_unlock(&callbackMutex);
}


void CameraFactoryV4L2Impl::putCallbackFrame(video_frame_t *frame) {
// 执行锁定
    pthread_mutex_lock(&callbackMutex);
    // 如果缓存池的数据满了，则吧第一帧的数据删除掉
    if (mIsPreviewRunning && callbackFrames.size() < MAX_FRAME) {
        callbackFrames.put(frame);
        frame = nullptr;
    }
    pthread_cond_signal(&callbackCond);
    pthread_mutex_unlock(&callbackMutex);
    if (frame) {
        // 如果没有写入到缓存，则直接释放当前对象
        delete frame;
    }
}

video_frame_t *CameraFactoryV4L2Impl::waitCallbackFrame() {
    video_frame_t *frame = nullptr;
    pthread_mutex_lock(&callbackMutex);
    {
        // 如果当前内容为空，则等待获取数据，被唤醒
        if (callbackFrames.isEmpty()) {
            pthread_cond_wait(&callbackCond, &callbackMutex);
        }
        // 如果当前是正在预览，并且预览数据大于0
        if (mIsPreviewRunning && !callbackFrames.isEmpty()) {
            frame = callbackFrames.remove(0);
        }
    }
    pthread_mutex_unlock(&callbackMutex);
    return frame;
}


void CameraFactoryV4L2Impl::callbackFrame(uint8_t *frame, int width, int height, JNIEnv *env) {
    if (!env || !previewListener || !onFrameMethod) {
        LOG_D("callbackFrame-return");
        return;
    }
    auto outImg = ImgUtils::bgr2Any(frame, width, height, requestMode);
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

