#include "../include/gl_render.h"
#include "Log.h"
#include <algorithm>

namespace {
    typedef EGLBoolean(EGLAPIENTRYP PFN_eglPresentationTimeANDROID_t)(
            EGLDisplay, EGLSurface, EGLnsecsANDROID);

    PFN_eglPresentationTimeANDROID_t gPresentationTime = nullptr;
}

GLRender::GLRender() = default;

GLRender::~GLRender() {
    release();
}

bool GLRender::addTarget(const std::string &targetId, ANativeWindow *window, int surfaceType,
                         int outputFormat) {
    if (!window || targetId.empty()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mMutex);
    auto it = std::find_if(outputSurfaces.begin(), outputSurfaces.end(),
                           [&](const OutputSurfaceTarget &item) { return item.id == targetId; });
    if (it != outputSurfaces.end()) {
        if (it->pendingRemove) {
            destroyOutputSurface(*it, true);
            outputSurfaces.erase(it);
        } else {
            ANativeWindow_release(window);
            return false;
        }
    }

    OutputSurfaceTarget target;
    target.id = targetId;
    target.window = window;
    target.type = static_cast<GLTargetType>(surfaceType);
    target.outputFormat = outputFormat;
    target.pendingCreate = glContext.initialized;
    outputSurfaces.push_back(target);
    return true;
}

bool GLRender::removeTarget(const std::string &targetId) {
    if (targetId.empty()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(mMutex);
    auto it = std::find_if(outputSurfaces.begin(), outputSurfaces.end(),
                           [&](const OutputSurfaceTarget &item) { return item.id == targetId; });
    if (it == outputSurfaces.end()) {
        return false;
    }
    if (!glContext.initialized || (it->surface == EGL_NO_SURFACE && it->pendingCreate)) {
        destroyOutputSurface(*it, true);
        outputSurfaces.erase(it);
        return true;
    }
    it->pendingCreate = false;
    it->pendingRemove = true;
    return true;
}

bool GLRender::hasTarget(int surfaceType) const {
    std::lock_guard<std::mutex> lock(mMutex);
    return std::any_of(outputSurfaces.begin(), outputSurfaces.end(),
                       [&](const OutputSurfaceTarget &item) {
                           return !item.pendingRemove && static_cast<int>(item.type) == surfaceType;
                       });
}

bool GLRender::init(int sourceWidth, int sourceHeight) {
    bool shouldRelease = false;
    {
        std::lock_guard<std::mutex> lock(mMutex);
        if (glContext.initialized) {
            return true;
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return false;
        }

        glContext.texWidth = sourceWidth;
        glContext.texHeight = sourceHeight;

        if (!initEGL() ||
            !initPrograms() ||
            !initVertices() ||
            !initInputTexture() ||
            !initFbo() ||
            !createOutputSurfaces()) {
            shouldRelease = true;
        } else {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glContext.initialized = true;
            return true;
        }
    }
    if (shouldRelease) {
        releaseOpenGL();
    }
    return false;
}

void GLRender::releaseOpenGL() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (glContext.eglDisplay != EGL_NO_DISPLAY && glContext.workSurface != EGL_NO_SURFACE) {
        makeCurrent(glContext.workSurface);
    }
    for (auto it = outputSurfaces.begin(); it != outputSurfaces.end();) {
        if (it->pendingRemove) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        destroyOutputSurface(*it, false);
        ++it;
    }
    destroyFbo();
    destroyInputTexture();
    destroyVertices();
    destroyPrograms();
    destroyEGL();
    pictureRequested.store(false);
    recording.store(false);
    glContext = {};
}

void GLRender::release() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (glContext.eglDisplay != EGL_NO_DISPLAY && glContext.workSurface != EGL_NO_SURFACE) {
        makeCurrent(glContext.workSurface);
    }
    for (auto &target: outputSurfaces) {
        destroyOutputSurface(target, true);
    }
    outputSurfaces.clear();
    destroyFbo();
    destroyInputTexture();
    destroyVertices();
    destroyPrograms();
    destroyEGL();
    pictureRequested.store(false);
    recording.store(false);
    glContext = {};
}

bool GLRender::renderFrame(const uint8_t *data, int width, int height, int64_t ptsNs) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!glContext.initialized || !data) {
        return false;
    }
    if (width != glContext.texWidth || height != glContext.texHeight) {
        LOG_E("GLRender", "renderFrame size mismatch: in=%d x %d, preview=%d x %d",
              width, height, glContext.texWidth, glContext.texHeight);
        return false;
    }
    if (!syncOutputSurfaces()) {
        return false;
    }
    if (!drawInputToFbo(data)) {
        return false;
    }
    return drawFboToOutputs(ptsNs);
}

