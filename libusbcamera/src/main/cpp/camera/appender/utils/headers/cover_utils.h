//
// Created on 2025/4/12.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef LOG4A_MASTER_TIME_COVER_UTILS_H
#define LOG4A_MASTER_TIME_COVER_UTILS_H
#include "string"
#include <cstdint>

#define DEFAULT_TIME_FORMAT "%Y-%m-%d %H:%M:%S%f"
class cover_utils {
public:
    static long getCurrentTime(); // 获取当前时间的格式化字符串

    static std::string formatTime(const std::string &pattern, long time); // 格式化时间

    static long getFileSize(const std::string& path); // 获取当前文件的大小

    static std::string getDailyFileName(const std::string& name); // 获取当前文件名称

    static std::string appendMillTime(); // 追加毫秒值
    
    static std::time_t getFileModificationTime(const std::string &filePath);
};
#endif // LOG4A_MASTER_TIME_COVER_UTILS_H
