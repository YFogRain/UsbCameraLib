//
// Created by MI T on 2025/6/16.
//

#include "media_recorder_codes.h"
#include "Log.h"
#include <filesystem>
#include <iomanip>
#include <sstream>


MediaRecorderCodes::MediaRecorderCodes() {}

MediaRecorderCodes::~MediaRecorderCodes() {
    if (videoEncoder != nullptr) {
        videoEncoder->stop();
        delete videoEncoder;
        videoEncoder = nullptr;
    }
    //暂时不支持录制音频
    if (audioEncoder != nullptr) {
//    audioEncoder->stop();
        delete audioEncoder;
        audioEncoder = nullptr;
    }
}


bool MediaRecorderCodes::prepare(uint32_t videoWidth, uint32_t videoHeight, int videoFps, int rotation, const std::string &parentPath, const std::string &filename, const recorder_format &format, bool enableAudio) {
    if (videoEncoder != nullptr && videoEncoder->isRecording()) { //当前正在录制
        return false;
    }
    std::string filePath = initRecordPath(parentPath, filename, format);
    if (filePath.empty()) { //初始化文件路径失败了
        return false;
    }
    int initFormat = avformat_alloc_output_context2(&formatContext, nullptr, nullptr, filePath.c_str());

    if (initFormat < 0 || !formatContext) {
        char errbuf[256];
        av_strerror(initFormat, errbuf, sizeof(errbuf));
        LOG_E("初始化ffmpeg编码器失败: %s", errbuf);
        formatContext = nullptr;
        return false;
    }
    //创建视频编码器
    videoEncoder = new VideoEncoder(filePath, videoWidth, videoHeight, videoFps, rotation);
    if (!videoEncoder->prepare(formatContext,format)) {
        delete videoEncoder;
        return false;
    }
    if (enableAudio) {
        //创建音频编码器
        audioEncoder = new AudioEncoder(filePath);
        if (!audioEncoder->prepare()) {
            LOG_E("音频编码器初始化失败");
            delete audioEncoder;
            audioEncoder = nullptr;
        }
    }
    mRecorderPath = filePath;
    return true;
}


bool MediaRecorderCodes::start() {
    if (videoEncoder != nullptr && !videoEncoder->isRecording()) {
        if (!videoEncoder->start()) { //未打开视频录制
            videoEncoder->stop();
            delete videoEncoder; //录制失败
            videoEncoder = nullptr;
            return false;
        }
    }
    if (audioEncoder != nullptr && !audioEncoder->isRecording()) {
        if (!audioEncoder->start()) { //未打开音频录制
            audioEncoder->stop();
            delete audioEncoder; //录制失败
            audioEncoder = nullptr;
        }
    }
    return true;
}

bool MediaRecorderCodes::stop() {
    if (videoEncoder != nullptr && videoEncoder->isRecording()) {
        videoEncoder->stop();
        delete videoEncoder; //录制失败
        videoEncoder = nullptr;
    }
    if (audioEncoder != nullptr && audioEncoder->isRecording()) {
        audioEncoder->stop();
        delete audioEncoder; //录制失败
        audioEncoder = nullptr;
    }
    if (formatContext) {
        avformat_free_context(formatContext);
        formatContext = nullptr;
    }
    return true;
}

void MediaRecorderCodes::putFrame(uint8_t *frame, uint32_t width, uint32_t height, int format, size_t data_size,int64_t timestamp) {
    if (videoEncoder == nullptr || !videoEncoder->isRecording()) {
        return;
    }
    recorder_frame_t *outFrame = (recorder_frame_t *) malloc(sizeof(*outFrame));
    if (!outFrame) {
        return;
    }
    outFrame->width = width;
    outFrame->height = height;
    outFrame->format = format;
    outFrame->data_size = data_size;
    outFrame->data = (uint8_t *) malloc(data_size);
    outFrame->timestamp = timestamp;
    if (!outFrame->data) {
        free(outFrame);
        return;
    }
    //  复制数据
    memcpy(outFrame->data, frame, data_size);
    videoEncoder->putFrame(outFrame);
}


const std::string MediaRecorderCodes::initRecordPath(const std::string &parentPath, const std::string &filename, const recorder_format &format) {
    if (parentPath.empty()) {
        return {}; // 未设置保存的路径
    }
    try {
        std::filesystem::create_directories(parentPath.c_str());
    } catch (const std::exception &e) {
        return {};
    }
    // 设置文件名
    std::string saveFileName = filename;
    if (saveFileName.empty()) {
        saveFileName = "video_" + formatTime("%Y%m%d_%H_%M%S%f", getCurrentTime());
    };

    std::string pix;
    switch (format) {
        case avi:
            pix = ".avi";
            break;
        case mkv:
            pix = ".mkv";
            break;
        default:
            pix = ".mp4";
            break;
    }
    saveFileName = saveFileName + pix;
    if (parentPath.back() == '/') {
        return parentPath + saveFileName;
    } else {
        return parentPath + "/" + saveFileName;
    }
}

long MediaRecorderCodes::getCurrentTime() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch())
            .count();
}

std::string MediaRecorderCodes::formatTime(const std::string &pattern, long timeMillis) {
    // 转换为秒和毫秒
    std::chrono::milliseconds ms_since_epoch(timeMillis);
    std::chrono::seconds sec_since_epoch = std::chrono::duration_cast<std::chrono::seconds>(ms_since_epoch);
    int millis = static_cast<int>(ms_since_epoch.count() % 1000);

    std::time_t time_sec = sec_since_epoch.count();
    std::tm tm_local;
    localtime_r(&time_sec, &tm_local); // 线程安全版本
    std::ostringstream oss;
    oss << std::put_time(&tm_local, pattern.c_str()); // 根据 pattern 格式化到秒
    // 判断格式字符串中是否包含需要毫秒的部分
    if (pattern.find("%f") != std::string::npos) {                 // 如果格式包含 %f，代表需要毫秒
        oss << '.' << std::setw(3) << std::setfill('0') << millis; // 追加毫秒部分
    }
    return oss.str();
}