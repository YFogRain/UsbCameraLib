//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_I_CAMERA_STREAM_H
#define USBCAMERALIB_I_CAMERA_STREAM_H

#include "Log.h"
#include "camera_constants.h"
#include "img_utils.h"
#include "opencv2/core/mat.hpp"
#include "string"
#include "gl_preview.h"
#include <cstdint>
#include <deque>
#include <mutex>
#include <thread>
#include <atomic>
#include <cstring>
#include <android/native_window.h>
#include <filesystem>

typedef struct stream_frame {
    uint8_t *data;    // 数据
    int format;       // 类型，参考camera_constants.h
    uint32_t width;   // 数据宽度
    uint32_t height;  // 数据高度
    size_t data_size; // 数据长度
    int rotation;     // 方向
} stream_frame_t;

static inline void free_stream(stream_frame_t *frame) {
    if (frame) {
        if (frame->data) {
            free(frame->data);
            frame->data = nullptr;
        }
        free(frame);
    }
}

class ICameraStream {
public:
    virtual ~ICameraStream() = default;

    virtual bool startPreview() = 0;                                    // 关闭预览
    virtual bool stopPreview() = 0;                                     // 开启预览
    virtual bool setPreviewSize(int width, int height, int format) = 0; // 设置预览分辨率
    /**
     * 添加surface目标
     * @param targetId 目标ID
     * @param window 窗口
     * @param surfaceType surface类型
     * @return 是否添加成功
     */
    bool addSurfaceTarget(std::string targetId, ANativeWindow *window, int surfaceType,
                          int outputFormat) {
        if (!window || targetId.empty() || !mPreview) { // 检查是否可以添加
            return false;
        }
        return mPreview->addSurfaceTarget(targetId, window, surfaceType, outputFormat);
    };

    /**
     * 移除surface目标
     * @param targetId 目标ID
     * @return 是否移除成功
     */
    bool removeSurfaceTarget(std::string targetId) {
        if (targetId.empty() || !mPreview) {
            return false;
        }
        return mPreview->removeSurfaceTarget(targetId);
    };

    /**
     * 是否正在预览
     * @return 是否正在预览
     */
    virtual bool isRunningPreview() = 0;

    /**
     * 拍照
     * @return 是否成功
     */
    bool takePicture() {
        LOG_D("ICameraStream", "执行拍照～～:%d", isRunningPreview());
        if (mPreview && isRunningPreview()) {
            mPreview->takePicture();
            return true;
        }
        return false;
    }

    /**
     * 开始录制
     * @return 是否成功
     */
    bool startRecord() {
        if (mPreview && isRunningPreview()) {
            mPreview->startRecord();
            return true;
        }
        return false;
    }

    /**
     * 停止录制
     * @return 是否成功
     */
    bool stopRecord() {
        if (mPreview && isRunningPreview()) {
            mPreview->stopRecord();
            return true;
        }
        return false;
    }

    std::string getCurrentPreviewSize() const {
        return std::to_string(previewWidth) + ":" + std::to_string(previewHeight);
    }

protected:
    int previewWidth = 640;
    int previewHeight = 480;
    int previewFormat = PREVIEW_FORMAT_BGR; // 预览宽高,预览类型

    GLPreview *mPreview = nullptr;
};

#endif //USBCAMERALIB_I_CAMERA_STREAM_H
