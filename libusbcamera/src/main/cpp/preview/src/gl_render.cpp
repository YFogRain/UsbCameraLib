//
// Created by MI T on 2026/3/23.
//
#include "gl_render.h"
#include "gl_constants.h"
#include "android/native_window_jni.h"
#include "android/native_window.h"
#include "Log.h"
#include "cmath"

GLRender::GLRender() : glContext() {
}

GLRender::~GLRender() {
    release();
    glContext = {};
}

bool GLRender::init(ANativeWindow *window, int width, int height) {
    if (!window)return false;
    auto viewWidth = ANativeWindow_getWidth(window);
    auto viewHeight = ANativeWindow_getHeight(window);
    LOG_D("当前窗口大小为 = %d*%d", viewWidth, viewHeight);
    LOG_D("图片实际大小为 = %d*%d", width, height);
    // 设置window的大小
    if (ANativeWindow_setBuffersGeometry(window, viewWidth, viewHeight, WINDOW_FORMAT_RGBA_8888) !=
        0) {
        LOG_E("设置window的buffer缓存大小失败");
        return false;
    }
    if (!initEGL(window)) {
        LOG_E("初始化EGL失败...");
        return false;
    }
    // 设置窗口尺寸
    glViewport(0, 0, viewWidth, viewHeight);
    // 创建着色器程序
    glContext.program = GLShaderUtils::createProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE);
    if (glContext.program == 0) {
        destroyEGL();
        LOG_E("创建着色器程序失败");
        return false;
    }
    // 使用着色器
    glUseProgram(glContext.program);

    // 获取着色器内的方向和镜像信息
    glContext.rotationState = glGetUniformLocation(glContext.program, "rotation_state");
    glContext.mirrorState = glGetUniformLocation(glContext.program, "mirror_state");

    // ========================
    // 新增：多格式支持变量
    // ========================
//    glContext.uFormat = glGetUniformLocation(glContext.program, "u_format");
    glContext.mRgbTexture = glGetUniformLocation(glContext.program, "mTexture");
//    glContext.mYTexture = glGetUniformLocation(glContext.program, "y_texture");
//    glContext.mUVTexture = glGetUniformLocation(glContext.program, "uv_texture");
    // 初始化纹理单元
    glUniform1i(glContext.mRgbTexture, 0);
//    glUniform1i(glContext.mYTexture, 0);
//    glUniform1i(glContext.mUVTexture, 1);

    // ========================
    // 创建 3 个纹理（支持YUV）
    // ========================
//    glGenTextures(1, &glContext.texY);
//    glGenTextures(1, &glContext.texUV);
    glGenTextures(1, &glContext.mTextureId);

    // 公共纹理配置
    auto configTexture = [](GLuint tex) {
        // 将纹理对象绑定到当前激活的纹理单元
        glBindTexture(GL_TEXTURE_2D, tex);
        // 设置纹理参数
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        // 防止边缘采样问题
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    };

    configTexture(glContext.mTextureId);
//    configTexture(glContext.texY);
//    configTexture(glContext.texUV);
    // 只分配，不传数据

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0,
                 GL_RGB, GL_UNSIGNED_BYTE, nullptr);
    glContext.texWidth = width;
    glContext.texHeight = height;
    initVertices();
    // 对齐修复
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    return true;
}

/**
 * 顶点数据初始化
 * @return
 */
bool GLRender::initVertices() {
    if (glContext.program == 0)return false;
    // 1. 创建VAP/VBO
    glGenVertexArrays(1, &glContext.vao);
    glGenBuffers(1, &glContext.vbo);

    // 绑定VAO
    glBindVertexArray(glContext.vao);
    // 绑定VBO，并上传顶点数据
    glBindBuffer(GL_ARRAY_BUFFER, glContext.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    // 配置位置属性
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *) 0);
    glEnableVertexAttribArray(0);

    // 配置纹理坐标属性
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float),
                          (void *) (2 * sizeof(float)));
    glEnableVertexAttribArray(1);

    // 解绑（防止后续操作污染）
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
    return true;

}

