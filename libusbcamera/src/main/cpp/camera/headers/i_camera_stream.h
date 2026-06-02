//
// Created on 2025/5/18.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_I_CAMERA_STREAM_H
#define UVCCAMERA_I_CAMERA_STREAM_H

#include "Log.h"
#include "camera_constants.h"
#include "img_util.h"
#include "opencv2/core/mat.hpp"
#include "string"
#include <cstdint>
#include <deque>
#include <mutex>
#include <thread>
#include <atomic>
#include <android/native_window.h>
#include <filesystem>
#include "gl_preview.h"

#define MAX_FRAME 2

typedef struct stream_frame {
    uint8_t *data;    // 数据
    int format;       // 类型，参考camera_constants.h
    uint32_t width;   // 数据宽度
    uint32_t height;  // 数据高度
    size_t data_size; // 数据长度
    int rotation;     // 方向
} stream_frame_t;

static inline void free_stream(stream_frame_t *frame) {
    if (frame) {
        if (frame->data) {
            free(frame->data);
            frame->data = nullptr;
        }
        free(frame);
    }
    frame = nullptr;
}

class ICameraStream {
public:
    virtual ~ICameraStream() = default;

    virtual bool startPreview() = 0;                                    // 关闭预览
    virtual bool stopPreview() = 0;                                     // 开启预览
    virtual bool setPreviewSize(int width, int height, int format) = 0; // 设置预览分辨率

    void releasePreviewFunc() {
        std::lock_guard<std::mutex> lock(previewFuncMutex);
        deleteGlobalRef(theVM, previewListener);
        theVM = nullptr;
        previewListener = nullptr;
        onFrameMethod = nullptr;
    };

    bool setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int format) {
        std::lock_guard<std::mutex> lock(previewFuncMutex);
        previewFormat = format;
        this->theVM = vm;
        if (env->IsSameObject(previewListener, listener)) {
            if (listener) {
                env->DeleteGlobalRef(listener);
            }
            return true;
        }
        onFrameMethod = nullptr;
        if (previewListener) {
            env->DeleteGlobalRef(previewListener);
        }
        previewListener = listener;
        if (!listener) {
            return true;
        }
        jclass frameClass = env->GetObjectClass(listener);
        if (frameClass) {
            //宽高
            onFrameMethod = env->GetMethodID(frameClass, "onFrame", "(Ljava/nio/ByteBuffer;II)V");
        }
        env->ExceptionClear();
        if (!onFrameMethod) {
            env->DeleteGlobalRef(listener);
            previewListener = nullptr;
            theVM = nullptr;
            return false;
        }
        return true;
    }

    /**
    * 添加surface目标
    * @param targetId 目标ID
    * @param window 窗口
    * @param surfaceType surface类型
    * @return 是否添加成功
    */
    bool addSurfaceTarget(std::string targetId, ANativeWindow *window, GLTargetType surfaceType) {
        if (!window || targetId.empty() || !mPreview) { // 检查是否可以添加
            return false;
        }
        return mPreview->addSurfaceTarget(targetId, window, surfaceType);
    }

    /**
       * 移除surface目标
       * @param targetId 目标ID
       * @return 是否移除成功
       */
    bool removeSurfaceTarget(std::string targetId) {
        if (targetId.empty() || !mPreview) {
            return false;
        }
        return mPreview->removeSurfaceTarget(targetId);
    }


    /**
     * 启动录制：仅标记状态与 PTS 起点，底层 captureThread 会在 drawFrame 时将帧时间戳带入 GL。
     */
    virtual bool startRecord() {
        if (mPreview && isRunningPreview()) {
            return mPreview->startRecord();
        }
        return false;
    }

    /**
     * 停止录制。
     */
    virtual bool stopRecord() {
        if (mPreview && isRunningPreview()) {
            mPreview->stopRecord();
            return true;
        }
        return false;
    }

    bool setDisplayOrientation(int orientation) {
        if (mPreview) {
            mPreview->setRotation(orientation);
            return true;
        }
        return false;
    }

    // 设置拍照镜像
    bool setJpegMirrorState(bool isMirror) {
        if (mPreview) {
            mPreview->setMirror(isMirror);
            return true;
        }
        return false;
    }

    int getDisplayOrientation() const {
        if (mPreview) {
            return mPreview->getRotation();
        }
        return 0;
    }

    int getJpegMirrorState() const {
        if (mPreview) {
            return mPreview->getMirrorState();
        }
        return 0;
    }

    std::string getCurrentPreviewSize() const {
        return std::to_string(previewWidth) + ":" + std::to_string(previewHeight);
    }

    virtual bool isRunningPreview() = 0;

    std::vector<uint8_t> takePicture() {
        if (!isRunningPreview()) { // 如果没有父文件夹，且没有开始预览，则返回空
            return {};
        }
        // 1. 读取流
        stream_frame_t *frame = waitPictureFrame();
        if (!frame) {
            return {};
        }
        auto isMirror = mPreview != nullptr && mPreview->getMirrorState();
        auto result = ImgUtils::bgr2Mjpeg(frame->data, frame->width, frame->height, frame->rotation,
                                          isMirror);
        // 2. 释放资源
        // 2. 释放资源
        free_stream(frame);
        return result;
    };
