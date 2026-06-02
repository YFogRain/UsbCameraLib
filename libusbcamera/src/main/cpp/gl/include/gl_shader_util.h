//
// Created by MI T on 2026/3/23.
//

#ifndef OPENGLDEMO_GL_SHADER_UTIL_H
#define OPENGLDEMO_GL_SHADER_UTIL_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <EGL/eglplatform.h>
#include <GLES3/gl3.h>

class GLShaderUtils {
public:
    // 编译单个着色器工具
    static GLuint compileShader(GLenum type, const char *source);

    // 创建着色器程序（链接顶点+片段着色器）
    static GLuint createProgram(const char *vertexSource, const char *fragmentSource);

private:
};

#endif //OPENGLDEMO_GL_SHADER_UTIL_H
