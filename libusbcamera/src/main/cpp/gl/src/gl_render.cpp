//
// Created by MI T on 2026/3/23.
//

#include "gl_render.h"
#include "Log.h"
#include <algorithm>
#include <cmath>

namespace {
    typedef EGLBoolean(EGLAPIENTRYP PFN_eglPresentationTimeANDROID_t)(
            EGLDisplay, EGLSurface, EGLnsecsANDROID);

    PFN_eglPresentationTimeANDROID_t gPresentationTime = nullptr;
}

GLRender::GLRender() = default;

GLRender::~GLRender() {
    release();
}

bool GLRender::addTarget(const std::string &targetId, ANativeWindow *window, GLTargetType type) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!window || targetId.empty()) {
        return false;
    }
    if (glContext.initialized) {
        LOG_E("GLRender", "addTarget requires preview restart: %s", targetId.c_str());
        return false;
    }

    auto it = std::find_if(outputSurfaces.begin(), outputSurfaces.end(),
                           [&](const OutputSurfaceTarget &item) { return item.id == targetId; });
    if (it != outputSurfaces.end()) {
        destroyOutputSurface(*it, true);
        outputSurfaces.erase(it);
    }

    OutputSurfaceTarget target;
    target.id = targetId;
    target.window = window;
    target.type = type;
    outputSurfaces.push_back(target);
    return true;
}

bool GLRender::removeTarget(const std::string &targetId) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (targetId.empty()) {
        return false;
    }
    if (glContext.initialized) {
        LOG_E("GLRender", "removeTarget requires preview restart: %s", targetId.c_str());
        return false;
    }
    for (auto it = outputSurfaces.begin(); it != outputSurfaces.end(); ++it) {
        if (it->id == targetId) {
            destroyOutputSurface(*it, true);
            outputSurfaces.erase(it);
            return true;
        }
    }
    return false;
}

bool GLRender::hasTarget(GLTargetType type) const {
    std::lock_guard<std::mutex> lock(mMutex);
    return std::any_of(outputSurfaces.begin(), outputSurfaces.end(),
                       [&](const OutputSurfaceTarget &item) {
                           return item.type == type;
                       });
}

bool GLRender::init(int width, int height) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (glContext.initialized) {
        return true;
    }
    if (width <= 0 || height <= 0 || outputSurfaces.empty()) {
        return false;
    }
    glContext.texWidth = width;
    glContext.texHeight = height;
    if (!initEGL() ||
        !initPrograms() ||
        !initVertices() ||
        !initInputTexture() ||
        !syncOutputSurfaces() ||
        !initRenderMode()) {
        releaseOpenGLLocked();
        return false;
    }
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glContext.initialized = true;
    return true;
}

void GLRender::releaseOpenGL() {
    std::lock_guard<std::mutex> lock(mMutex);
    releaseOpenGLLocked();
}

void GLRender::release() {
    std::lock_guard<std::mutex> lock(mMutex);
    releaseLocked();
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
    if (outputSurfaces.empty() || !initRenderMode()) {
        return false;
    }
    if (glContext.renderMode == GL_RENDER_MODE_FBO) {
        return drawInputToFbo(data, width, height) && drawFboToTargets(ptsNs);
    }
    return drawDirect(data, width, height, ptsNs);
}

void GLRender::updateMatrix(bool isMirror, int rotation) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (glContext.inputProgram == 0) {
        return;
    }
    float mirror[16] = {
            isMirror ? -1.f : 1.f, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    float rad = rotation * M_PI / 180.f;
    float cosv = cos(rad);
    float sinv = sin(rad);
    float rot[16] = {
            cosv, -sinv, 0, 0,
            sinv, cosv, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };
    glUseProgram(glContext.inputProgram);
    glUniformMatrix4fv(glContext.mirrorState, 1, GL_FALSE, mirror);
    glUniformMatrix4fv(glContext.rotationState, 1, GL_FALSE, rot);
}

void GLRender::resetDraw() const {
    std::lock_guard<std::mutex> lock(mMutex);
    if (glContext.eglDisplay == EGL_NO_DISPLAY || glContext.workSurface == EGL_NO_SURFACE) {
        return;
    }
    makeCurrent(glContext.workSurface);
    glClear(GL_COLOR_BUFFER_BIT);
}

