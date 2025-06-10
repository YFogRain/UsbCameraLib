//
// Created on 2025/4/12.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "cover_utils.h"
#include <ctime>
#include <chrono>
#include <iomanip>
#include <sstream>
#include <string>
#include <sys/stat.h>

long cover_utils::getCurrentTime() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch())
        .count();
}

std::string cover_utils::formatTime(const std::string &pattern, long timeMillis) {
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

long cover_utils::getFileSize(const std::string &path) {
    struct stat st;
    if (stat(path.c_str(), &st) == 0) {
        return st.st_size;
    }
    return 0;
}

std::string cover_utils::getDailyFileName(const std::string &name) {
    std::time_t now = std::time(nullptr);
    std::tm tm_now;
    localtime_r(&now, &tm_now); // 线程安全
    std::ostringstream oss;
    oss << name << "_"                                                    // 名称
        << tm_now.tm_year + 1900 << "_"                                   // 年份
        << std::setw(2) << std::setfill('0') << tm_now.tm_mon + 1 << "_"  // 月份，确保两位数
        << std::setw(2) << std::setfill('0') << tm_now.tm_mday << ".log"; // 日期，确保两位数

    return oss.str();
}

std::string cover_utils::appendMillTime() {
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;
    std::time_t now_time_t = std::chrono::system_clock::to_time_t(now);
    std::tm tm_now;
    localtime_r(&now_time_t, &tm_now); // 线程安全的版本
    std::ostringstream oss;
    oss << std::setfill('0') << std::setw(2) << tm_now.tm_hour << std::setw(2) << tm_now.tm_min << std::setw(2)
        << tm_now.tm_sec << std::setw(3) << ms.count(); // 毫秒补足3位
    return oss.str();                                   // e.g., "153045-123"
}


std::time_t cover_utils::getFileModificationTime(const std::string &filePath) {
    struct stat fileStat;
    if (stat(filePath.c_str(), &fileStat) != 0) {
        return -1;
    }
    return fileStat.st_mtime; // 返回文件的最后修改时间
}