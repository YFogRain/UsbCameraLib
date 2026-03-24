//
// Created by MI T on 2026/3/23.
//

#ifndef OPENGLDEMO_GL_RENDER_H
#define OPENGLDEMO_GL_RENDER_H

#include "gl_shader_util.h"
#include <cstdint>
#include <cstdlib>
#include "android/native_window.h"

/**
 * openGL初始化程序
 */

enum GL_FORMAT {
    FORMAT_RGB = 0,
    FORMAT_BGR = 1,
    FORMAT_NV21 = 2,
};

// openGL的核心变量
struct OpenGLContext {
    GLuint program = 0; // 着色器程序ID
    GLint rotationState = -1; // 方向配置
    GLint mirrorState = -1; // 镜像配置

    // 多格式支持
    GLint mRgbTexture = 0; // 纹理信息-默认使用（bgr、rgb）
//    GLint mYTexture = 0; // 纹理信息-Y轴
//    GLint mUVTexture = 0; // 纹理信息-uv
//    GLint uFormat = 0;

    // 释放的纹理
    GLuint mTexture = 0;
//    GLuint texY = 0;
//    GLuint texUV = 0;

    // 顶点数据
    GLuint vao = 0; // 顶点数组对象
    GLuint vbo = 0; // 顶点缓冲对象


    // EGL
    EGLDisplay eglDisplay = EGL_NO_DISPLAY;
    EGLContext eglContext = EGL_NO_CONTEXT;
    EGLSurface eglSurface = EGL_NO_SURFACE;
};

class GLRender {
public:
    GLRender();

    ~GLRender();

    bool init(ANativeWindow *window, int width, int height);

    void release();

    // 执行绘制
    void render(uint8_t *data, int width, int height, GL_FORMAT format) const;

    // 更新坐标配置
    void updateMatrix(bool isMirror, int rotation);

    void resetDraw() const;

    void clearDraw() const;

private:
    OpenGLContext glContext = {}; // 当前openGL的实例对象

    // 初始化window窗口
    bool initEGL(ANativeWindow *window);

    // 释放内容
    void destroyEGL();

    // 初始化顶点数据
    bool initVertices();


};

#endif //OPENGLDEMO_GL_RENDER_H
