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
    CameraDeviceUsbImpl(uvc_context_t *context, uvc_device_t *device, uvc_device_handle_t *deviceHandle, int fd);
    ~CameraDeviceUsbImpl() override;
    bool setParameter(int type, int value) override;
    std::variant<std::monostate, int, std::string> getParameter(int type) override;
    std::variant<std::monostate, std::pair<int, int>, std::string, int> getSupportParameters(int type) override;

private:
    uvc_context_t *mContext;
    uvc_device_handle_t *mDeviceHandle;
    uvc_device_t *mDevice;
    int mFd;

    CameraStreamUsbImpl *mCameraStream;

    std::string getSupportedPreviewSizes();
    int getFormatType(uint8_t descriptorSubtype);

    ICameraStream *getUserStream() override { return mCameraStream; }
};

#endif // UVCCAMERA_CAMERA_DEVICE_USB_H