void GLRender::release() {
    // 1. 删除纹理（3个都要删）
    if (glContext.mTextureId != 0) {
        glDeleteTextures(1, &glContext.mTextureId);
        glContext.mTextureId = 0;
    }
//    if (glContext.texY != 0) {
//        glDeleteTextures(1, &glContext.texY);
//        glContext.texY = 0;
//    }
//    if (glContext.texUV != 0) {
//        glDeleteTextures(1, &glContext.texUV);
//        glContext.texUV = 0;
//    }

    // 2. 删除VAO/VBO（你自己的initVertices里创建的）
    if (glContext.vao != 0) {
        glDeleteVertexArrays(1, &glContext.vao);
        glContext.vao = 0;
    }
    if (glContext.vbo != 0) {
        glDeleteBuffers(1, &glContext.vbo);
        glContext.vbo = 0;
    }

    // 3. 删除着色器程序
    if (glContext.program != 0) {
        glUseProgram(0);
        glDeleteProgram(glContext.program);
        glContext.program = 0;
    }

    // 重置成员变量
    glContext.mirrorState = -1;
    glContext.rotationState = -1;
    // 释放egl
    destroyEGL();
}

void GLRender::updateMatrix(bool isMirror, int rotation) {
    if (glContext.program == 0)return;
    // 1. 构建镜像矩阵
    float mirror[16] = {
            isMirror ? -1.f : 1.f, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    float rad = rotation * M_PI / 180.f;
    float cosv = cos(rad);
    float sinv = sin(rad);
    // 2. 构建旋转矩阵（绕z轴旋转）
    float rot[16] = {
            cosv, -sinv, 0, 0,
            sinv, cosv, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };
    // 3. 将矩阵传递到着色器
    glUniformMatrix4fv(glContext.mirrorState, 1, GL_FALSE, mirror);
    glUniformMatrix4fv(glContext.rotationState, 1, GL_FALSE, rot);
}

/**
 * 初始化egl，创建window绑定对象
 * @param window window窗口
 * @param width  宽度
 * @param height 高度
 * @return 初始化结果
 */
bool GLRender::initEGL(ANativeWindow *window) {
    // 如果不存在window，则直接返回false
    if (!window)return false;
    // 1. 获取 EGL 显示连接（关联设备屏幕）
    auto eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        LOG_E("获取关联设备屏幕失败: %d", eglGetError());
        return false;
    }
    // 2. 初始化 EGL
    EGLint major, minor;
    if (!eglInitialize(eglDisplay, &major, &minor)) {
        LOG_E("初始化EGL失败: %d", eglGetError());
        return false;
    }
    EGLConfig eglConfig = nullptr;
    EGLint numConfigs;
    if (!eglChooseConfig(eglDisplay, attribConfigs, &eglConfig, 1, &numConfigs) ||
        numConfigs == 0 || !eglConfig) {
        LOG_E("获取EGL配置失败: %d", eglGetError());
        return false;
    }
    if (!eglConfig) {
        return false;
    }
    // 4. 创建 EGL 窗口表面（绑定 Android 窗口）
    auto eglSurface = eglCreateWindowSurface(eglDisplay, eglConfig, window, nullptr);
    if (eglSurface == EGL_NO_SURFACE) {
        LOG_E("创建EGL窗口表面失败: %d", eglGetError());
        return false;
    }
    // initEGL中修改contextAttribs逻辑
    EGLContext eglContext = eglCreateContext(eglDisplay, eglConfig, nullptr, contextAttribES3);
    if (eglContext == EGL_NO_CONTEXT) {
        LOG_E("创建egl3的context实例失败: %d", eglGetError());
        eglContext = eglCreateContext(eglDisplay, eglConfig, nullptr, contextAttribES2);
        if (eglContext == EGL_NO_CONTEXT) {
            eglDestroySurface(eglDisplay, eglSurface);
            LOG_E("创建egl2的context实例失败: %d", eglGetError());
            return false;
        }
    }

    // 6. 绑定上下文到当前线程（核心：让 OpenGL 指令生效）
    if (!eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        LOG_E("绑定上下文失败: %d", eglGetError());
        eglDestroySurface(eglDisplay, eglSurface);
        eglDestroyContext(eglDisplay, eglContext);
        return false;
    }
    glContext.eglSurface = eglSurface;
    glContext.eglDisplay = eglDisplay;
    glContext.eglContext = eglContext;
    // 7. 获取窗口尺寸
//    eglQuerySurface(eglDisplay, eglSurface, EGL_WIDTH, &width);
//    eglQuerySurface(eglDisplay, eglSurface, EGL_HEIGHT, &height);
    LOG_D("初始化完毕");
    return true;
}


