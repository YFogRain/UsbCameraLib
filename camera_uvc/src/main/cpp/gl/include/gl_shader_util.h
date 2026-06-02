//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_GL_SHADER_UTIL_H
#define USBCAMERALIB_GL_SHADER_UTIL_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>

class GLShaderUtils {
public:
    static GLuint compileShader(GLenum type, const char *source);

    static GLuint createProgram(const char *vertexSource, const char *fragmentSource);
};

#endif //USBCAMERALIB_GL_SHADER_UTIL_H
