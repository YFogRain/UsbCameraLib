//
// Created on 2025/4/12.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "ConsoleAppender.h"
#include "android/log.h"

ConsoleAppender::~ConsoleAppender() {}

android_LogPriority ConsoleAppender::getLevelType(const Log4cLevel &level) {
    switch (level) {
        case DEBUG:
            return ANDROID_LOG_DEBUG;
        case INFO:
            return ANDROID_LOG_INFO;
        case WARN:
            return ANDROID_LOG_WARN;
        case ERROR:
            return ANDROID_LOG_ERROR;
        default:
            return ANDROID_LOG_UNKNOWN;
    }
}

void ConsoleAppender::onLogger(const Log4cLevel &level, const std::string &tag,
                               const std::string &message) {
    auto logLevel = getLevelType(level);
    if (logLevel == ANDROID_LOG_UNKNOWN) {
        return;
    }
    __android_log_print(logLevel, tag.c_str(), message.c_str(), nullptr);
}