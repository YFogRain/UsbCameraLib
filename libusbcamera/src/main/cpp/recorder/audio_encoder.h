//
// Created by MI T on 2025/6/16.
//

#ifndef USBCAMERALIB_AUDIO_ENCODER_H
#define USBCAMERALIB_AUDIO_ENCODER_H
#include "string"

// 音频编码器
class AudioEncoder {
public:
    AudioEncoder(const std::string filePath);

    ~AudioEncoder();

    bool prepare();

    bool start();

    bool stop();

    bool isRecording() const {
        return mIsRecordRunning.load();
    }

private:
    std::string mFilePath;
    std::atomic<bool> mIsRecordRunning;
};


#endif //USBCAMERALIB_AUDIO_ENCODER_H
