//
// Created on 2025/2/9.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_FACTORY_USB_IMPL_H
#define UVCCAMERA_CAMERA_FACTORY_USB_IMPL_H

#include "i_camera_factory.h"
#include "libuvc/libuvc.h"
#include "android/native_window.h"
#include "iostream"
#include <deque>


class CameraFactoryUsbImpl : public ICameraFactory {
public:
    CameraFactoryUsbImpl(uvc_context_t *context, uvc_device_t *device, uvc_device_handle_t *deviceHandle, int fd);

    ~CameraFactoryUsbImpl() override;

    bool setDisplaySurface(ANativeWindow *preview_window) override; // 设置预览控件

    bool setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int mode) override; // 设置监听回调

    bool setPreviewSize(int width, int height, int format) override; // 设置预览分辨率

    // 根据类型，获取对应的支持的参数信息
    std::variant<std::monostate, std::pair<int, int>, std::string, int> getSupportParameters(int type) override;

    bool setParameter(int type, int value) override; // 设置参数值

    std::variant<std::monostate, int, std::string> getParameter(int type) override; // 获取参数

    bool startPreview() override; // 开启预览

    bool stopPreview() override; // 关闭预览

    bool isRunningPreview() override { return mIsPreviewRunning; };

private:
    uvc_context_t *mContext;
    uvc_device_handle_t *mDeviceHandle;
    uvc_device_t *mDevice;
    int mFd;
    volatile bool mIsPreviewRunning; // 当前的预览状态
    int mDisplayTransformState;

    JavaVM *theVM; //回调对应全局应该保存的东西
    jobject previewListener; //回调的对象
    jmethodID onFrameMethod; //回调的方法
    ANativeWindow *mPreviewWindow; // 预览的窗口

    int requestWidth, requestHeight, requestMode; // 设置的预览数据,使用的类型
    int frameWidth, frameHeight, frameMode;       // 实际使用的预览控件的宽高
    size_t frameBytes;                            // 预览数据大小，为了校验数据完整性

    pthread_t captureThread;      // 捕获预览流，并且绘制到页面的线程
    pthread_mutex_t captureMutex; // 捕获线程的互斥锁
    pthread_cond_t captureCond;   // 等待专用的条件变量

    pthread_mutex_t surfaceMutex; // 预览互斥锁

    std::deque<uvc_frame_t *> previewFrames;

    int prepare_preview(uvc_stream_ctrl_t *ctrl); // 准备预览

    int do_preview(uvc_stream_ctrl_t *ctrl); // 开始预览

    static void uvc_stream_callback(uvc_frame_t *frame, void *vptr_args); // 预览数据回调

    void clearCaptureFrame(); // 清空所有捕获的数据

    void putFrame(uvc_frame_t *frame); // 发送数据

    uvc_frame_t *waitPreviewFrame(); // 等待获取数据

    void drawFrame(uvc_frame_t *frame); // 将数据绘制到控件上去，

    static void *capture_thread_func(void *vptr_args); // 当前捕获线程的回调

    // 获取当前使用的分辨率类型
    uvc_frame_format getPreviewFormat();

    size_t getPreviewBytesSize();

    std::string getSupportedPreviewSizes();

    int getFormatType(uint8_t descriptorSubtype);

    void callbackFrame(uvc_frame_t *frame, JNIEnv *env); //数据回调
};


#endif // UVCCAMERA_CAMERA_FACTORY_USB_IMPL_H
