

#include "gl_preview.h"
#include "Log.h"
#include <android/native_window_jni.h>

GLPreview::GLPreview() {}

GLPreview::~GLPreview() {
    destroyEGL();
    if (mWindow) {
        ANativeWindow_release(mWindow);
        mWindow = nullptr;
    }
}

void GLPreview::setCurrentSurface(ANativeWindow *window) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!window || mWindow == window)return;
    if (mWindow) {
        ANativeWindow_release(mWindow);
    }
    // 增加引用计数（关键）
    ANativeWindow_acquire(window);
    mWindow = window;
    ANativeWindow_setBuffersGeometry(window, 0, 0, UVC_FORMAT_FRAME_WINDOW);
}

bool GLPreview::initPreview(int width, int height) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mWindow)return false;
    // // 设置window的大小
    if (ANativeWindow_setBuffersGeometry(mWindow, width, height, UVC_FORMAT_FRAME_WINDOW) != 0) {
        ANativeWindow_release(mWindow);
        mWindow = nullptr;
        return false;
    }
    if (!initEGL(mWindow)) { // 初始化EGL
        return false;
    }
    mRenderer.init();
    // 设置窗口尺寸
    glViewport(0, 0, width, height);
    return true;
}

bool GLPreview::initEGL(ANativeWindow *window) {
    LOG_D("执行初始化openGL....");
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mDisplay == EGL_NO_DISPLAY) return false;

    if (!eglInitialize(mDisplay, nullptr, nullptr)) return false;

    const EGLint configAttribs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
            EGL_BLUE_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_RED_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;

    // 优化后
    if (!eglChooseConfig(mDisplay, configAttribs, &config, 1, &numConfigs) || numConfigs == 0) {
        LOG_E("eglChooseConfig failed, no matching config");
        return false;
    }


    mSurface = eglCreateWindowSurface(mDisplay, config, window, nullptr);

    // initEGL中修改contextAttribs逻辑
    EGLint contextAttribsES3[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    mContext = eglCreateContext(mDisplay, config, nullptr, contextAttribsES3);
    if (mContext == EGL_NO_CONTEXT) {
        LOG_E("ES3 context failed, fallback to ES2");
        EGLint contextAttribsES2[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        mContext = eglCreateContext(mDisplay, config, nullptr, contextAttribsES2);
        if (mContext == EGL_NO_CONTEXT) {
            return false;
        }
    }
    if (mSurface == EGL_NO_SURFACE || mContext == EGL_NO_CONTEXT) {
        return false;
    }

    if (!eglMakeCurrent(mDisplay, mSurface, mSurface, mContext)) {
        return false;
    }

    return true;
}

void GLPreview::drawFrame(uint8_t *data, int w, int h, int dataSize) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mDisplay || !mSurface) return;
    // 确保当前线程绑定EGL上下文
    if (!eglMakeCurrent(mDisplay, mSurface, mSurface, mContext)) {
        LOG_E("eglMakeCurrent failed in drawFrame");
        return;
    }
    glClear(GL_COLOR_BUFFER_BIT);

    mRenderer.render(data, w, h, dataSize);

    eglSwapBuffers(mDisplay, mSurface);
}

void GLPreview::setMirror(bool mirror) {
    std::lock_guard<std::mutex> lock(mMutex);
    mRenderer.setMirror(mirror);
}


void GLPreview::setRotation(int degree) {
    std::lock_guard<std::mutex> lock(mMutex);
    mRenderer.setRotation(degree);
}

void GLPreview::releasePreview() {
    std::lock_guard<std::mutex> lock(mMutex);
    // 先释放渲染器资源
    mRenderer.release();
    destroyEGL();

    mDisplay = EGL_NO_DISPLAY;
    mSurface = EGL_NO_SURFACE;
    mContext = EGL_NO_CONTEXT;
}

void GLPreview::destroyEGL() {
    if (mDisplay != EGL_NO_DISPLAY) {
        // 先解绑上下文（关键，避免其他线程占用）
        EGLBoolean ret = eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (!ret) {
            LOG_E("eglMakeCurrent failed when destroy EGL: %d", eglGetError());
        }
        if (mContext != EGL_NO_CONTEXT) {
            ret = eglDestroyContext(mDisplay, mContext);
            if (!ret) LOG_E("eglDestroyContext failed: %d", eglGetError());
            mContext = EGL_NO_CONTEXT;
        }
        if (mSurface != EGL_NO_SURFACE) {
            ret = eglDestroySurface(mDisplay, mSurface);
            if (!ret) LOG_E("eglDestroySurface failed: %d", eglGetError());
            mSurface = EGL_NO_SURFACE;
        }
        ret = eglTerminate(mDisplay);
        if (!ret) LOG_E("eglTerminate failed: %d", eglGetError());
        mDisplay = EGL_NO_DISPLAY;
    }
}