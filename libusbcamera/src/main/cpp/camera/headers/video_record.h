//
// Created on 2025/5/20.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_VIDEO_RECORD_H
#define UVCCAMERA_VIDEO_RECORD_H
// 录制操作
#include "camera_constants.h"
#include "opencv2/videoio.hpp"
#include <cstddef>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <thread>

extern std::string defaultParentPath;

void setDefaultParent(const std::string & path);

typedef struct record_frame {
    uint8_t *data;    // 数据
    int format;       // 类型，参考camera_constants.h
    uint32_t width;   // 数据宽度
    uint32_t height;  // 数据高度
    size_t data_size; // 数据长度
    int rotation;     // 旋转方向
} record_frame_t;

static inline void free_record_frame(record_frame_t *frame) {
    if (frame) {
        if (frame->data_size > 0 && frame->data) {
            free(frame->data);
            frame->data = nullptr;
        }
        free(frame);
        frame = nullptr;
    }
}

enum record_format {
    mp4v,  //.mp4
    avc,   //.mp4
    vid,   //.avi
    mjpeg, //.avi
    divx,  //.avi
};

class VideoRecord {
public:
    VideoRecord();
    ~VideoRecord();
    void putFrame(record_frame_t *frame);                                                     // 发送流数据
    void setRecordFormat(const record_format &recordFormat);                                        // 设置编码格式
    bool prepare(uint32_t w, uint32_t h, int rotation, int fps, const std::string &filename); // 准备
    bool startRecord();                                                                       // 开始录制
    void stopRecord();                                                                        // 结束录制
    void setParentPath(const std::string &parentPath);                                        // 设置父目录
    std::string getRecordPath() { return recordFilePath; }                            // 返回当前录制的路径
    bool isRecording() { return mIsRecordRunning.load() && videoWriter.isOpened(); }; // 是否正在运行
    bool checkFrames(uint32_t w, uint32_t h) {
        return (w == frameWidth && h == frameHeight) || (h == frameWidth && w == frameHeight);
    }; // 检查数据合法性

private:
    std::atomic<bool> mIsRecordRunning; // 当前录制运行的状态
    std::string recordFilePath;         // 文件路径
    std::string parentPath;             // 保存的文件夹路径
    record_format format;               // 录制格式
    uint32_t frameWidth, frameHeight;
    std::mutex recordMutex;                  // 捕获线程的互斥锁
    std::thread recordThread;                // 捕获预览流，并且绘制到页面的线程
    std::condition_variable recordCond;      // 等待专用的条件变量
    std::deque<record_frame *> recordFrames; // 检测的缓存数据
    record_frame_t *waitRecordFrame();       // 等待获取录制流
    cv::VideoWriter videoWriter;             // 视频录制的写入器
    int mRotation;                           // 当前的旋转方向
    void thread_func_record();               // 录制专用的线程

    void clearRecordFrames(); // 清空缓存数据

    long getCurrentTime(); // 获取当前时间的格式化字符串

    std::string formatTime(const std::string &pattern, long time); // 格式化时间

    bool initRecordPath(const std::string &filename);

    int initRecordFourcc(); // 初始化录制的编码器
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
#endif // UVCCAMERA_VIDEO_RECORD_H
