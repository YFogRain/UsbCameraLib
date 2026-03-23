//
// Created by MI T on 2026/3/23.
//

#ifndef YZY_ANDROID_APP_GL_PREVIEW_H
#define YZY_ANDROID_APP_GL_PREVIEW_H

#include "android/native_window.h"
#include "gl_renderer.h"
#include <mutex>

/**
 * openGL预览类
 */
class GLPreview {
public:
    GLPreview();

    ~GLPreview();

    void setCurrentSurface(ANativeWindow *window);

    //  初始化window
    bool initPreview(int width, int height);

    void drawFrame(uint8_t *data, int width, int height, int dataSize); // 绘制

    // 控制
    void setMirror(bool mirror); // 是否镜像

    void setRotation(int degree); // 设置方向

    void releasePreview(); // 释放

    int getRotation() {
        return mRenderer.getRotation();
    }

    bool getMirrorState() {
        return mRenderer.getMirrorState();
    }

private:
    bool initEGL(ANativeWindow *window);

    void destroyEGL();

private:
    std::mutex mMutex;

    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLSurface mSurface = EGL_NO_SURFACE;
    EGLContext mContext = EGL_NO_CONTEXT;

    ANativeWindow *mWindow = nullptr;

    GLRenderer mRenderer;
};

#endif //YZY_ANDROID_APP_GL_PREVIEW_H
