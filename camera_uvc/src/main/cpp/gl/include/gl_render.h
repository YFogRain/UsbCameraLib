//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_GL_RENDER_H
#define USBCAMERALIB_GL_RENDER_H

#include "gl_constants.h"
#include "gl_shader_util.h"
#include "camera_constants.h"
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

// openGL的核心变量
struct OpenGLContext {
    GLuint inputProgram = 0;              // 输入纹理 -> FBO
    GLuint outputProgram = 0;             // FBO纹理 -> Surface

    GLint mRgbTexture = -1;               // inputProgram sampler
    GLint outputTexture = -1;             // outputProgram sampler

    GLuint inputTextureId = 0;            // 唯一输入纹理，固定 BGR 数据上传
    GLuint fbo = 0;                       // 唯一共享 FBO
    GLuint fboTextureId = 0;              // FBO 颜色附件

    // ===== geometry =====
    GLuint vao = 0;
    GLuint vbo = 0;

    // ===== EGL =====
    EGLDisplay eglDisplay = EGL_NO_DISPLAY;
    EGLContext eglContext = EGL_NO_CONTEXT;
    EGLConfig eglConfig = nullptr;
    EGLSurface workSurface = EGL_NO_SURFACE;

    // ===== fixed preview size =====
    int texWidth = 0;
    int texHeight = 0;

    // state
    bool initialized = false;
};

/**
 * 实际绘制
 */
class GLRender {
public:
    std::atomic<bool> pictureRequested{false};
    std::atomic<bool> recording = {false};

    GLRender();

    ~GLRender();

    bool addTarget(const std::string &targetId, ANativeWindow *window, int surfaceType,
                   int outputFormat);

    bool removeTarget(const std::string &targetId);

    bool hasTarget(int surfaceType) const;

    bool init(int sourceWidth, int sourceHeight);

    void releaseOpenGL();

    void release();

    bool renderFrame(const uint8_t *data, int width, int height, int64_t ptsNs = 0);

    void requestTakePicture();

    void startRecord();

    void stopRecord();

private:
    mutable std::mutex mMutex; // 线程锁

    OpenGLContext glContext = {}; // 当前openGL的实例对象

    // 输出绘制的目标
    std::vector<OutputSurfaceTarget> outputSurfaces;

    // 初始化window窗口
    bool initEGL();

    // 释放内容
    void destroyEGL();

    bool initPrograms();

    void destroyPrograms();

    bool initVertices();

    void destroyVertices();

    bool initInputTexture();

    void destroyInputTexture();

    bool initFbo();

    void destroyFbo();

    bool createOutputSurface(OutputSurfaceTarget &target);

    bool createOutputSurfaces();

    void destroyOutputSurface(OutputSurfaceTarget &target, bool releaseWindow);

    bool makeCurrent(EGLSurface surface);

    bool drawInputToFbo(const uint8_t *data);

    bool drawFboToOutputs(int64_t ptsNs);

    bool syncOutputSurfaces();

    static void releaseTargetWindow(OutputSurfaceTarget &target);
};

#endif //USBCAMERALIB_GL_RENDER_H
