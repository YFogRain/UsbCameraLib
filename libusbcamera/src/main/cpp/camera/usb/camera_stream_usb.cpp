//
// Created on 2025/5/18.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "camera_stream_usb.h"

CameraStreamUsbImpl::CameraStreamUsbImpl(uvc_device_handle_t *deviceHandle)
        : mDeviceHandle(deviceHandle), frameWidth(640), frameHeight(480),
          frameFormat(PREVIEW_FORMAT_BGR) {
    // 初始化互斥锁
    mVideoRecord = new VideoRecord();
}

CameraStreamUsbImpl::~CameraStreamUsbImpl() {
    if (mVideoRecord) {
        delete mVideoRecord;
    }
    mVideoRecord = nullptr;
    mDeviceHandle = nullptr;
}

bool CameraStreamUsbImpl::startPreview() {
    uvc_stream_ctrl_t ctrl;
    int ret = prepare_preview(&ctrl);
    if (ret != UVC_SUCCESS) {
        return false;
    }
    ret = do_preview(&ctrl);
    return ret == UVC_SUCCESS;
}

bool CameraStreamUsbImpl::stopPreview() {
    if (mVideoRecord) {
        mVideoRecord->stopRecord();
    }
    LOG_D("停止预览开始");
    LOG_D("mIsRunning:%d", mIsCaptureRunning.load());
    if (mIsCaptureRunning.load()) {
        // 停止预览线程
        {
            std::lock_guard<std::mutex> lock(captureMutex);
            mIsCaptureRunning.store(false);
        }
        {
            std::lock_guard<std::mutex> lock(previewMutex);
            mIsPreviewCallRunning.store(false);
        }
        // 停止预览
        uvc_stop_streaming(mDeviceHandle);
        // 停止预览回调线程
        previewResultCond.notify_one();
        if (previewResultThread.joinable()) {
            previewResultThread.join();
        }
        // 停止捕获线程
        captureCond.notify_one();
        if (captureThread.joinable()) {
            captureThread.join();
        }
    }
    LOG_D("停止预览结束");
    clearCaptureFrames();
    clearPreviewFrames();
    clearPictureFrame();
    return true;
}

bool CameraStreamUsbImpl::setPreviewSize(int width, int height, int format) {
    previewWidth = width;
    previewHeight = height;
    frameFormat = format;
    uvc_stream_ctrl_t ctrl;
    // 设置完成后需要提前获取一次，否则打开时无法正常获取到流
    uvc_error_t ret = uvc_get_stream(mDeviceHandle, &ctrl, getPreviewFormat(), previewWidth, previewHeight);
    LOG_D("设置预览分辨率同步获取预览流-setPreviewSize-结果:%d", ret);
    return true;
}

