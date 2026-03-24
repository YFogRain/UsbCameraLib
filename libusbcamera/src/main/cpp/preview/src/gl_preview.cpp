//
// Created by MI T on 2026/3/24.
//
#include "gl_preview.h"

GLPreview::GLPreview() : mRender() {}

GLPreview::~GLPreview() {
    releaseSurface();
    mMirrorState = false;
    mRotation = 0;
    mRender = {};
}

void GLPreview::drawFrame(uint8_t *data, int width, int height, GL_FORMAT format) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mMatrixDirty.load()) { // 这里先更新一下方向
        mRender.updateMatrix(this->mMirrorState, this->mRotation);
        mMatrixDirty.store(false);
    }
    mRender.render(data, width, height, format);
}

void GLPreview::destroyRender() {
    std::lock_guard<std::mutex> lock(mMutex);
    mRender.release();
}

bool GLPreview::initRender(int width, int height) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mPreviewWindow)return false;
    if (!mRender.init(mPreviewWindow, width, height)) {
        return false;
    }
    mRender.updateMatrix(this->mMirrorState, this->mRotation);
    return true;
}
