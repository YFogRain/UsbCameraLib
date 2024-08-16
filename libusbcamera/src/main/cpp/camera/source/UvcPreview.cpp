//
// Created by MI T on 2024/8/1.
//

#include "../UvcPreview.h"
#include "../state/CameraParameterState.h"

UvcPreview::UvcPreview(uvc_device_handle_t *deviceHandler) :
        theVM(nullptr),
        previewListener(nullptr),
        onFrameMethod(nullptr),
        lastFrames(nullptr),
        mDeviceHandle(deviceHandler),
        frameMode(DEFAULT_PREVIEW_MODE),
        requestWidth(DEFAULT_PREVIEW_WIDTH),
        requestHeight(DEFAULT_PREVIEW_HEIGHT),
        requestFps(DEFAULT_PREVIEW_FPS),
        mPreviewWindow(nullptr),
        frameBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * 2),
        mIsRunning(false) {
    //初始化互斥锁
    pthread_mutex_init(&captureMutex, nullptr);
    pthread_mutex_init(&previewMutex, nullptr);
    pthread_cond_init(&captureCond, nullptr);
    pthread_cond_init(&previewCond, nullptr);
    initFrame();
}

UvcPreview::~UvcPreview() {
    if (mPreviewWindow) {
        ANativeWindow_release(mPreviewWindow);
    }
    pthread_mutex_destroy(&captureMutex);
    pthread_mutex_destroy(&previewMutex);
    pthread_cond_destroy(&captureCond);
    pthread_cond_destroy(&previewCond);
    mPreviewWindow = nullptr;
    mDeviceHandle = nullptr;

    theVM = nullptr;
    previewListener = nullptr;
    onFrameMethod = nullptr;
}

void UvcPreview::initFrame() {
    const uvc_format_desc_t *format_desc = uvc_get_format_descs(mDeviceHandle);
    const uvc_frame_desc_t *frame_desc = format_desc->frame_descs;
    int width = DEFAULT_PREVIEW_WIDTH;
    int height = DEFAULT_PREVIEW_HEIGHT;
    int fps = DEFAULT_PREVIEW_FPS;
    LOG_D("当前获取的预览模式:%d", format_desc->bDescriptorSubtype);
    if (frame_desc) {
        width = frame_desc->wWidth;
        height = frame_desc->wHeight;
        fps = 10000000 / frame_desc->dwDefaultFrameInterval;
        LOG_D("frame_desc-宽高:%d-%d;fps:%d", width, height, fps);
    }
    requestWidth = width;
    requestHeight = height;
    requestFps = fps;
    LOG_D("实际使用-宽高:%d-%d;fps:%d", requestWidth, requestHeight, requestFps);
}

int UvcPreview::startPreview() {
    //当前如果正在运行，则直接返回成功
    if (mIsRunning)return UVC_SUCCESS;
    uvc_stream_ctrl_t ctrl;
    int ret = prepare_preview(&ctrl);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    ret = do_preview(&ctrl);
    return ret;
}