int CameraStreamUsbImpl::prepare_preview(uvc_stream_ctrl_t *ctrl) {
    LOG_D("获取对应的流控制器-size:%d-%d", previewWidth, previewHeight);
    uvc_error_t ret = uvc_get_stream(mDeviceHandle, ctrl, getPreviewFormat(), previewWidth, previewHeight);
    LOG_D("获取对应的流控制器-结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    previewFps = 10000000 / ctrl->dwFrameInterval;
    LOG_D("当前设置的fps为:%d", 10000000 / ctrl->dwFrameInterval);
    // 获取当前预览流需要设置的宽高等数据
    uvc_frame_desc_t *frameDesc = uvc_get_frame_desc(mDeviceHandle, ctrl);
    if (frameDesc) {
        frameWidth = frameDesc->wWidth;
        frameHeight = frameDesc->wHeight;
    } else {
        frameWidth = previewWidth;
        frameHeight = previewHeight;
    }
    frameBytes = getPreviewBytesSize();
    changeWindowSize(frameWidth, frameHeight);
    return UVC_SUCCESS;
}

int CameraStreamUsbImpl::do_preview(uvc_stream_ctrl_t *ctrl) {
    clearCaptureFrames();
    clearPreviewFrames();
    uvc_error_t ret = uvc_start_streaming(mDeviceHandle, ctrl, uvc_stream_callback, (void *) this, 0);
    LOG_D("开启预览流-结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    // 开启线程，启动捕获流操作
    mIsCaptureRunning.store(true);
    captureThread = std::thread(&CameraStreamUsbImpl::thread_func_capture, this);
    mIsPreviewCallRunning.store(true);
    previewResultThread = std::thread(&CameraStreamUsbImpl::thread_func_preview_call, this);
    return ret;
}

stream_frame_t *CameraStreamUsbImpl::allocate_stream_frame(uvc_frame_t *inFrame) {
    if (!inFrame) {
        return nullptr;
    }
    stream_frame_t *outFrame = static_cast<stream_frame_t *>(malloc(sizeof(*outFrame)));
    if (!outFrame) {
        return nullptr;
    }
    outFrame->width = inFrame->width;
    outFrame->height = inFrame->height;
    outFrame->format = uvc_format_to_mode(inFrame->frame_format);
    if (inFrame->data_bytes > 0 && inFrame->data) {
        outFrame->data_size = inFrame->data_bytes;
        outFrame->data = static_cast<uint8_t *>(malloc(inFrame->data_bytes));
        if (!outFrame->data) {
            free_stream(outFrame);
            return nullptr;
        }
        memcpy(outFrame->data, inFrame->data, inFrame->data_bytes);
    }
    outFrame->rotation = mDisplayTransformState;
    return outFrame;
}

void CameraStreamUsbImpl::uvc_stream_callback(uvc_frame_t *frame, void *vptr_args) {
    // 获取当前的uvcPreview对象
    auto *preview = reinterpret_cast<CameraStreamUsbImpl *>(vptr_args);
    // 如果当前不是正在预览，或者当前数据返回的是null，则直接下一回合
    if (!preview->mIsCaptureRunning.load() || !frame) {
        LOG_E("当前数据返回的是null，则直接下一回合：：%d", preview->mIsCaptureRunning.load());
        return;
    }
    if ((frame->frame_format != UVC_FRAME_FORMAT_MJPEG && frame->data_bytes < preview->frameBytes) || !frame->data) {
        LOG_E("当前数据大小不符合::data_bytes:%zu,frameBytes:%zu", frame->data_bytes,
              preview->frameBytes);
        return;
    }
    stream_frame_t *captureFrame = preview->allocate_stream_frame(frame);
    // 获取bgr类型的数据数组
    if (!captureFrame || !captureFrame->data || captureFrame->data_size <= 0) {
        LOG_E("数据转换失败");
        free_stream(captureFrame);
        return;
    }
    // 数据发送出去
    preview->putCaptureFrames(captureFrame);
}

void CameraStreamUsbImpl::clearCaptureFrames() {
    std::lock_guard<std::mutex> lock(captureMutex);
    if (!captureFrames.empty()) {
        for (stream_frame_t *pFrame: captureFrames) { // 直接遍历，避免 size() 变化
            free_stream(pFrame);
        }
        captureFrames.clear();
    }
}

void CameraStreamUsbImpl::clearPreviewFrames() {
    std::lock_guard<std::mutex> lock(previewMutex);
    if (!previewResultFrames.empty()) {
        for (stream_frame_t *pFrame: previewResultFrames) { // 直接遍历，避免 size() 变化
            free_stream(pFrame);
        }
        previewResultFrames.clear();
    }
}

void CameraStreamUsbImpl::putCaptureFrames(stream_frame_t *frame) {
    {
        std::lock_guard<std::mutex> lock(captureMutex);
        if (mIsCaptureRunning.load() && captureFrames.size() < MAX_FRAME) {
            captureFrames.push_back(frame);
            frame = nullptr;
        }
    }
    captureCond.notify_one();
    if (frame) {
        free_stream(frame);
    }
}

void CameraStreamUsbImpl::putPreviewCallFrames(stream_frame_t *frame) {
    {
        std::lock_guard<std::mutex> lock(previewMutex);
        if (mIsPreviewCallRunning.load() && previewResultFrames.empty()) {
            previewResultFrames.push_back(frame);
            frame = nullptr;
        }
    }
    previewResultCond.notify_one();
    if (frame) {
        free_stream(frame);
    }
}

stream_frame_t *CameraStreamUsbImpl::waitCaptureFrames() {
    stream_frame_t *frame = nullptr;
    {
        std::unique_lock<std::mutex> lock(captureMutex);
        captureCond.wait(lock, [this] { return !mIsCaptureRunning.load() || !captureFrames.empty(); });
        if (mIsCaptureRunning.load() && !captureFrames.empty()) {
            frame = captureFrames.front();
            captureFrames.pop_front();
        }
    }
    return frame;
}

stream_frame_t *CameraStreamUsbImpl::waitPreviewCallFrames() {
    stream_frame *frame = nullptr;
    {
        std::unique_lock<std::mutex> lock(previewMutex);
        previewResultCond.wait(lock, [this] { return !mIsPreviewCallRunning.load() || !previewResultFrames.empty(); });
        if (mIsPreviewCallRunning.load() && !previewResultFrames.empty()) {
            frame = previewResultFrames.front();
            previewResultFrames.pop_front();
        }
    }
    return frame;
}

void CameraStreamUsbImpl::thread_func_capture() {
    LOG_D("=====开启循环捕获线程数据");
    LOG_D("=====mIsRunning：：：%d", mIsCaptureRunning.load());
    while (mIsCaptureRunning.load()) {
        // 等待获取预览的数据
        stream_frame_t *pFrame = waitCaptureFrames();
        if (!pFrame) {
            continue;
        }
        stream_frame_t *bgrFrame = any2Bgr(pFrame);
        free_stream(pFrame); // 释放源数据
        // 绘制
        if (bgrFrame && bgrFrame->data && bgrFrame->data_size > 0) { // 如果数据不为空
            drawFrame(bgrFrame->data, bgrFrame->data_size, bgrFrame->width, bgrFrame->height);
        }
        // 发送给回调线程处理
        putPictureFrame(bgrFrame);
        putPreviewCallFrames(bgrFrame);

    }
}

void CameraStreamUsbImpl::thread_func_preview_call() {
    LOG_D("=====开启循环捕获线程数据");
    LOG_D("=====mIsRunning：：：%d", mIsPreviewCallRunning.load());
    JNIEnv *env = nullptr;
    while (mIsPreviewCallRunning.load()) {
        // 等待获取预览的数据
        stream_frame_t *pFrame = waitPreviewCallFrames();
        if (!pFrame) {
            continue;
        }
        uint32_t width = pFrame->width;   // 数据宽度
        uint32_t height = pFrame->height;

        // 发送到录制的线程去进行录制，（路线线程会重新复制一次数据，不需担心后续内存释放问题）
        putRecordFrames(pFrame);
        {
            std::lock_guard<std::mutex> lock(previewMutex);
            if (!previewListener) {
                free_stream(pFrame);
                continue;
            }
        }
        // 格式化数据格式
        std::vector<uint8_t> outFrame = ImgUtils::format(pFrame->data, width, height, previewFormat);
        // 释放源数据
        free_stream(pFrame);
        if (outFrame.empty()) {
            continue;
        }
        if (theVM && !env) {
            theVM->AttachCurrentThread(&env, nullptr);
        }
        // 释放源数据
        int data_bytes = outFrame.size();
        if (outFrame.data() && data_bytes > 0) {
            jobject buf = env->NewDirectByteBuffer(outFrame.data(), data_bytes);
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
    if (theVM && env) {
        theVM->DetachCurrentThread();
    }
}