void GLRender::clearDraw() const {
    std::lock_guard<std::mutex> lock(mMutex);
    if (glContext.eglDisplay == EGL_NO_DISPLAY || glContext.workSurface == EGL_NO_SURFACE) {
        return;
    }
    makeCurrent(glContext.workSurface);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
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
                                                          INPUT_FRAGMENT_SHADER_SOURCE);
    if (glContext.inputProgram == 0) {
        return false;
    }
    glContext.outputProgram = GLShaderUtils::createProgram(OUTPUT_VERTEX_SHADER_SOURCE,
                                                           OUTPUT_FRAGMENT_SHADER_SOURCE);
    if (glContext.outputProgram == 0) {
        return false;
    }
    glContext.rotationState = glGetUniformLocation(glContext.inputProgram, "rotation_state");
    glContext.mirrorState = glGetUniformLocation(glContext.inputProgram, "mirror_state");
    glContext.inputTextureLoc = glGetUniformLocation(glContext.inputProgram, "mTexture");
    glContext.outputTextureLoc = glGetUniformLocation(glContext.outputProgram, "mTexture");
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
    glContext.rotationState = -1;
    glContext.mirrorState = -1;
    glContext.inputTextureLoc = -1;
    glContext.outputTextureLoc = -1;
}

bool GLRender::initVertices() {
    glGenVertexArrays(1, &glContext.vao);
    glGenBuffers(1, &glContext.vbo);
    glBindVertexArray(glContext.vao);
    glBindBuffer(GL_ARRAY_BUFFER, glContext.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void *) nullptr);
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
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, glContext.texWidth, glContext.texHeight, 0,
                 GL_RGB, GL_UNSIGNED_BYTE, nullptr);
    return true;
}

void GLRender::destroyInputTexture() {
    if (glContext.inputTextureId != 0) {
        glDeleteTextures(1, &glContext.inputTextureId);
        glContext.inputTextureId = 0;
    }
}