protected:
    JavaVM *theVM = nullptr; //回调对应全局应该保存的东西
    jobject previewListener = nullptr; //回调的对象
    jmethodID onFrameMethod = nullptr; //回调的方法
    std::mutex previewFuncMutex; // 预览回调锁


    static void deleteGlobalRef(JavaVM *vm, jobject listener) {
        if (!vm || !listener) {
            return;
        }
        JNIEnv *env = nullptr;
        if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(listener);
        } else if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            env->DeleteGlobalRef(listener);
            vm->DetachCurrentThread();
        }
    }

    GLPreview *mPreview = nullptr;

    int previewWidth = 640;
    int previewHeight = 480;
    int previewFormat = PREVIEW_FORMAT_BGR; // 预览宽高,预览类型
    int previewFps = 30;                    // 预览的fps

    std::mutex pictureMutex; // 拍照使用的锁对象
    std::condition_variable pictureCond;
    std::atomic<bool> mIsPictureRunning{false}; // 当前是否拍照状态
    stream_frame *pictureFrame = nullptr;       // 拍照的数据

    // 复制一份frame
    static stream_frame_t *allocate_stream_frame(stream_frame_t *inFrame) {
        if (!inFrame || !inFrame->data || inFrame->data_size <= 0) {
            return nullptr;
        }
        stream_frame *outFrame = (stream_frame *) malloc(sizeof(*outFrame));
        if (!outFrame) {
            return nullptr;
        }
        outFrame->width = inFrame->width;
        outFrame->height = inFrame->height;
        outFrame->format = inFrame->format;
        outFrame->rotation = inFrame->rotation;
        outFrame->data_size = 0;
        outFrame->data = nullptr;
        return outFrame;
    };

    // 数据转为bgr格式
    static stream_frame_t *any2Bgr(stream_frame_t *inFrame) {
        stream_frame *outFrame = allocate_stream_frame(inFrame);
        if (!outFrame) {
            return nullptr;
        }
        outFrame->format = PREVIEW_FORMAT_BGR;
        cv::Mat outImg = ImgUtils::any2Bgr(inFrame->data, inFrame->data_size, inFrame->width,
                                           inFrame->height, inFrame->format);
        if (outImg.empty()) {
            free_stream(outFrame);
            return nullptr;
        }
        size_t len = outImg.total() * outImg.elemSize();
        outFrame->data = (uint8_t *) malloc(len);
        outFrame->data_size = len;
        std::memcpy(outFrame->data, outImg.data, len);
        // 因为要保证内存数据有效，这里必须进行一次内存复制
        return outFrame;
    };

    // 格式化输出的类型
    static stream_frame_t *format(stream_frame_t *inFrame, int outFormat) {
        stream_frame *outFrame = allocate_stream_frame(inFrame);
        if (!outFrame) {
            return nullptr;
        }
        outFrame->format = outFormat;
        std::vector<uint8_t> outImg = ImgUtils::format(inFrame->data, inFrame->width,
                                                       inFrame->height, outFormat);
        if (outImg.empty()) {
            free_stream(outFrame);
            return nullptr;
        }
        outFrame->data_size = outImg.size();
        outFrame->data = (uint8_t *) malloc(outFrame->data_size);
        std::memcpy(outFrame->data, outImg.data(), outFrame->data_size);
        return outFrame;
    };

    static stream_frame_t *allocate_bgr_stream_frame(const cv::Mat &inImg, int rotation) {
        if (inImg.empty() || !inImg.data) {
            return nullptr;
        }
        stream_frame *outFrame = (stream_frame *) malloc(sizeof(*outFrame));
        if (!outFrame) {
            return nullptr;
        }
        outFrame->width = inImg.cols;
        outFrame->height = inImg.rows;
        outFrame->format = PREVIEW_FORMAT_BGR;
        outFrame->rotation = rotation;
        outFrame->data_size = inImg.total() * inImg.elemSize();
        outFrame->data = (uint8_t *) malloc(outFrame->data_size);
        if (!outFrame->data) {
            free_stream(outFrame);
            return nullptr;
        }
        std::memcpy(outFrame->data, inImg.data, outFrame->data_size);
        return outFrame;
    }

    bool hasPreviewDataListener() {
        std::lock_guard<std::mutex> lock(previewFuncMutex);
        return previewListener && onFrameMethod;
    }

    void putPictureFrame(stream_frame_t *inFrame) {
        std::lock_guard<std::mutex> lock(pictureMutex);
        if (mIsPictureRunning.load() && !pictureFrame && inFrame && inFrame->data &&
            inFrame->data_size > 0) {
            stream_frame *outFrame = allocate_stream_frame(inFrame);
            if (outFrame) {
                outFrame->data_size = inFrame->data_size;
                outFrame->data = (uint8_t *) malloc(outFrame->data_size);
                if (outFrame->data) {
                    std::memcpy(outFrame->data, inFrame->data, outFrame->data_size);
                    pictureFrame = outFrame;
                } else {
                    free_stream(outFrame);
                }
            }
        }
        pictureCond.notify_one();
    }

    // 发送一帧图像到拍照的回调
    void putPictureFrame(const cv::Mat &inImg) {
        std::lock_guard<std::mutex> lock(pictureMutex);
        if (mIsPictureRunning.load() && !pictureFrame && !inImg.empty() && inImg.data) {
            stream_frame *outFrame = allocate_bgr_stream_frame(
                    inImg, mPreview ? mPreview->getRotation() : 0);
            if (outFrame && outFrame->data) {
                pictureFrame = outFrame;
            }
        }
        pictureCond.notify_one();
    }

    stream_frame_t *waitPictureFrame() {
        stream_frame_t *frame = nullptr;
        {
            std::unique_lock<std::mutex> lock(pictureMutex);
            mIsPictureRunning.store(true);
            pictureCond.wait_for(lock, std::chrono::seconds(3), [this] { return pictureFrame; });
            mIsPictureRunning.store(false);
            frame = pictureFrame;
            pictureFrame = nullptr;
        }
        return frame;
    }

    void clearPictureFrame() {
        std::lock_guard<std::mutex> lock(pictureMutex);
        mIsPictureRunning.store(false);
        if (pictureFrame) {
            free_stream(pictureFrame);
            pictureFrame = nullptr;
        }
    }
};

#endif // UVCCAMERA_I_CAMERA_STREAM_H
