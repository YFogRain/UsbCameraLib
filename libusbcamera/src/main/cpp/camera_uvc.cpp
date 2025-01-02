#include <jni.h>
#include "camera/headers/UvcCamera.h"
#include <utility> // for std::pair
#include "Log.h"

//
// Created by MI T on 2024/8/1.
//

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_debuggable(JNIEnv *env, jclass clazz, jint status) {
    DEBUG_ENABLE = status;
    return true;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeCreate(JNIEnv *env, jclass clazz) {
    auto *camera = new UvcCamera();
    return reinterpret_cast<jlong>(camera);
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeDestroy(JNIEnv *env, jclass clazz, jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        SAFE_DELETE(camera)
    }
    return true;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeConnect(JNIEnv *env, jclass clazz, jlong nativeId,
                                                        jint fd) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->connect(fd);
        return ret == UVC_SUCCESS;
    }
    return false;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeConnectFd(JNIEnv *env, jclass clazz,
                                                          jlong nativeId, jint fd, jint bus_num,
                                                          jint dev_address, jstring usb_fs) {
    const char *cUsbFs = env->GetStringUTFChars(usb_fs, JNI_FALSE);
    if (!cUsbFs) {
        return false;
    }
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->connect(fd, bus_num, dev_address, cUsbFs);
        return ret == UVC_SUCCESS;
    }
    return false;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeDisConnect(JNIEnv *env, jclass clazz,
                                                           jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->disConnect();
        return ret == UVC_SUCCESS;
    }
    return false;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeStartPreview(JNIEnv *env, jclass clazz,
                                                             jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->startPreview();
        return ret == UVC_SUCCESS;
    }
    return false;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeStopPreview(JNIEnv *env, jclass clazz,
                                                            jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->stopPreview();
        return ret == UVC_SUCCESS;
    }
    return false;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeSetDisplaySurface(JNIEnv *env, jclass clazz,
                                                                  jlong nativeId,
                                                                  jobject jSurface) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        ANativeWindow *preview_window = jSurface ? ANativeWindow_fromSurface(env, jSurface)
                                                 : nullptr;
        int ret = camera->setPreviewDisplay(preview_window);
        return ret == UVC_SUCCESS;
    }
    return false;
}
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetPreviewSize(JNIEnv *env, jclass clazz,
                                                               jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        std::pair<int, int> pair = camera->getPreviewSize();
        int temp[3] = {pair.first, pair.second, camera->loadCurrentFormat()};
        jintArray pArray = env->NewIntArray(3);
        env->SetIntArrayRegion(pArray, 0, 3, temp);
        return pArray;
    }
    return nullptr;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeSetPreviewSize(JNIEnv *env, jclass clazz,
                                                               jlong nativeId, jint width,
                                                               jint height, jint format) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->setPreviewSize(width, height, format);
        return ret == UVC_SUCCESS;
    }
    return false;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetSupportAutoExposure(JNIEnv *env, jclass clazz,
                                                                       jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        int ret = camera->getSupportAutoExposure();
        return ret == UVC_SUCCESS;
    }
    return false;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetSupportPreviewSizes(JNIEnv *env, jclass clazz,
                                                                       jlong nativeId) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        char *str = camera->getSupportedPreviewSizes();
        if (LIKELY(str)) {
            jstring result = env->NewStringUTF(str);
            free(str);
            return result;
        }
    }
    return nullptr;
}
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetParameterRange(JNIEnv *env, jclass clazz,
                                                                  jlong nativeId, jint type) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        LOG_D("开始获取值");
        std::pair<int, int> pair = camera->getParameterRange(type);
        int min = pair.first;
        int max = pair.second;
        LOG_D("当前获取到的值:%d-%d", min, max);
        if (min == max) {
            return nullptr;
        }
        int temp[2] = {min, max};
        jintArray pArray = env->NewIntArray(2);
        env->SetIntArrayRegion(pArray, 0, 2, temp);
        return pArray;
    }
    return nullptr;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetIntValue(JNIEnv *env, jclass clazz,
                                                            jlong nativeId, jint type) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        return camera->getParameterIntValue(type);
    }
    return -999;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeSetIntValue(JNIEnv *env, jclass clazz,
                                                            jlong nativeId, jint type, jint value) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        return camera->setParameterIntValue(type, value);
    }
    return false;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_setPreviewListener(JNIEnv *env, jclass clazz,
                                                             jlong nativeId, jobject listener,
                                                             jint mode) {
    auto *camera = reinterpret_cast<UvcCamera *>(nativeId);
    if (camera) {
        JavaVM *vm;
        env->GetJavaVM(&vm);
        camera->setPreviewListener(vm, env, listener, mode);
    }
}