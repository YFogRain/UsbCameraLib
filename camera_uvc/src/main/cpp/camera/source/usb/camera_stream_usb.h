//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_CAMERA_STREAM_USB_H
#define USBCAMERALIB_CAMERA_STREAM_USB_H

#include "camera_constants.h"
#include "i_camera_stream.h"
#include "../../../gl/include/gl_preview.h"
#include "libuvc/libuvc.h"
#include <deque>
#include <memory>
#include <vector>

class CameraStreamUsbImpl : public ICameraStream {
private:
    uvc_device_handle_t *mDeviceHandle;

    std::atomic<bool> mIsCaptureRunning{false};     // 当前捕获数据状态
    std::mutex captureMutex; // 捕获线程的互斥锁
    std::thread captureThread;       // 捕获预览流，并且绘制到页面的线程
    std::condition_variable captureCond;       // 等待专用的条件变量
    std::deque<stream_frame_t *> captureFrames;       // 检测的缓存数据

    int frameWidth, frameHeight, frameFormat; // 实际参数
    size_t frameBytes;                        // 预览数据大小，为了校验数据完整性

    void clearCaptureFrames();

    void putCaptureFrames(stream_frame_t *frame);

    stream_frame_t *waitCaptureFrames();

    void thread_func_capture();      // 数据捕获线程

    int prepare_preview(uvc_stream_ctrl_t *ctrl); // 准备预览

    int do_preview(uvc_stream_ctrl_t *ctrl); // 开始预览

    static void uvc_stream_callback(uvc_frame_t *frame, void *vptr_args); // uvc预览数据回调

    uvc_frame_format getPreviewFormat() const {
        switch (frameFormat) {
            case PREVIEW_FORMAT_MJPEG:
                LOG_D("CameraStreamUsbImpl", "当前使用 PREVIEW_FORMAT_MJPEG");
                return UVC_FRAME_FORMAT_MJPEG;
            case PREVIEW_FORMAT_YUY2:
                LOG_D("CameraStreamUsbImpl", "当前使用 UVC_FRAME_FORMAT_YUYV");
                return UVC_FRAME_FORMAT_YUYV;
            case PREVIEW_FORMAT_NV12:
            case PREVIEW_FORMAT_YUV420SP:
                return UVC_FRAME_FORMAT_NV12; // 扩展支持 NV12 格式
            case PREVIEW_FORMAT_RGB:
                return UVC_FRAME_FORMAT_RGB; // 扩展支持 RGB 格式
            case PREVIEW_FORMAT_BGR:
                LOG_D("CameraStreamUsbImpl", "当前使用 UVC_FRAME_FORMAT_BGR");
                return UVC_FRAME_FORMAT_BGR; // 扩展支持 BGR 格式
            default:
                return UVC_FRAME_FORMAT_YUYV;
        }
    }

    size_t getPreviewBytesSize() const {
        size_t previewSizes = frameWidth * frameHeight;
        switch (frameFormat) {
            case PREVIEW_FORMAT_MJPEG:
                return previewSizes;
            case PREVIEW_FORMAT_YUY2:
                return previewSizes * 2;
            case PREVIEW_FORMAT_NV12:
            case PREVIEW_FORMAT_YUV420SP:
                return previewSizes * 3 / 2; // 扩展支持 NV12 格式
            case PREVIEW_FORMAT_RGB:
                return previewSizes * 3; // 扩展支持 RGB 格式
            case PREVIEW_FORMAT_BGR:
                return previewSizes * 3; // 扩展支持 BGR 格式
            default:
                return previewSizes * 2;
        }
    }

    static int uvc_format_to_mode(uvc_frame_format format) {
        if (format == UVC_FRAME_FORMAT_MJPEG) {
            return PREVIEW_FORMAT_MJPEG;
        } else if (format == UVC_FRAME_FORMAT_YUYV) {
            return PREVIEW_FORMAT_YUY2;
        } else if (format == UVC_FRAME_FORMAT_NV12) {
            return PREVIEW_FORMAT_NV12;
        } else if (format == UVC_FRAME_FORMAT_BGR) {
            return PREVIEW_FORMAT_BGR;
        }
        return PREVIEW_FORMAT_YUY2;
    };

    stream_frame_t *allocate_stream_frame(uvc_frame_t *inFrame);

public:
    explicit CameraStreamUsbImpl(uvc_device_handle_t *mDeviceHandle);

    ~CameraStreamUsbImpl() override;

    bool startPreview() override;                                    // 关闭预览
    bool stopPreview() override;                                     // 开启预览
    bool setPreviewSize(int width, int height, int format) override; // 设置预览分辨率
    bool isRunningPreview() override { return mIsCaptureRunning.load(); }
};

#endif //USBCAMERALIB_CAMERA_STREAM_USB_H
