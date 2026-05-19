//
// Created on 2025/5/19.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_STREAM_V4L2_H
#define UVCCAMERA_CAMERA_STREAM_V4L2_H

#include "i_camera_stream.h"

struct Buffer {
    void *start;
    size_t length;
};

class CameraStreamV4l2Impl : public ICameraStream {

public:
    CameraStreamV4l2Impl(int fd);
    ~CameraStreamV4l2Impl() override;
    bool startPreview() override;                                    // 关闭预览
    bool stopPreview() override;                                     // 开启预览
    bool setPreviewSize(int width, int height, int format) override; // 设置预览分辨率
    bool isRunningPreview() override { return mIsCaptureRunning.load(); }

private:
    int videoFd;

    std::atomic<bool> mIsCaptureRunning{false};     // 当前捕获数据状态
    std::atomic<bool> mIsPreviewCallRunning{false}; // 当前捕获数据状态

    std::mutex previewMutex;       // 预览线程的互斥锁
    std::mutex previewResultMutex; // 预览线程的互斥锁

    std::thread captureThread;       // 捕获预览流
    std::thread previewThread;       // 捕获预览流，并且绘制到页面的线程
    std::thread previewResultThread; // 预览回调的线程

    std::condition_variable previewCond;       // 等待专用的条件变量
    std::condition_variable previewResultCond; // 等待专用的条件变量

    std::deque<stream_frame_t *> previewFrames;       // 预览缓存数据
    std::deque<stream_frame_t *> previewResultFrames; // 回调缓存数据

    Buffer *captureBuffers = nullptr; // 缓冲区数据
    int captureBufferLength = 0;      // 缓冲区数量

    int frameWidth, frameHeight, frameFormat; // 实际参数

    void clearPreviewFrames();
    void clearPreviewResultFrames();

    void putPreviewFrames(stream_frame_t *frame);
    void putPreviewCallFrames(stream_frame_t *frame);

    stream_frame_t *waitPreviewFrames();
    stream_frame_t *waitPreviewCallFrames();

    void thread_func_capture();      // 预览处理线程
    void thread_func_preview();      // 预览处理线程
    void thread_func_preview_call(); // 数据回调线程

    void cleanup_buffers();   // 清理buffers
    bool prepare_mmap();      // 映射缓冲区到用户空间
    bool startCameraStream() const; // 启动视频流

    stream_frame_t *allocate_stream_frame(uint8_t *data, int length);
};

#endif // UVCCAMERA_CAMERA_STREAM_V4L2_H
