//
// Created by MI T on 2026/3/23.
//

#ifndef OPENGLDEMO_GL_CONSTANTS_H
#define OPENGLDEMO_GL_CONSTANTS_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/native_window.h>
#include <string>

#ifndef EGL_RECORDABLE_ANDROID
#define EGL_RECORDABLE_ANDROID 0x3142
#endif

// 输出目标类型：
// 1. 只有 preview 时走普通直绘
// 2. preview + record 两个 target 时走 FBO 分发
enum GLTargetType {
    GL_TARGET_PREVIEW = 0,
    GL_TARGET_RECORD = 1,
};

// 输出 surface 的运行时描述对象，window 生命周期由 GLRender 接管
struct OutputSurfaceTarget {
    std::string id;
    ANativeWindow *window = nullptr;
    EGLSurface surface = EGL_NO_SURFACE;
    GLTargetType type = GL_TARGET_PREVIEW;
    int width = 0;
    int height = 0;
};

// 3. 配置 EGL 属性（同时支持 window surface / pbuffer surface）
static const EGLint attribConfigs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RECORDABLE_ANDROID, EGL_TRUE,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_NONE
};

// OpenGL 渲染上下文3.0
static const EGLint contextAttribES3[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
// OpenGL 渲染上下文2.0
static const EGLint contextAttribES2[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};

// 工作线程绑定的 pbuffer surface，主要用于 FBO 绘制与中转
static const EGLint pBufferSurfaceAttrs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
};

// 顶点坐标信息，全屏纹理 4 分量为 2d 坐标 + 2 分量 textCoord
static const float vertices[] = {
        -1.0f, -1.0f, 0.0f, 1.0f,
        1.0f, -1.0f, 1.0f, 1.0f,
        -1.0f, 1.0f, 0.0f, 0.0f,
        1.0f, 1.0f, 1.0f, 0.0f
};

// 输入阶段顶点着色器：负责镜像 / 旋转矩阵
static const char INPUT_VERTEX_SHADER_SOURCE[] = R"(#version 300 es
layout(location = 0) in vec4 a_position;
layout(location = 1) in vec2 a_textCoord;
uniform mat4 rotation_state;
uniform mat4 mirror_state;
out vec2 v_textCoord;
void main(){
    gl_Position = mirror_state * rotation_state * a_position;
    v_textCoord = a_textCoord;
}
)";

// 输入阶段片段着色器：当前仍按 BGR -> RGB 做上传转换
static const char INPUT_FRAGMENT_SHADER_SOURCE[] = R"(#version 300 es
precision mediump float;
in vec2 v_textCoord;
out vec4 fragColor;
uniform sampler2D mTexture;
void main(){
    vec3 bgr = texture(mTexture, v_textCoord).rgb;
    fragColor = vec4(bgr.b, bgr.g, bgr.r, 1.0);
}
)";

// 输出阶段顶点着色器：FBO 纹理直接输出到目标 surface
static const char OUTPUT_VERTEX_SHADER_SOURCE[] = R"(#version 300 es
layout(location = 0) in vec4 a_position;
layout(location = 1) in vec2 a_textCoord;
out vec2 v_textCoord;
void main(){
    gl_Position = a_position;
    // FBO 颜色附件再次采样时需要补一次 Y 翻转，否则预览在 direct -> FBO 切换时会上下颠倒
    v_textCoord = vec2(a_textCoord.x, 1.0 - a_textCoord.y);
}
)";

// 输出阶段片段着色器：直接采样 FBO 颜色附件
static const char OUTPUT_FRAGMENT_SHADER_SOURCE[] = R"(#version 300 es
precision mediump float;
in vec2 v_textCoord;
out vec4 fragColor;
uniform sampler2D mTexture;
void main(){
    fragColor = texture(mTexture, v_textCoord);
}
)";

#endif //OPENGLDEMO_GL_CONSTANTS_H
