//
// Created by MI T on 2026/5/14.
//

#include "../include/gl_preview.h"
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
    mWidth = width;
    mHeight = height;
    mInitialized = true;
    return true;
}

void GLPreview::release() {
    std::lock_guard<std::mutex> lock(mMutex);
    mRender.release();
    mWidth = 0;
    mHeight = 0;
    mInitialized = false;
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
}

void GLPreview::drawFrame(uint8_t *data, int width, int height) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!data) {
        LOG_E("GLPreview", "未初始化");
        return;
    }
    if (!mInitialized || width != mWidth || height != mHeight) {
        LOG_D("GLPreview", "重新初始化渲染尺寸 : in=%d x %d, preview=%d x %d", width, height,
              mWidth, mHeight);
        if (mInitialized) {
            mRender.releaseOpenGL();
            mInitialized = false;
        }
        if (!mRender.init(width, height)) {
            LOG_E("GLPreview", "重新初始化失败 : %d x %d", width, height);
            mWidth = 0;
            mHeight = 0;
            return;
        }
        mWidth = width;
        mHeight = height;
        mInitialized = true;
    }
    const int64_t ptsNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    mRender.renderFrame(data, width, height, ptsNs);
}
