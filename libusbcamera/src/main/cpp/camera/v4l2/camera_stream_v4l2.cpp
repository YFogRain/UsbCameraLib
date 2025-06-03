//
// Created on 2025/5/19.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "camera_stream_v4l2.h"
#include "linux/v4l2-subdev.h"
#include "video_v4l2_utils.h"
#include <sys/ioctl.h>
#include <sys/mman.h>

CameraStreamV4l2Impl::CameraStreamV4l2Impl(int fd)
        : videoFd(fd), frameWidth(640), frameHeight(480), frameFormat(PREVIEW_FORMAT_BGR) {
    mVideoRecord = new VideoRecord();
}

CameraStreamV4l2Impl::~CameraStreamV4l2Impl() {
    if (mVideoRecord) {
        delete mVideoRecord;
    }
    mVideoRecord = nullptr;
    videoFd = -1;
}

bool CameraStreamV4l2Impl::setPreviewSize(int width, int height, int format) {
    if (videoFd == -1) {
        return false;
    }
    bool result = VideoV4L2Utils::setStreamPreviewSize(videoFd, format, width, height);
    previewWidth = width;
    previewHeight = height;
    frameFormat = format;
    return result;
}

bool CameraStreamV4l2Impl::startPreview() {
    if (videoFd == -1) {
        return false;
    }
    // 1. 获取宽高信息，设置到当前调用位置
    struct v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(videoFd, VIDIOC_G_FMT, &fmt) == 0) { // 获取当前的宽高配置信息
        frameWidth = fmt.fmt.pix.width;
        frameHeight = fmt.fmt.pix.height;
        frameFormat = VideoV4L2Utils::v4l2FormatToInt(fmt.fmt.pix.pixelformat);
    } else {
        frameWidth = previewWidth;
        frameHeight = previewHeight;
        previewFps = 30;
    }
    v4l2_streamparm streamparm{};
    streamparm.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(videoFd, VIDIOC_G_PARM, &streamparm) == 0) {
        if (streamparm.parm.capture.capability & V4L2_CAP_TIMEPERFRAME) {
            int num = streamparm.parm.capture.timeperframe.numerator;
            int denom = streamparm.parm.capture.timeperframe.denominator;
            if (num != 0) {
                previewFps = denom / num;
            } else {
                previewFps = 30; // fallback
            }
        }
    } else {
        previewFps = 30; // fallback
    }
    LOG_D("当前使用的分辨率信息:%d*%d ,format:%d", frameWidth, frameHeight, frameFormat);
    // 设置窗口的宽高
    changeWindowSize(frameWidth, frameHeight);
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
    mIsCaptureRunning.store(true);
    captureThread = std::thread(&CameraStreamV4l2Impl::thread_func_capture, this);
    previewThread = std::thread(&CameraStreamV4l2Impl::thread_func_preview, this);
    mIsPreviewCallRunning.store(true);
    previewResultThread = std::thread(&CameraStreamV4l2Impl::thread_func_preview_call, this);
    return true;
}

bool CameraStreamV4l2Impl::stopPreview() {
    if (mVideoRecord) {
        mVideoRecord->stopRecord();
    }
    previewFps = 30;
    LOG_D("停止预览开始");
    LOG_D("mIsRunning:%d", mIsCaptureRunning.load());
    if (mIsCaptureRunning.load()) {
        // 停止预览线程
        {
            std::lock_guard<std::mutex> lock(previewMutex);
            mIsCaptureRunning.store(false);
        }
        {
            std::lock_guard<std::mutex> lock(previewResultMutex);
            mIsPreviewCallRunning.store(false);
        }
        if (captureThread.joinable()) {
            captureThread.join();
        }
        previewCond.notify_one();
        if (previewThread.joinable()) {
            previewThread.join();
        }
        // 停止预览回调线程
        previewResultCond.notify_one();
        if (previewResultThread.joinable()) {
            previewResultThread.join();
        }
        enum v4l2_buf_type bufType = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        if (ioctl(videoFd, VIDIOC_STREAMOFF, &bufType) < 0) {
            LOG_E("停止视频流失败, 错误码: %d", errno);
        }
        // 停止预览
    }
    cleanup_buffers();
    clearPreviewFrames();
    clearPreviewResultFrames();
    LOG_D("停止预览结束");
    return true;
}


