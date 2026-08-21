//
// Created by MI T on 2026/3/24.
//

#include "gl_preview.h"
#include "Log.h"
#include <chrono>

GLPreview::GLPreview() = default;

GLPreview::~GLPreview() {
    release();
}

bool GLPreview::init(int width, int height) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (width <= 0 || height <= 0) {
        return false;
    }
    if (mInitialized && width == mWidth && height == mHeight) {
        return true;
    }
    if (mInitialized) {
        mRender.releaseOpenGL();
        mInitialized = false;
    }
    if (!mRender.init(width, height)) {
        return false;
    }
    mRender.updateMatrix(mMirrorState, mRotation);
    mWidth = width;
    mHeight = height;
    mInitialized = true;
    mMatrixDirty.store(false);
    return true;
}

void GLPreview::releaseOpenGL() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mInitialized) {
        return;
    }
    mRender.releaseOpenGL();
    mWidth = 0;
    mHeight = 0;
    mInitialized = false;
    mMatrixDirty.store(false);
}

void GLPreview::release() {
    std::lock_guard<std::mutex> lock(mMutex);
    mRender.release();
    mWidth = 0;
    mHeight = 0;
    mInitialized = false;
    mMatrixDirty.store(false);
}

void GLPreview::drawFrame(uint8_t *data, int width, int height) {
    if (!data) {
        LOG_E("GLPreview", "需要绘制的数据为null");
        return;
    }
    bool needUpdateMatrix = false;
    bool mirrorState = false;
    int rotation = 0;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        if (!mInitialized) {
            LOG_E("GLPreview", "drawFrame before init");
            return;
        }
        if (width != mWidth || height != mHeight) {
            LOG_I("GLPreview", "frame分辨率更新 : in=%d x %d, preview=%d x %d",
                  width, height, mWidth, mHeight);
            mRender.releaseOpenGL();
            if (!mRender.init(width, height)) {
                LOG_E("GLPreview", "初始化openGL失败 : %d x %d", width, height);
                mWidth = 0;
                mHeight = 0;
                mInitialized = false;
                mMatrixDirty.store(false);
                return;
            }
            mRender.updateMatrix(mMirrorState, mRotation);
            mWidth = width;
            mHeight = height;
            mMatrixDirty.store(false);
            needUpdateMatrix = false;
        } else {
            needUpdateMatrix = mMatrixDirty.exchange(false);
            if (needUpdateMatrix) {
                mirrorState = mMirrorState;
                rotation = mRotation;
            }
        }
    }
    if (needUpdateMatrix) { // 更新矩阵
        mRender.updateMatrix(mirrorState, rotation);
    }
    const int64_t ptsNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    // 执行绘制
    mRender.renderFrame(data, width, height, ptsNs);
}
