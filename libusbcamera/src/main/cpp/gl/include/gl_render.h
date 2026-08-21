//
// Created by MI T on 2026/3/23.
//

#ifndef OPENGLDEMO_GL_RENDER_H
#define OPENGLDEMO_GL_RENDER_H

#include "gl_constants.h"
#include "gl_shader_util.h"
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

// 渲染模式：
// 1. DIRECT：单 target，直接画到对应 surface
// 2. FBO：多 target，先画到 FBO 再分发
enum GLRenderMode {
    GL_RENDER_MODE_DIRECT = 0,
    GL_RENDER_MODE_FBO = 1,
};

// openGL 的核心变量
struct OpenGLContext {
    GLuint inputProgram = 0;          // 输入纹理 -> 目标 / FBO
    GLuint outputProgram = 0;         // FBO 纹理 -> 目标 surface

    GLint rotationState = -1;         // 方向配置
    GLint mirrorState = -1;           // 镜像配置

    GLint inputTextureLoc = -1;       // 输入 sampler
    GLint outputTextureLoc = -1;      // 输出 sampler

    GLuint inputTextureId = 0;        // 输入纹理

    GLuint fbo = 0;                   // 母 FBO
    GLuint fboTextureId = 0;          // FBO 颜色附件


    GLuint vao = 0;                   // 顶点数组对象
    GLuint vbo = 0;                   // 顶点缓冲对象


    EGLDisplay eglDisplay = EGL_NO_DISPLAY; // EGL 显示设备
    EGLContext eglContext = EGL_NO_CONTEXT; // EGL 上下文
    EGLConfig eglConfig = nullptr;          // EGL 配置
    EGLSurface workSurface = EGL_NO_SURFACE; // 工作 pbuffer surface
    int texWidth = 0;                 // 当前输入纹理宽度
    int texHeight = 0;                // 当前输入纹理高度
    bool initialized = false;         // 是否已经完成 OpenGL 初始化
    GLRenderMode renderMode = GL_RENDER_MODE_DIRECT; // 当前绘制模式
};

class GLRender {
public:
    GLRender();

    ~GLRender();

    // 添加一个输出目标，window 所有权转移到 GLRender。
    // 运行中不支持动态切换 target；请停止预览后重建。
    bool addTarget(const std::string &targetId, ANativeWindow *window, GLTargetType type);

    // 移除一个输出目标；运行中不支持动态移除 target
    bool removeTarget(const std::string &targetId);

    // 判断当前是否存在指定类型的输出目标
    bool hasTarget(GLTargetType type) const;

    // 初始化 OpenGL 与 EGL 资源
    bool init(int width, int height);

    // 释放所有 OpenGL / EGL 资源以及 target window
    void release();

    // 仅释放 OpenGL / EGL 资源，保留 target 列表
    void releaseOpenGL();

    // 执行绘制；模式由当前 target 数量自动决定
    bool renderFrame(const uint8_t *data, int width, int height, int64_t ptsNs = 0);

    // 更新镜像和旋转矩阵
    void updateMatrix(bool isMirror, int rotation);

    void resetDraw() const;

    void clearDraw() const;

    bool startRecord() {
        if (!hasTarget(GL_TARGET_RECORD))return false;
        recording.store(true);
        return true;
    }

    void stopRecord() { recording.store(false); }

private:
    mutable std::mutex mMutex;
    OpenGLContext glContext = {};                  // 当前 openGL 实例对象
    std::vector<OutputSurfaceTarget> outputSurfaces; // 当前所有输出目标
    std::atomic<bool> recording{false};           // 当前是否处于录制状态

    // 初始化 EGL（使用 workSurface 作为中转工作面）
    bool initEGL();

    // 销毁 EGL
    void destroyEGL();

    // 初始化 / 销毁 shader program
    bool initPrograms();

    void destroyPrograms();

    // 初始化 / 销毁顶点数据
    bool initVertices();

    void destroyVertices();

    // 初始化 / 销毁输入纹理
    bool initInputTexture();

    void destroyInputTexture();

    // 初始化 / 销毁 FBO（仅 FBO 模式需要）
    bool initFbo();

    void destroyFbo();

    // 创建 / 销毁单个输出 surface
    bool createOutputSurface(OutputSurfaceTarget &target) const;

    void destroyOutputSurface(OutputSurfaceTarget &target, bool releaseWindow) const;

    // 初始化当前输出 target 对应的 surface
    bool syncOutputSurfaces();

    // 根据当前可绘制 target 数量刷新渲染模式
    bool initRenderMode();

    // 统计当前可绘制 target 数量
    int countActiveTargets() const;

    // 不重复加锁的内部释放逻辑
    void releaseOpenGLLocked();

    void releaseLocked();

    // 绑定指定 EGLSurface 到当前线程
    bool makeCurrent(EGLSurface surface) const;

    // 将输入数据上传到纹理并完成一次 draw call
    bool drawInputTexture(const uint8_t *data, int width, int height);

    // 单目标普通直绘
    bool drawDirect(const uint8_t *data, int width, int height, int64_t ptsNs);

    // 多目标时先渲染到 FBO
    bool drawInputToFbo(const uint8_t *data, int width, int height);

    // 将 FBO 内容分发到各个 target
    bool drawFboToTargets(int64_t ptsNs);

    // 释放 target 持有的 native window
    static void releaseTargetWindow(OutputSurfaceTarget &target);
};

#endif //OPENGLDEMO_GL_RENDER_H
