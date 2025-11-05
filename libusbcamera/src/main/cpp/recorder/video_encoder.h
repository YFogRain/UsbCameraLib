//
// Created by MI T on 2025/6/16.
//

#ifndef USBCAMERALIB_VIDEO_ENCODER_H
#define USBCAMERALIB_VIDEO_ENCODER_H

#include <cstddef>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include "opencv2/core/types.hpp"
#include "camera_constants.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/time.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

enum recorder_format {
    mp4,  //.mp4
    avi,   //.avi
    mkv,   //.mkv
};

typedef struct recorder_frame {
    uint8_t *data;    // 数据
    int format;       // 类型，参考camera_constants.h
    uint32_t width;   // 数据宽度
    uint32_t height;  // 数据高度
    size_t data_size; // 数据长度
    int rotation;     // 旋转方向
    int64_t timestamp; // 时间戳
} recorder_frame_t;

static inline void free_frame(recorder_frame_t *frame) {
    if (frame) {
        if (frame->data_size > 0 && frame->data) {
            free(frame->data);
            frame->data = nullptr;
        }
        free(frame);
        frame = nullptr;
    }
}

// 视频编码器
class VideoEncoder {

public:
    VideoEncoder(const std::string filePath, int width, int height, int fps, int rotation);

    ~VideoEncoder();

    bool prepare(AVFormatContext *avFormatContext, recorder_format format);

    bool start();

    bool stop();

    bool isRecording() const {
        return mIsRecordRunning.load();
    }

    void putFrame(recorder_frame_t *frame);

    bool checkFrames(uint32_t w, uint32_t h) {
        return (w == frameWidth && h == frameHeight) || (w == frameHeight && h == frameWidth);
    }

private:
    std::string mFilePath;
    std::atomic<bool> mIsRecordRunning; // 当前录制运行的状态
    int mRotation;                           // 当前的旋转方向
    uint32_t frameWidth, frameHeight;
    int mFrameFps;
    std::mutex recordMutex;                  // 捕获线程的互斥锁
    std::thread recordThread;                // 捕获预览流，并且绘制到页面的线程
    std::condition_variable recordCond;      // 等待专用的条件变量
    std::deque<recorder_frame_t *> recordFrames; // 检测的缓存数据

    AVFormatContext *formatContext = nullptr; // 输出格式上下文
    AVCodecContext *videoCodecCtx = nullptr; // 视频编码上下文
    AVStream *videoStream = nullptr; // 视频流
    SwsContext *swsContext = nullptr; // 视频像素转换上下文

    void thread_func_record();               // 录制专用的线程

    recorder_frame_t *waitRecordFrame();       // 等待获取录制流

    void clearRecordFrames(); // 清空缓存数据

    AVCodecID select_video_codec_id_by_format(const recorder_format &recorderFormat);

    void writeFrame(recorder_frame_t *frame);

    bool encodeFrame(AVFrame *frame);

    cv::Size getRotatedSize(int width, int height, int transform) {
        switch (transform) {
            case TRANSFORM_ROTATE_90:
            case TRANSFORM_ROTATE_270:
            case TRANSFORM_FLIP_H_ROTATE_90:
            case TRANSFORM_FLIP_H_ROTATE_270:
            case TRANSFORM_FLIP_V_ROTATE_90:
            case TRANSFORM_FLIP_V_ROTATE_270:
                return cv::Size(height, width); // 宽高对调
            default:
                return cv::Size(width, height); // 保持不变
        }
    }
};


#endif //USBCAMERALIB_VIDEO_ENCODER_H