int UvcPreview::prepare_preview(uvc_stream_ctrl_t *ctrl) {
    LOG_D("获取对应的流控制器-sie:%d-%d,fps:%d", requestWidth, requestHeight, requestFps);
    uvc_error_t ret = uvc_get_stream_ctrl_format_size(mDeviceHandle, ctrl, frameMode,
                                                      requestWidth, requestHeight, requestFps);
    LOG_D("获取对应的流控制器-结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    //获取当前预览流需要设置的宽高等数据
    uvc_frame_desc_t *frameDesc = uvc_get_frame_desc(mDeviceHandle, ctrl);
    if (frameDesc) {
        frameWidth = frameDesc->wWidth;
        frameHeight = frameDesc->wHeight;
        LOG_D("获取到的当前预览宽高-结果:%d-%d", frameWidth, frameHeight);
        if (mPreviewWindow) {
            ANativeWindow_setBuffersGeometry(mPreviewWindow,
                                             frameWidth, frameHeight, UVC_FORMAT_FRAME_WINDOW);
        }
    } else {
        frameWidth = requestWidth;
        frameHeight = requestHeight;
    }
    frameBytes = frameWidth * frameHeight * (frameMode == UVC_FRAME_FORMAT_YUYV ? 2 : 4);
    return UVC_SUCCESS;
}


int UvcPreview::do_preview(uvc_stream_ctrl_t *ctrl) {
    uvc_error_t ret = uvc_start_streaming(mDeviceHandle, ctrl, uvc_stream_callback, (void *) this,
                                          0);
    LOG_D("开启预览流-结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    clearPreviewFrame();
    clearPreviewFrame();
    //开启线程，启动捕获流操作
    mIsRunning = true;
    int result = pthread_create(&captureThread, nullptr, capture_thread_func, (void *) this);
    LOG_D("创建捕获数据线程结果-结果:%d", result);
    if (result != UVC_SUCCESS) {
        mIsRunning = false;
        return result;
    }
    int resultPreview = pthread_create(&previewThread, nullptr, preview_thread_func, (void *) this);
    LOG_D("创建预览回调结果线程-结果:%d", resultPreview);
    return ret;
}

//预览流回调的实际数据
void UvcPreview::uvc_stream_callback(uvc_frame_t *frame, void *vptr_args) {
    //获取当前的uvcPreview对象
    auto *preview = reinterpret_cast<UvcPreview *>(vptr_args);
    //如果当前不是正在预览，或者当前数据返回的是null，则直接下一回合
    if (!preview->mIsRunning || !frame) {
        LOG_E("当前数据返回的是null，则直接下一回合");
        return;
    }
    if (frame->width != preview->frameWidth || frame->height != preview->frameHeight) {
        LOG_E("当前数据宽高不符");
        return;
    }
    uvc_frame_format format = frame->frame_format;
    if (!format || (format != UVC_FRAME_FORMAT_MJPEG && format != UVC_FRAME_FORMAT_YUYV)) {
        LOG_E("当前数据格式错误.metadata_bytes:%d", frame->metadata_bytes);
        return;
    }
    if ((format != UVC_FRAME_FORMAT_MJPEG && frame->data_bytes < preview->frameBytes) ||
        !frame->data) {
        LOG_E("当前数据大小不符合；；data_bytes:%d,frameBytes:%d", frame->data_bytes,
              preview->frameBytes);
        return;
    }
    //    //获取bgr类型的数据数组
    uvc_frame_t *bgrFrame = uvc_allocate_frame(frame->width * frame->height * 3);
    if (!bgrFrame) {
        LOG_E("数据转换失败");
        return;
    }
    //将数据转换成rgb格式
    uvc_error_t ret = uvc_any2rgb(frame, bgrFrame);
    if (ret != UVC_SUCCESS) {
        uvc_free_frame(bgrFrame);
        return;
    }
    //数据发送出去
    preview->putFrame(bgrFrame);
}

void *UvcPreview::capture_thread_func(void *vptr_args) {
    auto *preview = reinterpret_cast<UvcPreview *>(vptr_args);
    if (preview) {
        while (preview->mIsRunning) {
            //等待获取预览的数据
            uvc_frame_t *pFrame = preview->waitPreviewFrame();
            if (!pFrame)continue;
            preview->drawFrame(pFrame);
            //将当前帧发送给预览回调线程处理
            preview->putPreviewFrame(pFrame);
        }
    }
    pthread_exit(nullptr);
}

// Clamp 函数确保颜色值在 0-255 之间
inline uint8_t clamp(int value) {
    return (value < 0) ? 0 : (value > 255) ? 255 : value;
}

void UvcPreview::drawFrame(uvc_frame_t *frame) {
    if (!mPreviewWindow) {
        return;
    }
    pthread_mutex_lock(&captureMutex);
//    //获取bgr类型的数据数组
    if (frame) {
        uint8_t *src = (uint8_t *) frame->data;
        ANativeWindow_Buffer buffer;
        // 锁定缓冲区以获取可以写入的内存区域
        if (ANativeWindow_lock(mPreviewWindow, &buffer, nullptr) == 0) {
            uint8_t *dst = (uint8_t *) buffer.bits;
            uint32_t height = frame->height;
            uint32_t width = frame->width;
            // 将RGB数据复制到RGBA图像，并设置alpha值为255
            for (int i = 0, j = 0; i < width * height; ++i, j += 4) {
                dst[j] = src[i * 3];     // R
                dst[j + 1] = src[i * 3 + 1]; // G
                dst[j + 2] = src[i * 3 + 2]; // B
                dst[j + 3] = 0xFF;                 // A
            }
            // 解锁缓冲区
            ANativeWindow_unlockAndPost(mPreviewWindow);
        }
    }
    pthread_mutex_unlock(&captureMutex);
}

void UvcPreview::putFrame(uvc_frame_t *frame) {
    //执行锁定
    pthread_mutex_lock(&captureMutex);
    //如果缓存池的数据满了，则吧第一帧的数据删除掉
    if (previewFrames.size() >= MAX_FRAME) {
        uvc_frame_t *pFrame = previewFrames.remove(0);
        uvc_free_frame(pFrame);
    }
    previewFrames.put(frame);
    pthread_cond_signal(&captureCond);
    pthread_mutex_unlock(&captureMutex);
}

uvc_frame_t *UvcPreview::waitPreviewFrame() {
    uvc_frame_t *frame = nullptr;
    pthread_mutex_lock(&captureMutex);
    //如果当前内容为空，则等待获取数据，被唤醒
    if (previewFrames.isEmpty()) {
        pthread_cond_wait(&captureCond, &captureMutex);
    }
    //如果当前是正在预览，并且预览数据大于0
    if (mIsRunning && !previewFrames.isEmpty()) {
        frame = previewFrames.remove(0);
    }
    pthread_mutex_unlock(&captureMutex);
    return frame;
}

int UvcPreview::stopPreview() {
    LOG_D("停止预览开始");
    LOG_D("mIsRunning:%d", mIsRunning);
    if (mIsRunning) {
        //停止预览线程
        mIsRunning = false;
        uvc_stop_streaming(mDeviceHandle);
        pthread_cond_signal(&captureCond);
        if (pthread_join(captureThread, nullptr) != EXIT_SUCCESS) {
            LOG_E("UVCPreview::terminate capture thread: pthread_join failed");
        }
        pthread_cond_signal(&previewCond);
        if (pthread_join(previewThread, nullptr) != EXIT_SUCCESS) {
            LOG_E("UVCPreview::terminate capture thread: pthread_join failed");
        }
    }
    LOG_D("停止预览结束");
    clearCaptureFrame();
    clearPreviewFrame();
    return UVC_SUCCESS;
}

int UvcPreview::setPreviewSize(int width, int height, int fps, bool mode) {
    LOG_D("setPreviewSize-size:%d*%d,fps:%d", width, height, fps);
    if ((requestWidth != width) || (requestHeight != height) || (requestFps != fps)) {
        requestWidth = width;
        requestHeight = height;
        requestFps = fps;
    }
    if (mode) {
        frameMode = UVC_FRAME_FORMAT_MJPEG;
    } else {
        frameMode = UVC_FRAME_FORMAT_YUYV;
    }
    uvc_stream_ctrl_t ctrl;
    uvc_error_t ret = uvc_get_stream_ctrl_format_size(mDeviceHandle, &ctrl, frameMode,
                                                      requestWidth, requestHeight, requestFps);
    LOG_D("获取对应的流控制器-setPreviewSize-结果:%d", ret);
    return UVC_SUCCESS;
}

int UvcPreview::setDisplaySurface(ANativeWindow *preview_window) {
    if (mPreviewWindow != preview_window) {
        if (mPreviewWindow) {
            ANativeWindow_release(mPreviewWindow);
        }
        mPreviewWindow = preview_window;
        if (LIKELY(mPreviewWindow)) {
            ANativeWindow_setBuffersGeometry(mPreviewWindow, frameWidth, frameHeight,
                                             UVC_FORMAT_FRAME_WINDOW);
        }
    }
    return UVC_SUCCESS;
}

void UvcPreview::clearCaptureFrame() {
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

void UvcPreview::clearPreviewFrame() {
    pthread_mutex_lock(&previewMutex);
    if (lastFrames) {
        uvc_free_frame(lastFrames);
        lastFrames = nullptr;
    }
    pthread_mutex_unlock(&previewMutex);
}

void *UvcPreview::preview_thread_func(void *vptr_args) {
    auto *preview = reinterpret_cast<UvcPreview *>(vptr_args);
    if (preview) {
        JNIEnv *env = nullptr;
        while (preview->mIsRunning) {
            //等待获取预览的数据
            uvc_frame_t *pFrame = preview->getLastFrame();
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
    }
    pthread_exit(nullptr);
}

void UvcPreview::putPreviewFrame(uvc_frame_t *frame) {
    //执行锁定
    pthread_mutex_lock(&previewMutex);
    //如果缓存池的数据满了，则吧第一帧的数据删除掉
    if (lastFrames) {
        uvc_free_frame(lastFrames);
    }
    lastFrames = frame;
    pthread_cond_signal(&previewCond);
    pthread_mutex_unlock(&previewMutex);
}

uvc_frame_t *UvcPreview::getLastFrame() {
    uvc_frame_t *frame = nullptr;
    pthread_mutex_lock(&previewMutex);
    //如果当前内容为空，则等待获取数据，被唤醒
    if (!lastFrames) {
        pthread_cond_wait(&previewCond, &previewMutex);
    }
    if (mIsRunning) {
        frame = lastFrames;
        lastFrames = nullptr;
    }
    pthread_mutex_unlock(&previewMutex);
    return frame;
}

void UvcPreview::setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener) {
    theVM = vm;
    if (!env->IsSameObject(previewListener, listener)) {
        onFrameMethod = nullptr;
        if (previewListener) {
            env->DeleteGlobalRef(previewListener);
        }
        previewListener = listener;
        if (listener) {
            // get method IDs of Java object for callback
            jclass clazz = env->GetObjectClass(listener);
            if (clazz) {
                //宽高
                onFrameMethod = env->GetMethodID(clazz, "onFrame", "(IILjava/nio/ByteBuffer;)V");
            }
            env->ExceptionClear();
            if (!onFrameMethod) {
                env->DeleteGlobalRef(listener);
                previewListener = nullptr;
                return;
            }
        } else {
            env->DeleteGlobalRef(listener);
            onFrameMethod = nullptr;
            previewListener = nullptr;
        }
    } else {
        LOG_D("callbackFrame-IsSameObject-false");
    }
}

void UvcPreview::callbackFrame(uvc_frame_t *frame, JNIEnv *env) {
    if (!env || !previewListener || !onFrameMethod) {
        LOG_D("callbackFrame-return");
        return;
    }
    jobject buf = env->NewDirectByteBuffer(frame->data, frame->data_bytes);
    env->CallVoidMethod(previewListener, onFrameMethod, frame->width, frame->height, buf);
    if (env->ExceptionCheck()) {
        LOG_D("ExceptionCheck");
        env->ExceptionDescribe();
    }
}

bool UvcPreview::setDisplayOrientation(int orientation) {
    pthread_mutex_lock(&captureMutex);
    if (LIKELY(mPreviewWindow)) {
        int rotation = 0;
        if (orientation == 0) {
            rotation = 0x00;
        } else if (orientation > 0 && orientation <= 90) {
            rotation = 0x04;
        } else if (orientation > 90 && orientation <= 180) {
            rotation = 0x01 | 0x02;
        } else if (orientation > 180 && orientation <= 270) {
            rotation = (0x01 | 0x02) | 0x04;
        } else if (orientation == -1) {
            rotation = 0x01;
        } else if (orientation == -2) {
            rotation = 0x02;
        }
        mDisplayOrientation = orientation;
        native_window_set_buffers_transform(mPreviewWindow, rotation);
    }
    pthread_mutex_unlock(&captureMutex);
    return true;
}

int UvcPreview::getDisplayOrientation() {
    return mDisplayOrientation;
}

std::pair<int, int> UvcPreview::getPreviewSize() {
    return std::make_pair(requestWidth, requestHeight);
}

bool UvcPreview::currentFrameModeIsMjpeg() {
    return frameMode == UVC_FRAME_FORMAT_MJPEG;
}

int UvcPreview::getCurrentFps() {
    return requestFps;
}
