//
// Created by MI T on 2026/3/24.
//

#ifndef OPENGLDEMO_GL_PREVIEW_H
#define OPENGLDEMO_GL_PREVIEW_H

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

    // 绘制，输入固定为bgr格式；仅负责绘制，不处理重建
    void drawFrame(uint8_t *data, int width, int height);

    // 添加surface,仅支持RGBA的格式（录制、预览）
    bool
    addSurfaceTarget(const std::string &targetId, ANativeWindow *window, GLTargetType surfaceType) {
        return mRender.addTarget(targetId, window, surfaceType);
    };

    // 移除surface
    bool removeSurfaceTarget(const std::string &targetId) {
        return mRender.removeTarget(targetId);
    };

    // 是否存在surface
    bool hasSurfaceTarget(GLTargetType surfaceType) const {
        return mRender.hasTarget(surfaceType);
    }

    // 启动录制
    bool startRecord() {
        return mRender.startRecord();
    }

    // 停止录制
    void stopRecord() {
        mRender.stopRecord();
    }

    // 是否镜像
    void setMirror(bool mirror) {
        std::lock_guard<std::mutex> lock(mMutex);
        this->mMirrorState = mirror;
        mMatrixDirty.store(true);
    };

    // 设置方向
    void setRotation(int degree) {
        std::lock_guard<std::mutex> lock(mMutex);
        this->mRotation = degree;
        mMatrixDirty.store(true);
    };

    [[nodiscard]] int getRotation() const {
        std::lock_guard<std::mutex> lock(mMutex);
        return mRotation;
    }

    [[nodiscard]] bool getMirrorState() const {
        std::lock_guard<std::mutex> lock(mMutex);
        return mMirrorState;
    }

private:

    mutable std::mutex mMutex;
    std::atomic<bool> mMatrixDirty{false}; // 标记：是否需要更新矩阵
    bool mMirrorState = false;             // 当前镜像状态
    int mRotation = 0;                     // 当前旋转角度
    int mWidth = 0;                        // 当前初始化宽度
    int mHeight = 0;                       // 当前初始化高度
    bool mInitialized = false;             // 当前是否已完成 GL 初始化
    GLRender mRender;
};

#endif //OPENGLDEMO_GL_PREVIEW_H
