//
// Created on 2025/5/18.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_I_CAMERA_DEVICE_H
#define UVCCAMERA_I_CAMERA_DEVICE_H
#include "i_camera_stream.h"
#include "variant"

class ICameraDevice {

public:
    virtual ~ICameraDevice() = default;

    virtual bool setParameter(int type, int value) = 0; // 设置参数值

    virtual std::variant<std::monostate, int, std::string> getParameter(int type) = 0; // 获取参数

    virtual std::variant<std::monostate, std::pair<int, int>, std::string, int> getSupportParameters(int type) = 0;

    virtual ICameraStream* getUserStream() = 0;
};
#endif // UVCCAMERA_I_CAMERA_DEVICE_H