void GLRender::requestTakePicture() {
    std::lock_guard<std::mutex> lock(mMutex);
    pictureRequested.store(true);
    LOG_D("GLRender", "触发拍照执行");
}

void GLRender::startRecord() {
    std::lock_guard<std::mutex> lock(mMutex);
    recording.store(true);
}

void GLRender::stopRecord() {
    std::lock_guard<std::mutex> lock(mMutex);
    recording.store(false);
}

bool GLRender::initEGL() {
    glContext.eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (glContext.eglDisplay == EGL_NO_DISPLAY) {
        LOG_E("GLRender", "eglGetDisplay failed: %d", eglGetError());
        return false;
    }
    if (!eglInitialize(glContext.eglDisplay, nullptr, nullptr)) {
        LOG_E("GLRender", "eglInitialize failed: %d", eglGetError());
        return false;
    }

    EGLint numConfigs = 0;
    if (!eglChooseConfig(glContext.eglDisplay, attribConfigs, &glContext.eglConfig, 1,
                         &numConfigs) || numConfigs <= 0 || !glContext.eglConfig) {
        LOG_E("GLRender", "eglChooseConfig failed: %d", eglGetError());
        return false;
    }

    glContext.eglContext = eglCreateContext(glContext.eglDisplay, glContext.eglConfig,
                                            EGL_NO_CONTEXT, contextAttribES3);
    if (glContext.eglContext == EGL_NO_CONTEXT) {
        glContext.eglContext = eglCreateContext(glContext.eglDisplay, glContext.eglConfig,
                                                EGL_NO_CONTEXT, contextAttribES2);
    }
    if (glContext.eglContext == EGL_NO_CONTEXT) {
        LOG_E("GLRender", "eglCreateContext failed: %d", eglGetError());
        return false;
    }

    glContext.workSurface = eglCreatePbufferSurface(glContext.eglDisplay, glContext.eglConfig,
                                                    pBufferSurfaceAttrs);
    if (glContext.workSurface == EGL_NO_SURFACE) {
        LOG_E("GLRender", "eglCreatePbufferSurface failed: %d", eglGetError());
        return false;
    }
    if (!makeCurrent(glContext.workSurface)) {
        return false;
    }

    if (!gPresentationTime) {
        gPresentationTime = reinterpret_cast<PFN_eglPresentationTimeANDROID_t>(
                eglGetProcAddress("eglPresentationTimeANDROID"));
    }
    return true;
}

void GLRender::destroyEGL() {
    if (glContext.eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(glContext.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (glContext.workSurface != EGL_NO_SURFACE) {
            eglDestroySurface(glContext.eglDisplay, glContext.workSurface);
            glContext.workSurface = EGL_NO_SURFACE;
        }
        if (glContext.eglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(glContext.eglDisplay, glContext.eglContext);
            glContext.eglContext = EGL_NO_CONTEXT;
        }
        eglTerminate(glContext.eglDisplay);
        glContext.eglDisplay = EGL_NO_DISPLAY;
    }
    glContext.eglConfig = nullptr;
}

bool GLRender::initPrograms() {
    glContext.inputProgram = GLShaderUtils::createProgram(INPUT_VERTEX_SHADER_SOURCE,
                                                          INPUT_FRAGMENT_SHADER);
    if (glContext.inputProgram == 0) {
        return false;
    }
    glContext.outputProgram = GLShaderUtils::createProgram(OUTPUT_VERTEX_SHADER_SOURCE,
                                                           OUTPUT_FRAGMENT_SHADER);
    if (glContext.outputProgram == 0) {
        return false;
    }

    glContext.mRgbTexture = glGetUniformLocation(glContext.inputProgram, "mTexture");
    glContext.outputTexture = glGetUniformLocation(glContext.outputProgram, "u_texture");
    return true;
}

void GLRender::destroyPrograms() {
    if (glContext.inputProgram != 0) {
        glDeleteProgram(glContext.inputProgram);
        glContext.inputProgram = 0;
    }
    if (glContext.outputProgram != 0) {
        glDeleteProgram(glContext.outputProgram);
        glContext.outputProgram = 0;
    }
    glContext.mRgbTexture = -1;
    glContext.outputTexture = -1;
}

bool GLRender::initVertices() {
    glGenVertexArrays(1, &glContext.vao);
    glGenBuffers(1, &glContext.vbo);

    glBindVertexArray(glContext.vao);
    glBindBuffer(GL_ARRAY_BUFFER, glContext.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *) 0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float),
                          (void *) (2 * sizeof(float)));
    glEnableVertexAttribArray(1);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
    return true;
}

