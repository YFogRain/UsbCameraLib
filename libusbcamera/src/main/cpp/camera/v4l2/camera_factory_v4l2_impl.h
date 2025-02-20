//
// Created on 2025/1/23.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERAV4L2_H
#define UVCCAMERA_CAMERAV4L2_H

#include "i_camera_factory.h"
#include "opencv2/core/mat.hpp"
#include <cstdint>
#include <linux/videodev2.h>
#include "ObjectArray.h"
#include <variant>
#include "android/native_window.h"

struct Buffer {
    void *start;
    size_t length;
};

struct video_frame_t {
    uint32_t width;
    uint32_t height;
    uint8_t *data;
    uint64_t dataSize;
    int format;
};

class CameraFactoryV4L2Impl : public ICameraFactory {

public:
    CameraFactoryV4L2Impl(int fd);

    ~CameraFactoryV4L2Impl() override;

    bool setPreviewSize(int width, int height, int format) override; // 设置预览分辨率

    bool setDisplaySurface(ANativeWindow *preview_window) override; // 设置预览控件

    bool setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int mode) override; // 设置监听回调

    std::variant<std::monostate, std::pair<int, int>, std::string, int> getSupportParameters(int type) override;

    bool setParameter(int type, int value) override; // 设置参数值

    std::variant<std::monostate, int, std::string> getParameter(int type) override; // 获取参数

    bool startPreview() override; // 开启预览

    bool stopPreview() override; // 关闭预览

    bool isRunningPreview() override { return mIsPreviewRunning; };

private:
    int mVideoFd; // 对应的文件描述符

    JavaVM *theVM; //回调对应全局应该保存的东西
    jobject previewListener; //回调的对象
    jmethodID onFrameMethod; //回调的方法
    ANativeWindow *mPreviewWindow; // 预览的窗口

    int mDisplayTransformState;                     // 当前旋转方向
    volatile bool mIsPreviewRunning;                // 当前的预览状态
    Buffer *captureBuffers;                         // 缓冲区数据
    int captureBufferLength;                        // 缓冲区数量
    int previewWidth, previewHeight, previewFormat; // 预览信息配置
    int captureWidth, captureHeight, captureFormat; // 捕获预览时使用的宽高

    int requestMode;

    pthread_t captureThread;      // 捕获预览流，并且绘制到页面的线程

    pthread_mutex_t surfaceMutex; // 预览互斥锁

    pthread_t previewThread;      // 预览线程
    pthread_mutex_t previewMutex; // 预览线程的互斥锁
    pthread_cond_t previewCond;   // 预览线程等待专用的条件变量

    pthread_t callbackThread;      // 回调线程
    pthread_mutex_t callbackMutex; // 回调线程的互斥锁
    pthread_cond_t callbackCond;   // 回调线程等待专用的条件变量

    ObjectArray<video_frame_t *> previewFrames; // 当前帧缓存，读取绘制使用

    video_frame_t *lastFrame; // 最后一帧数据，需要发送给外部的

    static void *capture_thread_func(void *vptr_args); // 当前捕获线程的回调

    static void *preview_thread_func(void *vptr_args); // 当前预览线程的回调

    static void *callback_thread_func(void *vptr_args); // 当前回调线程的回调

    void clearCaptureFrame(); // 清空当前缓存的所有数据

    void clearCallbackFrame(); // 清空回调缓存数据

    bool prepare_mmap();      // 映射缓冲区到用户空间
    bool startCameraStream(); // 启动视频流

    void drawFrame(uint8_t *frame, int width, int height); // 绘制

    void putPreviewFrame(uint8_t *data, uint64_t dataSize);

    void putCallbackFrame(video_frame_t *frame);

    video_frame_t *waitPreviewFrame();

    video_frame_t *waitCallbackFrame();

    void cleanup_buffers(); // 清理buffers

    void callbackFrame(uint8_t *frame, int len, int width, int height, JNIEnv *env); //数据回调

    int loadTypeToId(int type);

    int loadValueToPutValue(int type, int value);

    video_frame_t *video_allocate_frame(size_t data_bytes);

    // 数据复制
    video_frame_t *copyVideoFrame(video_frame_t *inFrame, uint8_t *data, int length);

    static void video_free(video_frame_t *data);
};

#endif // UVCCAMERA_CAMERAV4L2_H
