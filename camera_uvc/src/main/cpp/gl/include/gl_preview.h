//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_GL_PREVIEW_H
#define USBCAMERALIB_GL_PREVIEW_H

#include "gl_render.h"
#include "android/native_window.h"
#include <atomic>
#include <mutex>

class GLPreview {
public:
    GLPreview();

    ~GLPreview();

    // 初始化
    bool init(int width, int height);

    // 释放
    void release();

    // 仅释放 OpenGL/EGL 资源，保留 surface target
    void releaseOpenGL();

    // 绘制，输入固定为bgr格式
    void drawFrame(uint8_t *data, int width, int height);

    // 添加surface
    bool addSurfaceTarget(const std::string &targetId, ANativeWindow *window, int surfaceType,
                          int outputFormat) {
        return mRender.addTarget(targetId, window, surfaceType, outputFormat);
    };

    // 移除surface
    bool removeSurfaceTarget(const std::string &targetId){
        return mRender.removeTarget(targetId);
    };

    bool hasSurfaceTarget(int surfaceType) const {
        return mRender.hasTarget(surfaceType);
    }

    void takePicture() {
        mRender.requestTakePicture();
    }

    void startRecord() {
        mRender.startRecord();
    }

    void stopRecord() {
        mRender.stopRecord();
    }

private:
    std::mutex mMutex;
    GLRender mRender;
    int mWidth = 0;
    int mHeight = 0;
    bool mInitialized = false;
};

#endif //USBCAMERALIB_GL_PREVIEW_H
