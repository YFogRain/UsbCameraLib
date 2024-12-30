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
#define MAX_FRAME 2

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
    int frameWidth, frameHeight; //实际使用的预览控件的宽高
    size_t frameBytes;//预览数据大小，为了校验数据完整性

    int frameMode;//使用的类型
    int requestMode;//回调时转换的数据类型
    pthread_mutex_t surfaceMutex;//预览互斥锁

    pthread_t captureThread; //捕获预览流，并且绘制到页面的线程
    pthread_mutex_t captureMutex;//捕获线程的互斥锁
    pthread_cond_t captureCond;//等待专用的条件变量

    ObjectArray<uvc_frame_t *> previewFrames;

    int prepare_preview(uvc_stream_ctrl_t *ctrl); //准备预览

    int do_preview(uvc_stream_ctrl_t *ctrl); //开始预览

    static void uvc_stream_callback(uvc_frame_t *frame, void *vptr_args);//预览数据回调

    void clearCaptureFrame();//清空所有捕获的数据

    static void *capture_thread_func(void *vptr_args); //当前捕获线程的回调

    void putFrame(uvc_frame_t *frame); //发送数据

    uvc_frame_t *waitPreviewFrame(); //等待获取数据

    void drawFrame(uvc_frame_t *frame);//将数据绘制到控件上去，

    void callbackFrame(uvc_frame_t *frame, JNIEnv *env); //数据回调

    uvc_frame_format getPreviewFormat();


public:

    UvcPreview(uvc_device_handle_t *deviceHandler);

    ~UvcPreview();

    int startPreview();

    int stopPreview();

    int setPreviewSize(int width, int height, int format);

    int setDisplaySurface(ANativeWindow *preview_window);

    void setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener,int mode);

    //设置预览方向
    bool setDisplayOrientation(int orientation);

    int getDisplayOrientation() const;

    std::pair<int, int> getPreviewSize();

    int loadCurrentFormat();

    size_t getPreviewBytesSize();
};


#endif //UVCCAMERA_UVCPREVIEW_H
