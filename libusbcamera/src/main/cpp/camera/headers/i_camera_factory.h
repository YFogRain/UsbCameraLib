//
// Created on 2025/1/23.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_I_CAMERA_FACTORY_H
#define UVCCAMERA_I_CAMERA_FACTORY_H
#include <variant>
#include <jni.h>
#include <android/native_window.h>

class ICameraFactory {

public:
    virtual ~ICameraFactory() = default;

    virtual bool setPreviewSize(int width, int height, int format) = 0; // 设置预览分辨率

    virtual bool setDisplaySurface(ANativeWindow *preview_window) = 0; // 设置预览控件

    virtual bool setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener,int mode) = 0; // 设置监听回调
    // 根据类型，获取对应的支持的参数信息
    virtual std::variant<std::monostate, std::pair<int, int>, std::string,int> getSupportParameters(int type) = 0;

    virtual bool setParameter(int type, int value) = 0; // 设置参数值

    virtual std::variant<std::monostate,int, std::string> getParameter(int type) = 0; // 获取参数

    virtual bool startPreview() = 0; // 开启预览

    virtual bool stopPreview() = 0; // 关闭预览

    virtual bool isRunningPreview() = 0;
};
#endif // UVCCAMERA_I_CAMERA_FACTORY_H
