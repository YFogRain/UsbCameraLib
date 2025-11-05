//
// Created on 2025/5/20.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "video_record.h"
#include <filesystem>
#include <iomanip>
#include <sstream>
#include "Log.h"
#include "camera_constants.h"
#include "img_util.h"
#include "opencv2/videoio.hpp"
#include <opencv2/core.hpp>

std::string defaultParentPath = "";

void setDefaultParent(const std::string &path) { defaultParentPath = path; }


VideoRecord::VideoRecord()
        : mIsRecordRunning(false), frameWidth(0), frameHeight(0), format(mjpeg), recordFilePath(""),
          parentPath(defaultParentPath), mRotation(0) {}

VideoRecord::~VideoRecord() {}

long VideoRecord::getCurrentTime() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch())
            .count();
}

std::string VideoRecord::formatTime(const std::string &pattern, long timeMillis) {
    // 转换为秒和毫秒
    std::chrono::milliseconds ms_since_epoch(timeMillis);
    std::chrono::seconds sec_since_epoch = std::chrono::duration_cast<std::chrono::seconds>(
            ms_since_epoch);
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

void VideoRecord::setParentPath(const std::string &path) { this->parentPath = path; }

void VideoRecord::setRecordFormat(const record_format &format) { this->format = format; }

bool
VideoRecord::prepare(uint32_t w, uint32_t h, int rotation, int fps, const std::string &filename) {
    std::lock_guard<std::mutex> lock(recordMutex);
    if (w == 0 || h == 0 || parentPath.empty()) {
        return false;
    }
    if (mIsRecordRunning.load()) {
        return false; // 正在录制中，退出
    }
    this->mRotation = rotation;
    cv::Size size = getRotatedSize(w, h, rotation);
    frameWidth = size.width;
    frameHeight = size.height;
    // 初始化文件路径
    if (!initRecordPath(filename)) {
        return false;
    }
    LOG_D("使用帧率为: %d", fps);
    // 获取opencv的编码器
    int fourcc = initRecordFourcc();
    if (!videoWriter.open(this->recordFilePath, fourcc, fps, size, true)) {
        stopRecord();
        return false;
    };
    return true;
}

void VideoRecord::stopRecord() {
    if (mIsRecordRunning.load()) {
        {
            std::lock_guard<std::mutex> lock(recordMutex);
            mIsRecordRunning.store(false);
        }
        recordCond.notify_one();
        if (recordThread.joinable()) {
            recordThread.join();
        }
    }
    if (videoWriter.isOpened()) {
        videoWriter.release();
    }
    frameWidth = 0;
    frameHeight = 0;
    clearRecordFrames();
}

bool VideoRecord::startRecord() {
    if (!videoWriter.isOpened() || mIsRecordRunning.load()) {
        return false; // 预加载失败,或者正在录制中
    }
    mIsRecordRunning.store(true);
    recordThread = std::thread(&VideoRecord::thread_func_record, this);
    return true;
}

void VideoRecord::clearRecordFrames() {
    std::lock_guard<std::mutex> lock(recordMutex);
    if (!recordFrames.empty()) {
        for (record_frame_t *pFrame: recordFrames) { // 直接遍历，避免 size() 变化
            free_record_frame(pFrame);
        }
        recordFrames.clear();
    }
}

record_frame_t *VideoRecord::waitRecordFrame() {
    record_frame_t *frame = nullptr;
    {
        std::unique_lock<std::mutex> lock(recordMutex);
        recordCond.wait(lock, [this] { return !mIsRecordRunning.load() || !recordFrames.empty(); });
        if (mIsRecordRunning.load() && !recordFrames.empty()) {
            frame = recordFrames.front();
            recordFrames.pop_front();
        }
    }
    return frame;
}

void VideoRecord::thread_func_record() {
    // 循环读取数据
    while (mIsRecordRunning.load() && videoWriter.isOpened()) {
        record_frame_t *frame = waitRecordFrame();
        if (!frame) {
            continue;
        }
        if (!checkFrames(frame->width, frame->height) ||
            this->mRotation != frame->rotation) { // 如果宽高和定义的不相同
            free_record_frame(frame);
            continue;
        }
        // 将数据转为bgr格式。
        cv::Mat bgrImg = ImgUtils::any2Bgr(frame->data, frame->data_size, frame->width,
                                           frame->height, frame->format);
        if (bgrImg.empty()) {
            free_record_frame(frame);
            continue;
        }
        cv::Mat img = ImgUtils::rotation(bgrImg.data, bgrImg.cols, bgrImg.rows, frame->rotation);
        // 写入数据
        try {
            videoWriter.write(img);
        } catch (const cv::Exception &e) {
            LOG_E("OpenCV Exception: %s", e.what()); // 打印详细错误
        }
        free_record_frame(frame);
    }
}

void VideoRecord::putFrame(record_frame_t *frame) {
    {
        std::lock_guard<std::mutex> lock(recordMutex);
        if (mIsRecordRunning.load() && videoWriter.isOpened()) {
            recordFrames.push_back(frame);
            frame = nullptr;
        }
    }
    recordCond.notify_one();
    if (frame) {
        free_record_frame(frame);
    }
}


int VideoRecord::initRecordFourcc() {
    switch (format) {
        case mp4v:
            return cv::VideoWriter::fourcc('M', 'P', '4', 'V'); // MPEG-4
        case avc:
            return cv::VideoWriter::fourcc('H', '2', '6', '4'); // H.264 (需编解码支持)
        case vid:
            return cv::VideoWriter::fourcc('X', 'V', 'I', 'D'); // XVID (兼容MPEG-4)
        case mjpeg:
            return cv::VideoWriter::fourcc('M', 'J', 'P', 'G'); // Motion JPEG
        case divx:
            return cv::VideoWriter::fourcc('D', 'I', 'V', 'X'); // 老MPEG-4格式
        default:
            return cv::VideoWriter::fourcc('M', 'J', 'P', 'G'); // 默认使用mjpeg的类型
    }
}

bool VideoRecord::initRecordPath(const std::string &filename) {
    LOG_D("当前父文件路径 = %s", parentPath.c_str());
    if (parentPath.empty()) {
        return false; // 未设置保存的路径
    }
    try {
        std::filesystem::create_directories(parentPath);
    } catch (const std::exception &e) {
        LOG_E("创建文件夹报错 = %s", e.what());
        return false;
    }
    // 设置文件名
    std::string saveFileName = filename;
    if (saveFileName.empty()) {
        saveFileName = "video_" + formatTime("%Y%m%d_%H_%M%S%f", getCurrentTime());
    };
    if (format == mp4v || format == avc) {
        saveFileName = saveFileName + ".mp4";
    } else {
        saveFileName = saveFileName + ".avi";
    }
    if (parentPath.back() == '/') {
        this->recordFilePath = parentPath + saveFileName;
    } else {
        this->recordFilePath = parentPath + "/" + saveFileName;
    }
    LOG_E("当前录制的文件 = %s", this->recordFilePath.c_str());
    return true;
}