void GLRender::destroyVertices() {
    if (glContext.vao != 0) {
        glDeleteVertexArrays(1, &glContext.vao);
        glContext.vao = 0;
    }
    if (glContext.vbo != 0) {
        glDeleteBuffers(1, &glContext.vbo);
        glContext.vbo = 0;
    }
}

bool GLRender::initInputTexture() {
    glGenTextures(1, &glContext.inputTextureId);
    glBindTexture(GL_TEXTURE_2D, glContext.inputTextureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB,
                 glContext.texWidth, glContext.texHeight,
                 0, GL_RGB, GL_UNSIGNED_BYTE, nullptr);

    return true;
}

void GLRender::destroyInputTexture() {
    if (glContext.inputTextureId != 0) {
        glDeleteTextures(1, &glContext.inputTextureId);
        glContext.inputTextureId = 0;
    }
}

bool GLRender::initFbo() {
    glGenTextures(1, &glContext.fboTextureId);
    glBindTexture(GL_TEXTURE_2D, glContext.fboTextureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                 glContext.texWidth, glContext.texHeight,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    glGenFramebuffers(1, &glContext.fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, glContext.fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, glContext.fboTextureId, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOG_E("GLRender", "FBO not complete: 0x%x", status);
        return false;
    }
    return true;
}

void GLRender::destroyFbo() {
    if (glContext.fbo != 0) {
        glDeleteFramebuffers(1, &glContext.fbo);
        glContext.fbo = 0;
    }
    if (glContext.fboTextureId != 0) {
        glDeleteTextures(1, &glContext.fboTextureId);
        glContext.fboTextureId = 0;
    }
}

bool GLRender::createOutputSurface(OutputSurfaceTarget &target) {
    LOG_D("GLRender", "createOutputSurface - 创建对象的surface = %d", target.type);
    if (!target.window) {
        LOG_E("GLRender", "createOutputSurface - surface不存在");
        return false;
    }
    target.width = ANativeWindow_getWidth(target.window);
    target.height = ANativeWindow_getHeight(target.window);
    if (target.width <= 0) {
        target.width = glContext.texWidth;
    }
    if (target.height <= 0) {
        target.height = glContext.texHeight;
    }
    if (target.type == GL_TARGET_PREVIEW &&
        ANativeWindow_setBuffersGeometry(target.window, 0, 0, WINDOW_FORMAT_RGBA_8888) != 0) {
        LOG_E("GLRender", "ANativeWindow_setBuffersGeometry failed");
        return false;
    }
    target.surface = eglCreateWindowSurface(glContext.eglDisplay, glContext.eglConfig,
                                            target.window, nullptr);
    if (target.surface == EGL_NO_SURFACE) {
        LOG_E("GLRender", "createOutputSurface - 创建surface失败 : %d", eglGetError());
        return false;
    }
    EGLint width = 0;
    EGLint height = 0;
    eglQuerySurface(glContext.eglDisplay, target.surface, EGL_WIDTH, &width);
    eglQuerySurface(glContext.eglDisplay, target.surface, EGL_HEIGHT, &height);
    if (width > 0) {
        target.width = width;
    }
    if (height > 0) {
        target.height = height;
    }

    LOG_D("GLRender", "createOutputSurface - 创建完成 : target=%s size=%d x %d type=%d",
          target.id.c_str(), target.width,
          target.height, target.type);
    return true;
}

bool GLRender::createOutputSurfaces() {
    bool previewCreated = false;
    bool hasPreviewTarget = false;
    for (auto &target: outputSurfaces) {
        if (target.pendingRemove) {
            continue;
        }
        if (target.type == GL_TARGET_PREVIEW) {
            hasPreviewTarget = true;
        }
        if (target.surface != EGL_NO_SURFACE) {
            if (target.type == GL_TARGET_PREVIEW) {
                previewCreated = true;
            }
            continue;
        }
        if (!createOutputSurface(target)) {
            if (target.type == GL_TARGET_PREVIEW) {
                return false;
            }
            target.pendingCreate = true;
            LOG_E("GLRender", "createOutputSurfaces skip non-preview target=%s type=%d",
                  target.id.c_str(), target.type);
            continue;
        }
        target.pendingCreate = false;
        if (target.type == GL_TARGET_PREVIEW) {
            previewCreated = true;
        }
    }
    return previewCreated || !hasPreviewTarget;
}

void GLRender::destroyOutputSurface(OutputSurfaceTarget &target, bool releaseWindow) {
    if (target.surface != EGL_NO_SURFACE && glContext.eglDisplay != EGL_NO_DISPLAY) {
        eglDestroySurface(glContext.eglDisplay, target.surface);
        target.surface = EGL_NO_SURFACE;
    }
    target.pendingCreate = false;
    target.pendingRemove = false;
    if (releaseWindow) {
        releaseTargetWindow(target);
    }
}

bool GLRender::makeCurrent(EGLSurface surface) {
    if (glContext.eglDisplay == EGL_NO_DISPLAY ||
        glContext.eglContext == EGL_NO_CONTEXT ||
        surface == EGL_NO_SURFACE) {
        return false;
    }
    if (!eglMakeCurrent(glContext.eglDisplay, surface, surface, glContext.eglContext)) {
        LOG_E("GLRender", "eglMakeCurrent failed: %d", eglGetError());
        return false;
    }
    return true;
}

bool GLRender::drawInputToFbo(const uint8_t *data) {
    if (!makeCurrent(glContext.workSurface)) {
        return false;
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, glContext.inputTextureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                    glContext.texWidth, glContext.texHeight,
                    GL_RGB, GL_UNSIGNED_BYTE, data);

    glBindFramebuffer(GL_FRAMEBUFFER, glContext.fbo);
    glViewport(0, 0, glContext.texWidth, glContext.texHeight);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(glContext.inputProgram);
    glUniform1i(glContext.mRgbTexture, 0);
    glBindVertexArray(glContext.vao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return true;
}

bool GLRender::syncOutputSurfaces() {
    if (!makeCurrent(glContext.workSurface)) {
        return false;
    }
    for (auto it = outputSurfaces.begin(); it != outputSurfaces.end();) {
        if (it->pendingRemove) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        if (it->pendingCreate && it->surface == EGL_NO_SURFACE) {
            if (createOutputSurface(*it)) {
                it->pendingCreate = false;
            } else {
                LOG_E("GLRender", "syncOutputSurfaces create failed target=%s", it->id.c_str());
            }
        }
        ++it;
    }
    return true;
}

bool GLRender::drawFboToOutputs(int64_t ptsNs) {
    for (auto &target: outputSurfaces) {
        if (target.pendingRemove || target.pendingCreate) {
            continue;
        }
        if (target.surface == EGL_NO_SURFACE) { // surface未初始化则跳过
            if (target.type == GL_TARGET_PICTURE && pictureRequested.load()) {
                LOG_D("GLRender", "跳过了拍照!! : %d - %d*%d", target.type, target.width, target.height);
            }
            continue;
        }
        // 如果当前不为拍照
        if (target.type == GL_TARGET_PICTURE && !pictureRequested.load()) {
            continue;
        }
        // 如果当前不为录制
        if (target.type == GL_TARGET_RECORD && !recording.load()) {
            continue;
        }
        // 切换至当前的surface
        if (!makeCurrent(target.surface)) {
            LOG_D("GLRender", "未绑定surface = %d", target.type);
            continue;
        }
        if (target.type == GL_TARGET_PICTURE && pictureRequested.load()) {
            LOG_D("GLRender", "执行拍照 = %d - %d*%d", target.type, target.width, target.height);
        }
        glViewport(0, 0, target.width, target.height);
        glClearColor(0.f, 0.f, 0.f, 1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(glContext.outputProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, glContext.fboTextureId);
        glUniform1i(glContext.outputTexture, 0);
        glBindVertexArray(glContext.vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);
        if (target.type == GL_TARGET_RECORD && gPresentationTime && ptsNs > 0) {
            gPresentationTime(glContext.eglDisplay, target.surface,
                              static_cast<EGLnsecsANDROID>(ptsNs));
        }
        if (!eglSwapBuffers(glContext.eglDisplay, target.surface)) {
            LOG_E("GLRender", "eglSwapBuffers failed target=%s type=%d err=%d",
                  target.id.c_str(), target.type, eglGetError());
        }
    }
    // 重置拍照状态防止多次触发
    if (pictureRequested.load()) {
        LOG_D("GLRender", "drawFboToOutputs - 拍照完成");
        pictureRequested.store(false);
    }
    if (glContext.workSurface != EGL_NO_SURFACE) {
        makeCurrent(glContext.workSurface);
    }
    return true;
}

void GLRender::releaseTargetWindow(OutputSurfaceTarget &target) {
    if (target.window) {
        ANativeWindow_release(target.window);
        target.window = nullptr;
    }
}