void GLRender::destroyEGL() {
    if (glContext.eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(glContext.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (glContext.eglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(glContext.eglDisplay, glContext.eglContext);
            glContext.eglContext = EGL_NO_CONTEXT;
        }
        if (glContext.eglSurface != EGL_NO_SURFACE) {
            eglDestroySurface(glContext.eglDisplay, glContext.eglSurface);
            glContext.eglSurface = EGL_NO_SURFACE;
        }
        eglTerminate(glContext.eglDisplay);
        glContext.eglDisplay = EGL_NO_DISPLAY;
    }
}

void GLRender::render(uint8_t *data, int width, int height, GL_FORMAT format) {
    if (!data)return;
    if (!glContext.eglDisplay || !glContext.eglSurface)return;
    glUseProgram(glContext.program);
    // 设置格式
//    glUniform1i(glContext.uFormat, format);
//    glUniform1i(glContext.uFormat, format);
    // 清理buffer缓存
//    glClear(GL_COLOR_BUFFER_BIT);
//    if (format == FORMAT_RGB || format == FORMAT_BGR) {
    // 激活纹理
    glActiveTexture(GL_TEXTURE0);
    // 绑定纹理
    glBindTexture(GL_TEXTURE_2D, glContext.mTextureId);
    if (width != glContext.texWidth || height != glContext.texHeight) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0,
                     GL_RGB, GL_UNSIGNED_BYTE, nullptr);

        glContext.texWidth = width;
        glContext.texHeight = height;
    }
    // 每次更新图像
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                    width, height,
                    GL_RGB, GL_UNSIGNED_BYTE, data);
    // 绘制纹理
//    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0,
//                 GL_RGB, GL_UNSIGNED_BYTE, data);
//    } else if (format == FORMAT_NV21) {
//        // NV21
//        glActiveTexture(GL_TEXTURE0);
//        glBindTexture(GL_TEXTURE_2D, glContext.texY);
//        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, width, height, 0,
//                     GL_LUMINANCE, GL_UNSIGNED_BYTE, data);
//
//        glActiveTexture(GL_TEXTURE1);
//        glBindTexture(GL_TEXTURE_2D, glContext.texUV);
//        glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE_ALPHA, width / 2, height / 2, 0,
//                     GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, data + width * height);
//    }

    // 5. 绑定vao
    glBindVertexArray(glContext.vao);
    // 绘制4个顶点（三角带，2个三角形）
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    //  交换缓冲区（将后台缓冲区显示到屏幕）
    eglSwapBuffers(glContext.eglDisplay, glContext.eglSurface);
}


void GLRender::resetDraw() const {
    // 安全判断
    if (!glContext.eglDisplay || !glContext.eglSurface)return;
    // 确保当前线程绑定EGL上下文
    if (!eglMakeCurrent(glContext.eglDisplay, glContext.eglSurface, glContext.eglSurface,
                        glContext.eglContext)) {
        return;
    }
    glClear(GL_COLOR_BUFFER_BIT);
    glBindVertexArray(glContext.vao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    eglSwapBuffers(glContext.eglDisplay, glContext.eglSurface);
    // 解绑
    glBindVertexArray(0);
}

void GLRender::clearDraw() const {
    // 安全判断
    if (!glContext.eglDisplay || !glContext.eglSurface)return;
    // 确保当前线程绑定EGL上下文
    if (!eglMakeCurrent(glContext.eglDisplay, glContext.eglSurface, glContext.eglSurface,
                        glContext.eglContext)) {
        return;
    }
    // 清空屏幕为黑色
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    eglSwapBuffers(glContext.eglDisplay, glContext.eglSurface);
}