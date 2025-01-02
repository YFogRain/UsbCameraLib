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

class UvcCamera {
private:
    uvc_context_t *mContext;
    uvc_device_t *mDevice;
    uvc_device_handle_t *mDeviceHandle;
    UvcPreview *mPreview;
    int mFd;
    //获取可使用的分辨率对应类型
    int getFormatType(uint8_t descriptorSubtype);

public:
    UvcCamera();//初始化

    ~UvcCamera(); //数据销毁

    int connect(int fd);//连接设备

    int connect(int fd, int busNum, int devAddress,const char *usbFs);

    int disConnect();//断开连接释放内存

    int setPreviewSize(int width, int height, int format); //设置预览分辨率

    int setPreviewDisplay(ANativeWindow *preview_window); //设置预览控件

    int startPreview();//开启预览

    int stopPreview();//停止预览

    char *getSupportedPreviewSizes();//获取支持的预览分辨率

    std::pair<int, int> getPreviewSize(); //获取当前分辨率

    std::pair<int, int> getParameterRange(int type); //获取对应分辨率的区间

    int getParameterIntValue(int type);//获取当前int类型参数

    bool setParameterIntValue(int type, int value);

    bool getSupportAutoExposure();

    void setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener,int mode);

    int loadCurrentFormat();

};


#endif //UVCCAMERA_UVCCAMERA_H
