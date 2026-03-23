#include "gl_renderer.h"
#include "cmath"
#include "Log.h"

GLuint GLRenderer::loadShader(GLenum type, const char *src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    checkShader(shader);
    return shader;
}

void GLRenderer::checkShader(GLuint shader) {
    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char log[512];
        glGetShaderInfoLog(shader, 512, nullptr, log);
        LOG_E("Shader error: %s", log);
    }
}

void GLRenderer::checkProgram(GLuint program) {
    GLint success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        char log[512];
        glGetProgramInfoLog(program, 512, nullptr, log);
        LOG_E("Program error: %s", log);
    }
}

void GLRenderer::init() {
    GLuint vs = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fs = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

    mProgram = glCreateProgram();
    glAttachShader(mProgram, vs);
    glAttachShader(mProgram, fs);
    glLinkProgram(mProgram);
    checkProgram(mProgram);

    glDeleteShader(vs);
    glDeleteShader(fs);

    // ⭐⭐⭐ 必须先 useProgram（否则 uniform 设置无效）
    glUseProgram(mProgram);

    // ⭐⭐⭐ 获取 uniform
    uRotationLoc = glGetUniformLocation(mProgram, "rotation_model");
    uMirrorLoc   = glGetUniformLocation(mProgram, "mirror_model");

    // ⭐⭐⭐ 新增：绑定纹理采样器（关键！！）
    GLint uTextureLoc = glGetUniformLocation(mProgram, "uTexture");
    glUniform1i(uTextureLoc, 0); // 绑定到 GL_TEXTURE0

    // =========================
    // 创建纹理
    // =========================
    glGenTextures(1, &mTexture);

    // ⭐⭐⭐ 必须指定 texture unit
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mTexture);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    // ⭐⭐⭐ 非常关键（防止边缘采样问题）
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // =========================
    // 顶点数据
    // =========================
    float vertices[] = {
            // position    // texCoord
            -1, -1,        0, 1,
            1, -1,        1, 1,
            -1,  1,        0, 0,
            1,  1,        1, 0
    };

    glGenVertexArrays(1, &mVAO);
    glGenBuffers(1, &mVBO);

    glBindVertexArray(mVAO);
    glBindBuffer(GL_ARRAY_BUFFER, mVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    // position
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void *)0);
    glEnableVertexAttribArray(0);

    // texCoord
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void *)(2 * sizeof(float)));
    glEnableVertexAttribArray(1);

    // ⭐⭐⭐ 解绑（防止外部污染）
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
}

void GLRenderer::updateMatrix() {
    float identity[16] = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    float mirror[16] = {
            mMirror ? -1.f : 1.f, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    float rad = mRotation * M_PI / 180.f;
    float cosv = cos(rad);
    float sinv = sin(rad);

    float rot[16] = {
            cosv,  -sinv, 0, 0,
            sinv,   cosv, 0, 0,
            0,      0,    1, 0,
            0,      0,    0, 1
    };
    glUniformMatrix4fv(uMirrorLoc, 1, GL_FALSE, mirror);
    glUniformMatrix4fv(uRotationLoc, 1, GL_FALSE, rot);
}

void GLRenderer::render(uint8_t *&data, int width, int height, int dataSize) {
    LOG_D("预览信息 size = %d = %d*%d", dataSize, width, height);
    if (!data) return;

    glUseProgram(mProgram);

    updateMatrix();
    // 对齐修复
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

    glBindTexture(GL_TEXTURE_2D, mTexture);

    // 初始化一次
    if (width != mTexW || height != mTexH) {
        mTexW = width;
        mTexH = height;
        glTexImage2D(GL_TEXTURE_2D, 0,
                     GL_RGB,
                     width, height,
                     0,
                     GL_RGB,
                     GL_UNSIGNED_BYTE,
                     nullptr);
    }

    // 更新数据
    glTexSubImage2D(GL_TEXTURE_2D, 0,
                    0, 0,
                    width, height,
                    GL_RGB,
                    GL_UNSIGNED_BYTE,
                    data);

    glBindVertexArray(mVAO);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void GLRenderer::setMirror(bool mirror) {
    mMirror = mirror;
}

void GLRenderer::setRotation(int degree) {
    mRotation = degree;
}
void GLRenderer::release() {
    if (mProgram != 0) {
        glDeleteProgram(mProgram);
        mProgram = 0;
    }
    if (mTexture != 0) {
        glDeleteTextures(1, &mTexture);
        mTexture = 0;
    }
    if (mVBO != 0) {
        glDeleteBuffers(1, &mVBO);
        mVBO = 0;
    }
    if (mVAO != 0) {
        glDeleteVertexArrays(1, &mVAO);
        mVAO = 0;
    }
    // 重置成员变量
    mTexW = 0;
    mTexH = 0;
    mMirror = false;
    mRotation = 0;
}