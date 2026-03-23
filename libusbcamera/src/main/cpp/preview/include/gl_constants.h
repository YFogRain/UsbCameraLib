//
// Created by MI T on 2026/3/23.
//

#ifndef YZY_ANDROID_APP_GL_CONSTANTS_H
#define YZY_ANDROID_APP_GL_CONSTANTS_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <EGL/eglplatform.h>
#include <GLES3/gl3.h>

/**
 * 顶点着色器
 */
static const char VERTEX_SHADER[] = R"(#version 300 es
layout(location=0) in vec4 a_position;
layout(location=1) in vec2 a_textCoord;

uniform mat4 rotation_model;
uniform mat4 mirror_model;

out vec2 v_textCoord;
void main(){
    gl_Position = mirror_model * rotation_model * a_position;
    v_textCoord = vec2(a_textCoord.x, 1.0 - a_textCoord.y);
}

)";


/**
 * 片段着色器
 */
static const char FRAGMENT_SHADER[] = R"(#version 300 es
precision mediump float;

in vec2 v_textCoord;
uniform sampler2D uTexture;

out vec4 FragColor;
void main(){
    vec3 c = texture(uTexture, v_textCoord).rgb;
    FragColor = vec4(c.b, c.g, c.r, 1.0);
}
)";




#endif //YZY_ANDROID_APP_GL_CONSTANTS_H
