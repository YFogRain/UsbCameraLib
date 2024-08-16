//
// Created by MI T on 2024/8/1.
//

#ifndef UVCCAMERA_UVCCAMERA_H
#define UVCCAMERA_UVCCAMERA_H

#include "android/native_window.h"
#include "android/native_window_jni.h"
#include "UvcPreview.h"
#include "libuvc/libuvc_internal.h"
#include "Log.h"
#include "rapidjson/rapidjson.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"
#include <utility> // for std::pair

typedef struct control_value {
    int min;
    int max;
} control_value_t;

class UvcCamera {
private:
    uvc_context_t *mContext;
//    uvc_device_t *mDevice;
    uvc_device_handle_t *mDeviceHandle;
    UvcPreview *mPreview;
    void clearCameraParams();

public:
    UvcCamera();//初始化

    ~UvcCamera(); //数据销毁

    int connect(int fd);//连接设备

    int disConnect();//断开连接释放内存

    int setPreviewSize(int width, int height, int fps, bool mode); //设置预览分辨率

    int setPreviewDisplay(ANativeWindow *preview_window); //设置预览控件

    int startPreview();//开启预览

    int stopPreview();//停止预览

    char *getSupportedSize();//获取支持的预览分辨率

    std::pair<int, int> getPreviewSize(); //获取当前分辨率

    std::pair<int, int> getParameterRange(int type); //获取对应分辨率的区间

    int getParameterIntValue(int type);//获取当前int类型参数

    bool getParameterBoolValue(int type); //设置当前bool类型参数

    bool setParameterIntValue(int type, int value);

    bool setParameterBoolValue(int type, bool value); //设置当前bool类型参数

    bool getSupportAutoExposure();

    void setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener);

    bool  currentFrameModeIsMjpeg();

    int getCurrentFps();
};


#endif //UVCCAMERA_UVCCAMERA_H