bool CameraStreamV4l2Impl::prepare_mmap() {
    // 请求缓冲区，
    struct v4l2_requestbuffers req;
    memset(&req, 0, sizeof(req));
    req.count = 4;
    req.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    req.memory = V4L2_MEMORY_MMAP;

    if (ioctl(videoFd, VIDIOC_REQBUFS, &req) < 0) {
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
        if (ioctl(videoFd, VIDIOC_QUERYBUF, &buf) < 0) {
            LOG_E("获取缓冲区数据,错误码:%d", errno);
            return false;
        }
        captureBuffers[i].length = buf.length;
        captureBuffers[i].start = mmap(NULL, buf.length, PROT_READ | PROT_WRITE, MAP_SHARED, videoFd, buf.m.offset);
        if (captureBuffers[i].start == MAP_FAILED) {
            LOG_E("获取缓冲区数据,错误码:%d", errno);
            return false;
        }
        // Queue the buffer
        if (ioctl(videoFd, VIDIOC_QBUF, &buf) < 0) {
            LOG_E("放回缓冲区数据失败,错误码:%d", errno);
            return false;
        }
    }
    return true;
}

bool CameraStreamV4l2Impl::startCameraStream() {
    enum v4l2_buf_type bufType = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (ioctl(videoFd, VIDIOC_STREAMON, &bufType) < 0) {
        LOG_E("视频流启动失败, 错误码: %d", errno);
        return false;
    }
    return true;
}


stream_frame_t *CameraStreamV4l2Impl::allocate_stream_frame(uint8_t *data, int length) {
    if (!data || length <= 0) {
        return nullptr;
    }
    stream_frame_t *outFrame = static_cast<stream_frame_t *>(malloc(sizeof(*outFrame)));
    if (!outFrame) {
        return nullptr;
    }
    outFrame->width = frameWidth;
    outFrame->height = frameHeight;
    outFrame->format = frameFormat;
    outFrame->rotation = mDisplayTransformState;
    outFrame->data_size = length;
    outFrame->data = static_cast<uint8_t *>(malloc(length));
    if (!outFrame->data) {
        free(outFrame);
        return nullptr;
    }
    memcpy(outFrame->data, data, length);
    return outFrame;
}

void CameraStreamV4l2Impl::clearPreviewFrames() {
    std::lock_guard<std::mutex> lock(previewMutex);
    if (!previewFrames.empty()) {
        for (stream_frame_t *pFrame: previewFrames) { // 直接遍历，避免 size() 变化
            free_stream(pFrame);
        }
        previewFrames.clear();
    }
}

void CameraStreamV4l2Impl::cleanup_buffers() {
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
    ioctl(videoFd, VIDIOC_REQBUFS, &req_buffers);
    captureBuffers = nullptr;
    captureBufferLength = 0;
}

void CameraStreamV4l2Impl::clearPreviewResultFrames() {
    std::lock_guard<std::mutex> lock(previewResultMutex);
    if (!previewResultFrames.empty()) {
        for (stream_frame_t *pFrame: previewResultFrames) { // 直接遍历，避免 size() 变化
            free_stream(pFrame);
        }
        previewResultFrames.clear();
    }
}

void CameraStreamV4l2Impl::putPreviewFrames(stream_frame_t *frame) {
    {
        std::lock_guard<std::mutex> lock(previewMutex);
        if (mIsCaptureRunning.load() && previewFrames.size() < MAX_FRAME) {
            previewFrames.push_back(frame);
            frame = nullptr;
        }
    }
    previewCond.notify_one();
    if (frame) {
        free_stream(frame);
    }
}

