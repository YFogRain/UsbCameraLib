//
// Created by MI T on 2026/3/23.
//

#ifndef YZY_ANDROID_APP_GL_RENDERER_H
#define YZY_ANDROID_APP_GL_RENDERER_H

#include <GLES3/gl3.h>
#include "gl_constants.h"
#include "camera_constants.h"

/**
 * 格式处理、shader切换
 */
class GLRenderer {
public:
    // 初始化
    void init();

    void release();

    void render(uint8_t *&data, int width, int height, int dataSize);

    // 镜像
    void setMirror(bool mirror);

    // 旋转方向
    void setRotation(int degree);

    int getRotation() {
        return mRotation;
    }

    bool getMirrorState() {
        return mMirror;
    }


private:
    static GLuint loadShader(GLenum type, const char *src);

    static void checkShader(GLuint shader);

    static void checkProgram(GLuint program);

    void updateMatrix();


private:
    GLuint mProgram = 0;
    GLuint mTexture = 0;

    GLuint mVAO = 0;
    GLuint mVBO = 0;

    GLint uRotationLoc = -1;
    GLint uMirrorLoc = -1;

    bool mMirror = false;
    int mRotation = 0;

    int mTexW = 0;
    int mTexH = 0;
};

#endif //YZY_ANDROID_APP_GL_RENDERER_H
