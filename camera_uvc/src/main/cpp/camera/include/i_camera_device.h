//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_I_CAMERA_DEVICE_H
#define USBCAMERALIB_I_CAMERA_DEVICE_H

#include <jni.h>
#include "i_camera_stream.h"
#include "variant"

class ICameraDevice {
public:
    virtual ~ICameraDevice() = default;

    /**
     * 设置参数值
     * @param type 参数类型
     * @param value 参数值
     * @return
     */
    virtual bool setParameter(int type, int value) = 0;

    /**
     * 获取参数
     * @param type 参数类型
     * @return 参数值
     */
    virtual std::variant<std::monostate, int, std::string> getParameter(int type) = 0; // 获取参数
    /**
     * 获取支持的参数列表
     * @param type 参数类型
     * @return 支持的参数列表
     */
    virtual std::variant<std::monostate, std::pair<int, int>, std::string, int> getSupportParameters(int type) = 0;

    /**
     * 获取用户流
     * @return 用户流
     */
    virtual ICameraStream *getUserStream() = 0;

    /**
     * 设置按钮监听事件
     * @param vm JavaVM
     * @param env JNIEnv
     * @param listener 监听事件
     * @return
     */
    virtual bool setButtonListener(JavaVM *vm, JNIEnv *env, jobject listener) = 0;

    /**
     * 释放按钮监听事件
     */
    virtual void releaseButtonListener() = 0;
};

#endif //USBCAMERALIB_I_CAMERA_DEVICE_H
