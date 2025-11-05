//
// Created by MI T on 2025/6/16.
//

#ifndef USBCAMERALIB_MEDIA_RECORDER_CODES_H
#define USBCAMERALIB_MEDIA_RECORDER_CODES_H

#include <string>
#include "mutex"
#include <deque>
#include <thread>
#include "video_encoder.h"
#include "audio_encoder.h"

// 录制工具类(使用ffmpeg录制)
class MediaRecorderCodes {
public:
    MediaRecorderCodes();

    ~MediaRecorderCodes();

    bool prepare(uint32_t videoWidth,             // 视频宽度
            uint32_t videoHeight,            // 视频高度
            int videoFps,                    // 视频帧率
            int rotation,      //视频方向
            const std::string &parentPath, // 父目录
            const std::string &filename, // 文件名
            const recorder_format &format, // 录制格式
            bool enableAudio);//准备录制，预加载视频和音频编码器
    bool start(); //开始录制

    void putFrame(uint8_t *frame, uint32_t width, uint32_t height, int format, size_t data_size, int64_t timestamp); //写入数据

    bool stop(); //停止录制

    std::string getRecordPath() {
        return mRecorderPath;
    }; // 获取录制的文件路径

    bool isRecording() const {
        if (videoEncoder == nullptr)return false;
        return videoEncoder->isRecording();
    }
    bool checkFrames(uint32_t w, uint32_t h) {
        if (videoEncoder == nullptr)return false;
        return  videoEncoder->checkFrames(w,h);
    }; // 检查数据合法性

    static long getCurrentTime(); // 获取当前时间的格式化字符串

    static std::string formatTime(const std::string &pattern, long time); // 格式化时间
private:
    VideoEncoder *videoEncoder = nullptr;
    AudioEncoder *audioEncoder = nullptr;

    AVFormatContext *formatContext = nullptr; // 封装格式上下文

    std::string mRecorderPath;

    const std::string initRecordPath(const std::string &parentPath, const std::string &filename, const recorder_format &format);

};


#endif //USBCAMERALIB_MEDIA_RECORDER_CODES_H
