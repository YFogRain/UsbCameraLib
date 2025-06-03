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
#include "video_record.h"
#include <filesystem>

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
}

class ICameraStream {
public:
    virtual ~ICameraStream() = default;

    virtual bool startPreview() = 0;                                    // 关闭预览
    virtual bool stopPreview() = 0;                                     // 开启预览
    virtual bool setPreviewSize(int width, int height, int format) = 0; // 设置预览分辨率

    void releaseWindows() {
        std::lock_guard<std::mutex> lock(surfaceMutex);
        if (mPreviewWindow) {
            ANativeWindow_release(mPreviewWindow);
            mPreviewWindow = nullptr;
        }
    };

    void releasePreviewFunc() {
        std::lock_guard<std::mutex> lock(previewFuncMutex);
        if (previewListener && theVM) {
            JNIEnv *env = nullptr;
            if (theVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
                // 在析构函数中删除全局引用
                env->DeleteGlobalRef(previewListener);  // 删除全局引用
            }
        }
        theVM = nullptr;
        previewListener = nullptr;
        onFrameMethod = nullptr;
    };

    bool setPreviewDataListener(JavaVM *vm, JNIEnv *env, jobject listener, int format) {
        std::lock_guard<std::mutex> lock(previewFuncMutex);
        previewFormat = format;
        this->theVM = vm;
        if (env->IsSameObject(previewListener, listener)) {
            return true;
        }
        onFrameMethod = nullptr;
        if (previewListener) {
            env->DeleteGlobalRef(previewListener);
        }
        previewListener = listener;
        if (!listener) {
            LOG_E("监听设置失败，listener为null");
            return false;
        }
        jclass frameClass = env->GetObjectClass(listener);
        if (frameClass) {
            //宽高
            onFrameMethod = env->GetMethodID(frameClass, "onFrame", "(IILjava/nio/ByteBuffer;)V");
        }
        env->ExceptionClear();
        if (!onFrameMethod) {
            env->DeleteGlobalRef(listener);
            previewListener = nullptr;
            LOG_E("设置监听失败");
            return false;
        }
        return true;
    }

    bool setDisplaySurface(ANativeWindow *preview_window) {
        std::lock_guard<std::mutex> lock(surfaceMutex);
        if (mPreviewWindow != preview_window) {
            if (mPreviewWindow) {
                ANativeWindow_release(mPreviewWindow);
            }
            mPreviewWindow = preview_window;
            if (LIKELY(mPreviewWindow)) {
                ANativeWindow_setBuffersGeometry(mPreviewWindow, previewWidth, previewHeight, UVC_FORMAT_FRAME_WINDOW);
            }
            ImgUtils::setDisplayTransformState(mPreviewWindow, mDisplayTransformState);
        }
        return true;
    };

    bool setDisplayTransform(int state) {
        LOG_D("当前的设备方向:触发设置方向");
        std::lock_guard<std::mutex> lock(surfaceMutex);
        return ImgUtils::setDisplayTransformState(mPreviewWindow, mDisplayTransformState);;
    }

    int getDisplayTransformState() { return mDisplayTransformState; }

    std::string getCurrentPreviewSize() {
        return std::to_string(previewWidth) + ":" + std::to_string(previewHeight) + ":" + std::to_string(previewFormat);
    }

    virtual bool isRunningPreview() = 0;

    bool startRecord(const std::string &fileName) {
        bool isPrepare =
                mVideoRecord->prepare(previewWidth, previewHeight, mDisplayTransformState, previewFps, fileName);
        if (!isPrepare) {
            return false;
        }
        return mVideoRecord->startRecord();
    };

    void stopRecord() { mVideoRecord->stopRecord(); };

    void setRecordParentPath(const std::string &parentPath) { mVideoRecord->setParentPath(parentPath); };

    std::string getRecordPath() { return mVideoRecord->getRecordPath(); };

    void setRecordFormat(int format) {
        record_format videoFormat;
        switch (format) {
            case RECORD_FORMAT_MP4V:
                videoFormat = mp4v;
                break;
            case RECORD_FORMAT_AVC:
                videoFormat = avc;
                break;
            case RECORD_FORMAT_VID:
                videoFormat = vid;
                break;
            case RECORD_FORMAT_DIVX:
                videoFormat = divx;
                break;
            default:
                videoFormat = mjpeg;
                break;
        }
        mVideoRecord->setRecordFormat(videoFormat);
    };

    std::string takePicture(const std::string &parentPath, const std::string &fileName) {
        if (parentPath.empty() || !isRunningPreview()) { // 如果没有父文件夹，且没有开始预览，则返回空
            return std::string{};
        }
        try { // 创建父类文件夹
            std::filesystem::create_directories(parentPath);
        } catch (const std::exception &e) {
            return std::string{};
        }
        std::string saveFileName = fileName;
        if (saveFileName.empty()) {
            saveFileName = "img_" + VideoRecord::formatTime("%Y%m%d_%H_%M%S%f", VideoRecord::getCurrentTime());
        };
        saveFileName = saveFileName + ".jpeg";

        std::string fullPath;
        if (parentPath.back() == '/') {
            fullPath = parentPath + saveFileName;
        } else {
            fullPath = parentPath + "/" + saveFileName;
        }
        // 1. 读取流
        stream_frame_t *frame = waitPictureFrame();
        if (!frame) {
            return nullptr;
        }
        bool isSaveSuccess = ImgUtils::writeMjpeg(frame->data, frame->width, frame->height, frame->rotation, fullPath);
        // 2. 释放资源
        free_stream(frame);
        if (isSaveSuccess) {
            return fullPath;
        }
        return nullptr;
    };
protected:
    JavaVM *theVM = nullptr; //回调对应全局应该保存的东西
    jobject previewListener = nullptr; //回调的对象
    jmethodID onFrameMethod = nullptr; //回调的方法
    VideoRecord *mVideoRecord = nullptr;       // 录制的对象
    int mDisplayTransformState;                // 预览方向

