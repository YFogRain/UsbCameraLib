//
// Created by MI T on 2026/3/23.
//
#include "gl_shader_util.h"
#include "Log.h"

GLuint GLShaderUtils::compileShader(GLenum type, const char *source) {
    // 创建着色器对象
    GLuint shader = glCreateShader(type);
    if (shader == 0) {
        LOG_E("GLShaderUtils", "创建 %d 着色器失败", type);
        return 0;
    }
    // 绑定着色器代码
    glShaderSource(shader, 1, &source, nullptr);

    // 编译着色器
    glCompileShader(shader);
    // 检查编译错误
    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char infoLog[512];
        glGetShaderInfoLog(shader, 512, nullptr, infoLog);
        LOG_E("GLShaderUtils", "着色器编译失败：%s", infoLog);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLuint GLShaderUtils::createProgram(const char *vertexSource, const char *fragmentSource) {
    // 编译顶点着色器
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    if (vertexShader == 0) return 0;

    // 编译片段着色器
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (fragmentShader == 0) {
        glDeleteShader(vertexShader);
        return 0;
    }
    // 创建程序
    GLuint program = glCreateProgram();
    if (program == 0) {
        LOG_E("GLShaderUtils", "创建着色器程序失败");
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return 0;
    }
    // 附加着色器
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    // 链接
    glLinkProgram(program);

    // 检查链接错误
    GLint success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        char infoLog[512];
        glGetProgramInfoLog(program, 512, nullptr, infoLog);
        LOG_E("GLShaderUtils", "着色器程序链接失败：%s", infoLog);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        glDeleteProgram(program);
        return 0;
    }

    // 删除临时着色器
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    return program;

}
