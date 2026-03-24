//
// Created by MI T on 2026/3/24.
//

#ifndef OPENGLDEMO_GL_PREVIEW_H
#define OPENGLDEMO_GL_PREVIEW_H

#include "gl_render.h"
#include "android/native_window.h"
#include <mutex>

class GLPreview {
public:
    GLPreview();

    ~GLPreview();

    void setCurrentSurface(ANativeWindow *window) {
        std::lock_guard<std::mutex> lock(mMutex);
        if (this->mPreviewWindow) {
            ANativeWindow_release(this->mPreviewWindow);
        }
        this->mPreviewWindow = window;
    };

    void releaseSurface() {
        std::lock_guard<std::mutex> lock(mMutex);
        mRender.release();
        if (this->mPreviewWindow) {
            ANativeWindow_release(this->mPreviewWindow);
        }
        this->mPreviewWindow = nullptr;
    };

    bool initRender(int width, int height);

    void destroyRender();

    // 绘制，目前强制使用brg格式，后续尝试多种格式
    void drawFrame(uint8_t *data, int width, int height, GL_FORMAT format);

    // 是否镜像
    void setMirror(bool mirror) {
        std::lock_guard<std::mutex> lock(mMutex);
        this->mMirrorState = mirror;
        mMatrixDirty.store(true);
//        mRender.updateMatrix(this->mMirrorState, this->mRotation);
    };

    // 设置方向
    void setRotation(int degree) {
        std::lock_guard<std::mutex> lock(mMutex);
        this->mRotation = degree;
        mMatrixDirty.store(true);
//        mRender.updateMatrix(this->mMirrorState, this->mRotation);
    };

    [[nodiscard]] int getRotation() const {
        return mRotation;
    }

    [[nodiscard]] bool getMirrorState() const {
        return mMirrorState;
    }

    void clearDraw() {
        std::lock_guard<std::mutex> lock(mMutex);
        mRender.clearDraw();
    }

private:
    std::mutex mMutex;
    ANativeWindow *mPreviewWindow = nullptr;
    // 标记：是否需要更新矩阵
    std::atomic<bool> mMatrixDirty{false};
    bool mMirrorState = false;
    int mRotation = 0;
    GLRender mRender;

};

#endif //OPENGLDEMO_GL_PREVIEW_H
