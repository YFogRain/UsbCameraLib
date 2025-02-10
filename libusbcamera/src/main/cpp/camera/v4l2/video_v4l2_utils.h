//
// Created on 2025/1/24.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_VIDEO_V4L2_UTILS_H
#define UVCCAMERA_VIDEO_V4L2_UTILS_H

#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"
#include <cstdint>
#include <utility>
#include <variant>
#include <iostream>
class VideoV4L2Utils {
public:
    // 获取当前分辨率支持的帧率范围
    static std::vector<int> getPreviewSizeFps(int fd, int format, int width, int height);

    static int v4l2FormatToInt(int format); // 将v4l2的分辨率格式转为当前识别的格式

    static int intToV4l2Format(int formatType); // 转换为v4l2的格式

    static bool isSupportVideoCapture(int fd); // 校验是否支持videoCapture流

    static bool setStreamPreviewSize(int fd, int format, int width, int height); // 自动使用最佳帧率

    // 查询支持的参数范围
    static std::variant<std::monostate, std::pair<int, int>> getSupportParameter(int fd, int id);
    // 获取当前参数
    static std::variant<std::monostate, int> getParameter(int fd, int id);

    static bool setParameter(int fd, int id, int value); // 设置参数

    static std::string getSupportPreviewSize(int fd); // 获取支持的分辨率列表
private:
    // 获取当前格式支持的分辨率信息
    static void getFormatPreviewSize(int fd, int format, rapidjson::Writer<rapidjson::StringBuffer> &writer);
    static bool setStreamFps(int fd, int fps); // 设置帧率信息
};
#endif // UVCCAMERA_VIDEO_V4L2_UTILS_H
