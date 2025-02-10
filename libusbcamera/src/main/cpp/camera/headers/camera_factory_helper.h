//
// Created on 2025/2/9.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_HELPER_H
#define UVCCAMERA_CAMERA_HELPER_H
#include "i_camera_factory.h"

class CameraFactoryHelper {
public:
    static ICameraFactory *openCamera(int fd, int busNum, int devAddress); // 打开设备
    static ICameraFactory *openCamera(const char *videoPath);                    // 打开设备
    static bool closeCamera(int64_t cameraId);                             // 关闭设备
};
#endif // UVCCAMERA_CAMERA_HELPER_H
