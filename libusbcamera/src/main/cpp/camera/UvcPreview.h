//
// Created by MI T on 2024/8/1.
//

#ifndef UVCCAMERA_UVCPREVIEW_H
#define UVCCAMERA_UVCPREVIEW_H

#include <android/native_window.h>
#include <jni.h>
#include "libuvc/libuvc.h"
#include "libuvc/libuvc_internal.h"
#include "Log.h"
#include "ObjectArray.h"
#include "window.h"
#include <utility> // for std::pair

#define  UVC_FORMAT_FRAME_WINDOW  WINDOW_FORMAT_RGBA_8888
#define MAX_FRAME 5

class UvcPreview {
private:
    JavaVM *theVM;
    jobject previewListener;
    jmethodID onFrameMethod;
    int mDisplayOrientation;
    uvc_device_handle_t *mDeviceHandle;
    ANativeWindow *mPreviewWindow;
    volatile bool mIsRunning;
    int requestWidth, requestHeight; //设置的预览数据
    int requestFps;
    int frameWidth, frameHeight; //实际使用的预览控件的宽高
    size_t frameBytes;//预览数据大小，为了校验数据完整性

    uvc_frame_format frameMode;//使用的类型
    pthread_t captureThread; //捕获预览流，并且绘制到页面的线程
    pthread_mutex_t captureMutex;//捕获线程的互斥锁
    pthread_cond_t captureCond;//等待专用的条件变量

    pthread_t previewThread; //捕获预览流，并且绘制到页面的线程
    pthread_mutex_t previewMutex;//预览回调的互斥锁
    pthread_cond_t previewCond;//等待专用的条件变量
    ObjectArray<uvc_frame_t *> previewFrames;
    uvc_frame_t *lastFrames;//最后一帧数据，回调用
    void initFrame();

    int prepare_preview(uvc_stream_ctrl_t *ctrl); //准备预览

    int do_preview(uvc_stream_ctrl_t *ctrl); //开始预览

    static void uvc_stream_callback(uvc_frame_t *frame, void *vptr_args);//预览数据回调

    void clearCaptureFrame();//清空所有捕获的数据

    void clearPreviewFrame();//清理旧的数据

    static void *capture_thread_func(void *vptr_args); //当前捕获线程的回调

    static void *preview_thread_func(void *vptr_args); //当前捕获线程的回调

    void putFrame(uvc_frame_t *frame); //发送数据

    void putPreviewFrame(uvc_frame_t *frame);//将当前预览帧的数据推到预览回调的线程

    uvc_frame_t *waitPreviewFrame(); //等待获取数据

    uvc_frame_t *getLastFrame();//获取最后一帧数据

    void drawFrame(uvc_frame_t *frame);//将数据绘制到控件上去，

    void callbackFrame(uvc_frame_t *frame, JNIEnv *env); //数据回调

public:

    UvcPreview(uvc_device_handle_t *deviceHandler);

    ~UvcPreview();

    int startPreview();

    int stopPreview();

    int setPreviewSize(int width, int height, int fps, bool mode);

    int setDisplaySurface(ANativeWindow *preview_window);

    void setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener);

    //设置预览方向
    bool setDisplayOrientation(int orientation);

    int getDisplayOrientation();

    std::pair<int, int> getPreviewSize();

    bool currentFrameModeIsMjpeg();

    int getCurrentFps();
};


#endif //UVCCAMERA_UVCPREVIEW_H
