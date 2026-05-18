//
// Created on 2025/2/9.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_HELPER_H
#define UVCCAMERA_CAMERA_HELPER_H

#include "i_camera_device.h"
#include "vector"
#include "string"

class CameraFactoryHelper {
public:
    static ICameraDevice *openCamera(int fd, int busNum, int devAddress); // 打开设备
    static ICameraDevice *openCamera(const char *videoPath);                    // 打开设备
    static std::vector<std::string> loadV4L2Devices();                     // 获取v4l2的支持的设备列表信息
    static bool closeCamera(int64_t cameraId);                             // 关闭设备

private:
    static bool isV4L2Supported(const std::string &dev_name); //判断是否是v4l2的支持
};

#endif // UVCCAMERA_CAMERA_HELPER_H
