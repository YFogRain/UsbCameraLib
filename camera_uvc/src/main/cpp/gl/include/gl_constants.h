//
// Created by MI T on 2026/3/23.
//

#ifndef OPENGLDEMO_GL_CONSTANTS_H
#define OPENGLDEMO_GL_CONSTANTS_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/native_window.h>
#include <string>

// gl输入类型
enum GLTargetType {
    GL_TARGET_PREVIEW = 0,
    GL_TARGET_CALLBACK = 1,
    GL_TARGET_PICTURE = 2,
    GL_TARGET_RECORD = 3,
};

// 输出格式枚举（与 camera_constants.h 中 PREVIEW_FORMAT_* 取值保持一致）
enum GLOutputFormat {
    GL_OUTPUT_FORMAT_YUYV = 1,    // YUY2 packed
    GL_OUTPUT_FORMAT_NV21 = 2,    // YUV420 semi-planar (V/U)
    GL_OUTPUT_FORMAT_RGBA = 4,
    GL_OUTPUT_FORMAT_RGB = 5,
    GL_OUTPUT_FORMAT_JPEG = 7,
    GL_OUTPUT_FORMAT_YUV420SP = 8, // 通用 YUV420 semi-planar，等价于 NV12 (U/V)
};

// 渲染目标
struct OutputSurfaceTarget {
    std::string id;
    ANativeWindow *window = nullptr;
    EGLSurface surface = EGL_NO_SURFACE;
    GLTargetType type = GL_TARGET_PREVIEW;
    int outputFormat = GL_OUTPUT_FORMAT_RGBA;
    int width = 0;
    int height = 0;
    bool pendingCreate = false;
    bool pendingRemove = false;
};

// 3. 配置 EGL 属性（选择渲染格式）
static const EGLint attribConfigs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, // 支持 OpenGL ES 3.0
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT, // 窗口表面
        EGL_RECORDABLE_ANDROID, EGL_TRUE,        // 支持 MediaCodec / ImageReader 等生产者 surface
        EGL_RED_SIZE, 8,                         // 红通道 8 位
        EGL_GREEN_SIZE, 8,                       // 绿通道 8 位
        EGL_BLUE_SIZE, 8,                        // 蓝通道 8 位
        EGL_ALPHA_SIZE, 8,                       // 透明通道 8 位
        EGL_DEPTH_SIZE, 16,                      // 深度缓冲区 16 位
        EGL_NONE                                 // 结束标记
};

// OpenGL 渲染上下文3.0
static const EGLint contextAttribES3[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
// OpenGL 渲染上下文2.0
static const EGLint contextAttribES2[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};

// pbuffer 默认属性
static const EGLint pBufferSurfaceAttrs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
};

// 顶点坐标信息，全屏纹理 4分量为2d坐标+2分量textCoord
static const float vertices[] = {
        // position (x,y)  |  texCoord (u,v)
        -1.0f, -1.0f, 0.0f, 1.0f, // 左下
        1.0f, -1.0f, 1.0f, 1.0f, // 右下
        -1.0f, 1.0f, 0.0f, 0.0f, // 左上
        1.0f, 1.0f, 1.0f, 0.0f  // 右上
};

// 输入阶段顶点着色器：CPU 图像上传到纹理后做一次 Y 翻转
static const char INPUT_VERTEX_SHADER_SOURCE[] = R"(#version 300 es
layout(location = 0) in vec4 a_position; // 位置属性
layout(location = 1) in vec2 a_textCoord; // 纹理坐标属性

out vec2 v_textCoord; // 传递给片段着色器的信息
void main(){
    gl_Position = a_position; // 内置变量，最终输出的顶点裁剪空间坐标（必须赋值）
    v_textCoord = vec2(a_textCoord.x, 1.0 - a_textCoord.y);
}
)";

// 输出阶段顶点着色器：FBO 结果直接输出到 surface
static const char OUTPUT_VERTEX_SHADER_SOURCE[] = R"(#version 300 es
layout(location = 0) in vec4 a_position; // 位置属性
layout(location = 1) in vec2 a_textCoord; // 纹理坐标属性

out vec2 v_textCoord; // 传递给片段着色器的信息
void main(){
    gl_Position = a_position;
    v_textCoord = a_textCoord;
}
)";


// FBO输入的片段着色器
static const char INPUT_FRAGMENT_SHADER[] = R"(#version 300 es
precision mediump float; // 声明float类型
in vec2 v_textCoord; // 输入纹理坐标属性
out vec4 fragColor; // 输出像素颜色
uniform sampler2D mTexture;

void main(){
     vec3 bgr = texture(mTexture, v_textCoord).rgb;
    fragColor = vec4(bgr.b,bgr.g, bgr.r,1.0); // 交换R/B通道（颜色反转）
}
)";

// 输出的片段着色器
static const char OUTPUT_FRAGMENT_SHADER[] = R"(#version 300 es
precision mediump float;
in vec2 v_textCoord;
uniform sampler2D u_texture;
out vec4 fragColor;
void main() {
    fragColor = texture(u_texture, v_textCoord);
}
)";

#endif //OPENGLDEMO_GL_CONSTANTS_H
