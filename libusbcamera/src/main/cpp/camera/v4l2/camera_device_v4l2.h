//
// Created on 2025/5/19.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_DEVICE_V4L2_H
#define UVCCAMERA_CAMERA_DEVICE_V4L2_H

#include "camera_stream_v4l2.h"
#include "i_camera_device.h"

class CameraDeviceV4L2Impl : public ICameraDevice {
public:
    CameraDeviceV4L2Impl(int fd);
    ~CameraDeviceV4L2Impl() override;
    bool setParameter(int type, int value) override;
    std::variant<std::monostate, int, std::string> getParameter(int type) override;
    std::variant<std::monostate, std::pair<int, int>, std::string, int> getSupportParameters(int type) override;
    bool setButtonListener(JavaVM *vm, JNIEnv *env, jobject listener) override;
    void releaseButtonListener() override;
private:
    int mVideoFd; // 对应的文件描述符
    JavaVM *theVM = nullptr; //回调对应全局应该保存的东西
    jobject buttonListener = nullptr; // 按钮回调的对象
    jmethodID onButtonMethod = nullptr; // 按钮回调的方法

    std::mutex buttonMutex;     // 窗口操作锁，单线程操作当前指定窗口

    CameraStreamV4l2Impl *mCameraStream;

    std::string getSupportedPreviewSizes();
    int getFormatType(uint8_t descriptorSubtype);

    ICameraStream *getUserStream() override { return mCameraStream; }

    int loadTypeToId(int type);

    int loadValueToPutValue(int type, int value);
};

#endif // UVCCAMERA_CAMERA_DEVICE_V4L2_H
