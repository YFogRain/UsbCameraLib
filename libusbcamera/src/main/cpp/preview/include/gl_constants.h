//
// Created by MI T on 2026/3/23.
//

#ifndef OPENGLDEMO_GL_CONSTANTS_H
#define OPENGLDEMO_GL_CONSTANTS_H

/**
 * 着色器的配置参数
 */

// 顶点着色器
static const char VERTEX_SHADER_SOURCE[] = R"(#version 300 es // 设置openGL版本
layout(location = 0) in vec4 a_position; // 位置属性
layout(location = 1) in vec2 a_textCoord; // 纹理坐标属性

uniform mat4 rotation_state; // 旋转方向配置
uniform mat4 mirror_state; // 左右镜像配置
out vec2 v_textCoord; // 传递给片段着色器的信息
void main(){
    gl_Position = mirror_state * rotation_state * a_position; // 内置变量，最终输出的顶点裁剪空间坐标（必须赋值）
//    v_textCoord = vec2(a_textCoord.x,1.0 - a_textCoord.y); // 纹理坐标 y 轴翻转（因为 OpenGL 纹理原点在左下角，而安卓 Surface 原点在左上角）。
    v_textCoord = a_textCoord; // 纹理坐标 y 轴翻转（因为 OpenGL 纹理原点在左下角，而安卓 Surface 原点在左上角）。
}
)";


// 片段着色器
static const char FRAGMENT_SHADER_SOURCE[] = R"(#version 300 es // 设置openGL版本
precision mediump float; // 声明float类型

in vec2 v_textCoord; // 输入纹理坐标属性

out vec4 fragColor; // 输出像素颜色

uniform sampler2D mTexture;

// 纹理（YUV 使用 2 个纹理）
// uniform sampler2D y_texture;
// uniform sampler2D uv_texture;
// 默认的纹理
// uniform sampler2D rgb_texture;

// 格式类型,0 = RGB、1 = BGR、2 = NV21 / YUV420SP
// uniform int u_format;

void main(){
//    vec3 rgb;
//    if(u_format == 0){
//        rgb = texture(rgb_texture, v_textCoord).rgb;
//    }else if(u_format == 1){
//        // BGR → RGB
//        vec3 bgr = texture(rgb_texture, v_textCoord).rgb;
//        rgb = vec3(bgr.b, bgr.g, bgr.r);
//    }else if(u_format == 2){
//        // 你的 Kotlin 生成的 NV21 必须用这种读取方式 ✅
//        float y = texture(y_texture, v_textCoord).r;
//        float u = texture(uv_texture, v_textCoord).a - 0.5;
//        float v = texture(uv_texture, v_textCoord).r - 0.5;
//        y = 1.1643 * (y - 0.0625);
//        rgb = vec3(y + 1.5958 * v,y - 0.39173 * u - 0.81290 * v,y + 2.017 * u);
//    }else {
//        rgb = vec3(0.0);
//    }
     vec3 bgr = texture(mTexture, v_textCoord).rgb;
    fragColor = vec4(bgr.b,bgr.g, bgr.r,1.0); // 交换R/B通道（颜色反转）
}
)";


// 3. 配置 EGL 属性（选择渲染格式）
static const EGLint attribConfigs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, // 支持 OpenGL ES 3.0
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,        // 窗口表面
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

// 顶点坐标信息，全屏纹理 4分量为2d坐标+2分量textCoord
static const float vertices[] = {
        // position (x,y)  |  texCoord (u,v)
        -1.0f, -1.0f, 0.0f, 1.0f, // 左下
        1.0f, -1.0f, 1.0f, 1.0f, // 右下
        -1.0f, 1.0f, 0.0f, 0.0f, // 左上
        1.0f, 1.0f, 1.0f, 0.0f  // 右上
};

#endif //OPENGLDEMO_GL_CONSTANTS_H
