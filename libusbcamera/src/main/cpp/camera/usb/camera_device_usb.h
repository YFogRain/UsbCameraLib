//
// Created on 2025/5/18.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_DEVICE_USB_H
#define UVCCAMERA_CAMERA_DEVICE_USB_H

#include "camera_stream_usb.h"
#include "i_camera_device.h"
#include "libuvc/libuvc.h"

class CameraDeviceUsbImpl : public ICameraDevice {
public:
    CameraDeviceUsbImpl(uvc_context_t *context, uvc_device_t *device,
                        uvc_device_handle_t *deviceHandle, int fd);

    ~CameraDeviceUsbImpl() override;

    bool setParameter(int type, int value) override;

    std::variant<std::monostate, int, std::string> getParameter(int type) override;

    std::variant<std::monostate, std::pair<int, int>, std::string, int>
    getSupportParameters(int type) override;

    bool setButtonListener(JavaVM *vm, JNIEnv *env, jobject listener) override;

    void releaseButtonListener() override;

private:
    bool buttonCallbackRegistered = false; // 是否已经注册了监听
    JavaVM *theVM = nullptr; //回调对应全局应该保存的东西
    jobject buttonListener = nullptr; // 按钮回调的对象
    jmethodID onButtonMethod = nullptr; // 按钮回调的方法

    std::mutex buttonMutex;     // 窗口操作锁，单线程操作当前指定窗口

    uvc_context_t *mContext;
    uvc_device_handle_t *mDeviceHandle;
    uvc_device_t *mDevice;
    int mFd;

    CameraStreamUsbImpl *mCameraStream;

    std::string getSupportedPreviewSizes();

    static int getFormatType(uint8_t descriptorSubtype);

    ICameraStream *getUserStream() override { return mCameraStream; }

    static void uvc_button_callback(int button, int state, void *user_ptr); // button按钮回调
    /**
     * 按钮事件回调
     * @param type 按钮类型
     * @param state  按钮状态
     */
    void onButtonStateCallback(int type, int state);
};

#endif // UVCCAMERA_CAMERA_DEVICE_USB_H
