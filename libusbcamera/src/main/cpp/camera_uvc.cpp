#include <jni.h>
#include <utility> // for std::pair
#include <android/native_window_jni.h>
#include "Log.h"
#include "camera_factory_helper.h"
#include <iostream>
#include "vector"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_debuggable(JNIEnv *env, jclass clazz, jint status) {
    DEBUG_ENABLE = status;
    return true;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeOpen(JNIEnv *env, jclass clazz, jint fd,
        jint bus_num, jint dev_address) {
    auto result = CameraFactoryHelper::openCamera(fd, bus_num, dev_address);
    if (result) {
        return reinterpret_cast<jlong>(result);
    }
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeOpenVideo(JNIEnv *env, jclass clazz,
        jstring video_path) {
    if (!video_path)return 0L;
    const char *path = env->GetStringUTFChars(video_path, nullptr);
    if (!path)return 0L;
    auto result = CameraFactoryHelper::openCamera(path);
    env->ReleaseStringUTFChars(video_path, path);
    if (result) {
        return reinterpret_cast<jlong>(result);
    }
    return 0L;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeClose(JNIEnv *env, jclass clazz, jlong native_id) {
    return CameraFactoryHelper::closeCamera(native_id);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeStartPreview(JNIEnv *env, jclass clazz,
        jlong nativeId) {
    auto *camera = reinterpret_cast<ICameraDevice *>(nativeId);
    if (camera) {
        return camera->getUserStream()->startPreview();
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeStopPreview(JNIEnv *env, jclass clazz,
        jlong nativeId) {
    auto *camera = reinterpret_cast<ICameraDevice *>(nativeId);
    if (camera) {
        return camera->getUserStream()->stopPreview();
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeSetDisplaySurface(JNIEnv *env, jclass clazz,
        jlong nativeId,
        jobject jSurface) {
    auto *camera = reinterpret_cast<ICameraDevice *>(nativeId);
    if (camera) {
        ANativeWindow *preview_window = jSurface ? ANativeWindow_fromSurface(env, jSurface)
                                                 : nullptr;
        return camera->getUserStream()->setDisplaySurface(preview_window);
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeSetPreviewSize(JNIEnv *env, jclass clazz,
        jlong nativeId, jint width,
        jint height, jint format) {
    auto *camera = reinterpret_cast<ICameraDevice *>(nativeId);
    if (camera) {
        return camera->getUserStream()->setPreviewSize(width, height, format);
    }
    return false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_setPreviewListener(JNIEnv *env, jclass clazz,
        jlong nativeId, jobject listener,
        jint mode) {
    auto *camera = reinterpret_cast<ICameraDevice *>(nativeId);
    if (camera) {
        JavaVM *vm;
        env->GetJavaVM(&vm);
        jobject frameResultObj = nullptr;
        if (listener) {
            frameResultObj = env->NewGlobalRef(listener);
        }
        return camera->getUserStream()->setPreviewDataListener(vm, env, frameResultObj, mode);
    }
    return false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetSupportedParameters(JNIEnv *env, jclass clazz,
        jlong native_id, jint type) {
    ICameraDevice *camera = reinterpret_cast<ICameraDevice *>(native_id);
    if (!camera)return nullptr;
    auto result = camera->getSupportParameters(type);
    if (std::holds_alternative<std::monostate>(result)) {
        return nullptr;
    } else if (std::holds_alternative<int>(result)) {
        int value = std::get<int>(result);
        std::string str = std::to_string(value);
        return env->NewStringUTF(str.c_str());
    } else if (std::holds_alternative<std::string>(result)) {
        auto value = std::get<std::string>(result);
        LOG_D("当前获取到的值为:%s", value.c_str());
        return env->NewStringUTF(value.c_str());
    } else if (std::holds_alternative<std::pair<int, int>>(result)) {
        auto value = std::get<std::pair<int, int>>(result);
        auto resultStr = std::to_string(value.first) + ":" + std::to_string(value.second);
        return env->NewStringUTF(resultStr.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeGetParameterValue(JNIEnv *env, jclass clazz,
        jlong native_id, jint type) {
    ICameraDevice *camera = reinterpret_cast<ICameraDevice *>(native_id);
    if (!camera)return nullptr;
    auto result = camera->getParameter(type);
    if (std::holds_alternative<std::monostate>(result)) {
        return nullptr;
    } else if (std::holds_alternative<int>(result)) {
        return env->NewStringUTF(std::to_string(std::get<int>(result)).c_str());
    } else if (std::holds_alternative<std::string>(result)) {
        auto value = std::get<std::string>(result);
        LOG_D("当前获取到的值为:%s", value.c_str());
        return env->NewStringUTF(value.c_str());
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeSetParameterValue(JNIEnv *env, jclass clazz,
        jlong native_id, jint type,
        jint value) {
    ICameraDevice *camera = reinterpret_cast<ICameraDevice *>(native_id);
    if (!camera)return false;
    return camera->setParameter(type, value);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeLoadV4L2Devices(JNIEnv *env, jclass clazz) {
    std::vector<std::string> vector = CameraFactoryHelper::loadV4L2Devices();
    if (vector.empty()) {
        return nullptr;
    }
    //创建指定大小的集合
    jobjectArray deviceArray = env->NewObjectArray(vector.size(),
                                                   env->FindClass("java/lang/String"), nullptr);
    // 遍历 C++ 的 vector，转换成 Java 字符串并填充到数组中
    for (size_t i = 0; i < vector.size(); ++i) {
        jstring javaString = env->NewStringUTF(vector[i].c_str());
        env->SetObjectArrayElement(deviceArray, i, javaString);
        env->DeleteLocalRef(javaString);  // 删除局部引用以避免内存泄漏
    }
    return deviceArray;  // 返回转换后的 Java 字符串数组
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_nativeTakePicture(JNIEnv *env, jclass clazz,
                                                            jlong native_id) {
    ICameraDevice *camera = reinterpret_cast<ICameraDevice *>(native_id);
    if (!camera)return nullptr;
    auto pictureBytes = camera->getUserStream()->takePicture();
    if (pictureBytes.empty())return nullptr;
    auto size = pictureBytes.size();
    jbyteArray byteArray = env->NewByteArray(size);
    if (!byteArray) {
        return nullptr;
    }
    env->SetByteArrayRegion(byteArray, 0, size, reinterpret_cast<jbyte *>(pictureBytes.data()));
    return byteArray;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_rain_uvc_utils_CameraNativeUtils_setButtonListener(JNIEnv *env, jclass clazz,
                                                            jlong nativeId, jobject listener) {
    LOG_D("开始设置button按钮~~~");
    auto *camera = reinterpret_cast<ICameraDevice *>(nativeId);
    if (camera) {
        JavaVM *vm;
        env->GetJavaVM(&vm);
        return camera->setButtonListener(vm, env, listener);
    }
    return false;
}