    std::mutex surfaceMutex;     // 窗口操作锁，单线程操作当前指定窗口
    std::mutex previewFuncMutex; // 预览回调锁

    std::string mSurfaceId;
    int previewWidth = 640;
    int previewHeight = 480;
    int previewFormat = PREVIEW_FORMAT_BGR; // 预览宽高,预览类型
    int previewFps = 30;                    // 预览的fps

    std::mutex pictureMutex; // 拍照使用的锁对象
    std::condition_variable pictureCond;
    std::atomic<bool> mIsPictureRunning{false}; // 当前是否拍照状态
    stream_frame *pictureFrame = nullptr;       // 拍照的数据

    void drawFrame(uint8_t *data, size_t dataSize, int w, int h) {
        std::lock_guard<std::mutex> lock(surfaceMutex);
        if (!mPreviewWindow || !data || dataSize <= 0 || w == 0 || h == 0) {
            return;
        }
        ANativeWindow_Buffer buffer;
        // 锁定缓冲区以获取可以写入的内存区域
        if (ANativeWindow_lock(mPreviewWindow, &buffer, nullptr) == 0) {
            auto *dst = (uint8_t *) buffer.bits;
            // 将RGB数据复制到RGBA图像，并设置alpha值为255
            for (int i = 0, j = 0; i < w * h; ++i, j += 4) {
                dst[j] = data[i * 3 + 2];     // R
                dst[j + 1] = data[i * 3 + 1]; // G
                dst[j + 2] = data[i * 3]; // B
                dst[j + 3] = 0xFF;                 // A
            }
            // 解锁缓冲区
            ANativeWindow_unlockAndPost(mPreviewWindow);
        }
    };

    void changeWindowSize(int width, int height) {
        std::lock_guard<std::mutex> lock(surfaceMutex);
        if (LIKELY(mPreviewWindow)) {
            ANativeWindow_setBuffersGeometry(mPreviewWindow, width, height, UVC_FORMAT_FRAME_WINDOW);
        }
    };

    void putRecordFrames(stream_frame_t *inFrame) {
        if (!mVideoRecord || !mVideoRecord->isRecording()) {
            return;
        }
        if (!inFrame || !mVideoRecord->checkFrames(inFrame->width, inFrame->height)) {
            return;
        }
        record_frame_t *outFrame = (record_frame_t *) malloc(sizeof(*outFrame));
        if (!outFrame) {
            return;
        }
        outFrame->width = inFrame->width;
        outFrame->height = inFrame->height;
        outFrame->rotation = inFrame->rotation;
        outFrame->format = inFrame->format;
        outFrame->data_size = inFrame->data_size;
        outFrame->data = (uint8_t *) malloc(inFrame->data_size);
        if (!outFrame->data) {
            free_record_frame(outFrame);
            return;
        }
        //  复制数据
        memcpy(outFrame->data, inFrame->data, inFrame->data_size);
        mVideoRecord->putFrame(outFrame);
    }

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

    static stream_frame_t *any2Bgr(stream_frame_t *inFrame) {
        stream_frame *outFrame = allocate_stream_frame(inFrame);
        if (!outFrame) {
            return nullptr;
        }
        outFrame->format = PREVIEW_FORMAT_BGR;
        cv::Mat outImg =
                ImgUtils::any2Bgr(inFrame->data, inFrame->data_size, inFrame->width, inFrame->height, inFrame->format);
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

    static stream_frame_t *format(stream_frame_t *inFrame, int outFormat) {
        stream_frame *outFrame = allocate_stream_frame(inFrame);
        if (!outFrame) {
            return nullptr;
        }
        outFrame->format = outFormat;
        cv::Mat outImg = ImgUtils::format(inFrame->data, inFrame->width, inFrame->height, outFormat);
        if (outImg.empty()) {
            free_stream(outFrame);
            return nullptr;
        }
        outFrame->data_size = outImg.total() * outImg.elemSize();
        outFrame->data = (uint8_t *) malloc(outFrame->data_size);
        std::memcpy(outFrame->data, outImg.data, outFrame->data_size);
        return outFrame;
    };

    void putPictureFrame(stream_frame_t *inFrame) {
        std::lock_guard<std::mutex> lock(pictureMutex);
        if (mIsPictureRunning.load() && !pictureFrame) {
            stream_frame *outFrame = allocate_stream_frame(inFrame);
            if (outFrame) {
                outFrame->data_size = inFrame->data_size;
                outFrame->data = (uint8_t *) malloc(outFrame->data_size);
                std::memcpy(outFrame->data, inFrame->data, inFrame->data_size);
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

private:
    ANativeWindow *mPreviewWindow = nullptr; // 预览的窗口
};

#endif // UVCCAMERA_I_CAMERA_STREAM_H