void CameraStreamV4l2Impl::putPreviewCallFrames(stream_frame_t *frame) {
    {
        std::lock_guard<std::mutex> lock(previewResultMutex);
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

stream_frame_t *CameraStreamV4l2Impl::waitPreviewFrames() {
    stream_frame_t *frame = nullptr;
    {
        std::unique_lock<std::mutex> lock(previewMutex);
        previewCond.wait(lock, [this] { return !mIsCaptureRunning.load() || !previewFrames.empty(); });
        if (mIsCaptureRunning.load() && !previewFrames.empty()) {
            frame = previewFrames.front();
            previewFrames.pop_front();
        }
    }
    return frame;
}

stream_frame_t *CameraStreamV4l2Impl::waitPreviewCallFrames() {
    stream_frame_t *frame = nullptr;
    {
        std::unique_lock<std::mutex> lock(previewResultMutex);
        previewResultCond.wait(lock, [this] { return !mIsPreviewCallRunning.load() || !previewResultFrames.empty(); });
        if (mIsPreviewCallRunning.load() && !previewResultFrames.empty()) {
            frame = previewResultFrames.front();
            previewResultFrames.pop_front();
        }
    }
    return frame;
}

void CameraStreamV4l2Impl::thread_func_capture() {
    LOG_D("=====开启循环捕获线程数据");
    struct v4l2_buffer buf;
    buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    buf.memory = V4L2_MEMORY_MMAP;

    while (mIsCaptureRunning && videoFd != -1) {
        // 从缓冲区队列中获取帧
        if (ioctl(videoFd, VIDIOC_DQBUF, &buf) < 0) {
            LOG_E("从缓冲区队列中获取帧失败, 错误码: %d", errno);
            continue;
        }
        if (!(buf.flags & V4L2_BUF_FLAG_ERROR)) {
            if (captureBuffers && buf.index >= 0 && buf.index < captureBufferLength) {
                stream_frame_t *captureFrame =
                        allocate_stream_frame((uint8_t *) captureBuffers[buf.index].start, buf.bytesused);
                if (captureFrame && captureFrame->data && captureFrame->data_size > 0) {
                    // 将数据复制到外部，然后释放当前缓冲区
                    putPreviewFrames(captureFrame);
                } else {
                    free_stream(captureFrame);
                }
            }
        }
        // 处理完毕后，将缓冲区重新放回队列
        if (ioctl(videoFd, VIDIOC_QBUF, &buf) < 0) {
            LOG_E("将缓冲区放回队列失败, 错误码: %d", errno);
        }
    }
}

void CameraStreamV4l2Impl::thread_func_preview() {
    LOG_D("=====开启循环捕获线程数据");
    LOG_D("=====mIsRunning：：：%d", mIsCaptureRunning.load());
    while (mIsCaptureRunning.load()) {
        // 等待获取预览的数据
        stream_frame_t *pFrame = waitPreviewFrames();
        if (!pFrame) {
            continue;
        }
        stream_frame_t *bgrFrame = any2Bgr(pFrame);
        free_stream(pFrame); // 释放源数据
        // 绘制
        if (bgrFrame && bgrFrame->data && bgrFrame->data_size > 0) { // 如果数据不为空
            drawFrame(bgrFrame->data, bgrFrame->data_size, bgrFrame->width, bgrFrame->height);
        }
        putPictureFrame(bgrFrame);
        // 发送给回调线程处理
        putPreviewCallFrames(bgrFrame);
    }
}

void CameraStreamV4l2Impl::thread_func_preview_call() {
    LOG_D("=====开启循环捕获线程数据");
    LOG_D("=====mIsRunning：：：%d", mIsPreviewCallRunning.load());
    JNIEnv *env = nullptr;
    while (mIsPreviewCallRunning.load()) {
        // 等待获取预览的数据
        stream_frame_t *pFrame = waitPreviewCallFrames();
        if (!pFrame) {
            continue;
        }
        putRecordFrames(pFrame);
        // 格式化数据格式
        std::vector<uint8_t> outFrame = ImgUtils::format(pFrame->data, pFrame->width, pFrame->height, previewFormat);
        // 释放源数据
        free_stream(pFrame);

        if (outFrame.empty()) {
            return;
        }
        if (theVM && !env) {
            theVM->AttachCurrentThread(&env, nullptr);
        }
        // 释放源数据
        int data_bytes = outFrame.size();
        if (outFrame.data() && data_bytes > 0) {
            jobject buf = env->NewDirectByteBuffer(outFrame.data(), data_bytes);
            if (buf) {
                env->CallVoidMethod(previewListener, onFrameMethod, pFrame->width, pFrame->height, buf);
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