bool GLRender::initFbo() {
    if (glContext.fbo != 0 && glContext.fboTextureId != 0) {
        return true;
    }
    glGenTextures(1, &glContext.fboTextureId);
    glBindTexture(GL_TEXTURE_2D, glContext.fboTextureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, glContext.texWidth, glContext.texHeight, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glGenFramebuffers(1, &glContext.fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, glContext.fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                           glContext.fboTextureId, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOG_E("GLRender", "FBO not complete: 0x%x", status);
        destroyFbo();
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

bool GLRender::createOutputSurface(OutputSurfaceTarget &target) const {
    if (!target.window) {
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
    // 全部设置为RGBA
    if (ANativeWindow_setBuffersGeometry(target.window, 0, 0, WINDOW_FORMAT_RGBA_8888) != 0) {
        LOG_E("GLRender", "ANativeWindow_setBuffersGeometry failed");
        return false;
    }
    target.surface = eglCreateWindowSurface(glContext.eglDisplay, glContext.eglConfig,
                                            target.window, nullptr);
    if (target.surface == EGL_NO_SURFACE) {
        LOG_E("GLRender", "createOutputSurface failed: %d", eglGetError());
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
    return true;
}

void GLRender::destroyOutputSurface(OutputSurfaceTarget &target, bool releaseWindow) const {
    if (target.surface != EGL_NO_SURFACE && glContext.eglDisplay != EGL_NO_DISPLAY) {
        eglDestroySurface(glContext.eglDisplay, target.surface);
        target.surface = EGL_NO_SURFACE;
    }
    if (releaseWindow) {
        releaseTargetWindow(target);
    }
}

bool GLRender::syncOutputSurfaces() {
    if (!makeCurrent(glContext.workSurface)) {
        return false;
    }
    for (auto it = outputSurfaces.begin(); it != outputSurfaces.end();) {
        if (!it->window) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        if (it->surface == EGL_NO_SURFACE) {
            if (!createOutputSurface(*it)) {
                LOG_E("GLRender", "create target surface failed: %s", it->id.c_str());
                destroyOutputSurface(*it, true);
                it = outputSurfaces.erase(it);
                continue;
            }
        }
        ++it;
    }
    return !outputSurfaces.empty();
}

bool GLRender::initRenderMode() {
    const int targetCount = countActiveTargets();
    if (targetCount <= 0) {
        return false;
    }
    const GLRenderMode desiredMode = targetCount >= 2 ? GL_RENDER_MODE_FBO : GL_RENDER_MODE_DIRECT;
    if (desiredMode == glContext.renderMode) {
        if (desiredMode != GL_RENDER_MODE_FBO ||
            (glContext.fbo != 0 && glContext.fboTextureId != 0)) {
            return true;
        }
    }
    if (!makeCurrent(glContext.workSurface)) {
        return false;
    }
    if (desiredMode == GL_RENDER_MODE_FBO) {
        if (glContext.fbo == 0 || glContext.fboTextureId == 0) {
            if (!initFbo()) {
                return false;
            }
        }
    } else if (glContext.fbo != 0 || glContext.fboTextureId != 0) {
        destroyFbo();
    }
    glContext.renderMode = desiredMode;
    return true;
}

int GLRender::countActiveTargets() const {
    return static_cast<int>(std::count_if(outputSurfaces.begin(), outputSurfaces.end(),
                                          [&](const OutputSurfaceTarget &item) {
                                              if (!item.window) {
                                                  return false;
                                              }
                                              if (item.type == GL_TARGET_RECORD) {
                                                  return recording.load();
                                              }
                                              return true;
                                          }));
}

bool GLRender::drawInputTexture(const uint8_t *data, int width, int height) {
    glUseProgram(glContext.inputProgram);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, glContext.inputTextureId);
    if (width != glContext.texWidth || height != glContext.texHeight) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0,
                     GL_RGB, GL_UNSIGNED_BYTE, nullptr);
        glContext.texWidth = width;
        glContext.texHeight = height;
    }
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, data);
    glUniform1i(glContext.inputTextureLoc, 0);
    glBindVertexArray(glContext.vao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);
    return true;
}

bool GLRender::drawDirect(const uint8_t *data, int width, int height, int64_t ptsNs) {
    for (auto it = outputSurfaces.begin(); it != outputSurfaces.end();) {
        if (!it->window) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        if (it->type == GL_TARGET_RECORD && !recording.load()) {
            ++it;
            continue;
        }
        if (it->surface == EGL_NO_SURFACE) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        if (!makeCurrent(it->surface)) {
            LOG_E("GLRender", "makeCurrent failed target=%s", it->id.c_str());
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        glViewport(0, 0, it->width, it->height);
        glClearColor(0.f, 0.f, 0.f, 1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (!drawInputTexture(data, width, height)) {
            return false;
        }
        if (it->type == GL_TARGET_RECORD && gPresentationTime && ptsNs > 0) {
            gPresentationTime(glContext.eglDisplay, it->surface, static_cast<EGLnsecsANDROID>(ptsNs));
        }
        if (eglSwapBuffers(glContext.eglDisplay, it->surface) == EGL_TRUE) {
            return true;
        }
        LOG_E("GLRender", "eglSwapBuffers failed target=%s err=%d",
              it->id.c_str(), eglGetError());
        destroyOutputSurface(*it, true);
        it = outputSurfaces.erase(it);
    }
    return false;
}

bool GLRender::drawInputToFbo(const uint8_t *data, int width, int height) {
    if (!makeCurrent(glContext.workSurface)) {
        return false;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, glContext.fbo);
    glViewport(0, 0, width, height);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
    bool ok = drawInputTexture(data, width, height);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return ok;
}

bool GLRender::drawFboToTargets(int64_t ptsNs) {
    bool hasDrawn = false;
    for (auto it = outputSurfaces.begin(); it != outputSurfaces.end();) {
        if (!it->window) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        if (it->type == GL_TARGET_RECORD && !recording.load()) {
            ++it;
            continue;
        }
        if (it->surface == EGL_NO_SURFACE) {
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        if (!makeCurrent(it->surface)) {
            LOG_E("GLRender", "makeCurrent failed target=%s", it->id.c_str());
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        glViewport(0, 0, it->width, it->height);
        glClearColor(0.f, 0.f, 0.f, 1.f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(glContext.outputProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, glContext.fboTextureId);
        glUniform1i(glContext.outputTextureLoc, 0);
        glBindVertexArray(glContext.vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);
        if (it->type == GL_TARGET_RECORD && gPresentationTime && ptsNs > 0) {
            gPresentationTime(glContext.eglDisplay, it->surface,
                              static_cast<EGLnsecsANDROID>(ptsNs));
        }
        if (!eglSwapBuffers(glContext.eglDisplay, it->surface)) {
            LOG_E("GLRender", "eglSwapBuffers failed target=%s err=%d",
                  it->id.c_str(), eglGetError());
            destroyOutputSurface(*it, true);
            it = outputSurfaces.erase(it);
            continue;
        }
        hasDrawn = true;
        ++it;
    }
    return hasDrawn && makeCurrent(glContext.workSurface);
}

void GLRender::releaseTargetWindow(OutputSurfaceTarget &target) {
    if (target.window) {
        ANativeWindow_release(target.window);
        target.window = nullptr;
    }
}

void GLRender::releaseOpenGLLocked() {
    if (glContext.eglDisplay != EGL_NO_DISPLAY && glContext.workSurface != EGL_NO_SURFACE) {
        makeCurrent(glContext.workSurface);
    }
    for (auto &target: outputSurfaces) {
        destroyOutputSurface(target, false);
    }
    destroyFbo();
    destroyInputTexture();
    destroyVertices();
    destroyPrograms();
    destroyEGL();
    glContext = {};
}

void GLRender::releaseLocked() {
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
    recording.store(false);
    glContext = {};
}

bool GLRender::makeCurrent(EGLSurface surface) const {
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
