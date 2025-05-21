//
// Created on 2025/4/11.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef LOG4A_MASTER_IBASEAPPENDER_H
#define LOG4A_MASTER_IBASEAPPENDER_H

#include <string>
enum AppenderType { CONSOLE, LOCAL_FILE,CUSTOM };

enum Log4cLevel { OFF, DEBUG, INFO, WARN, ERROR, TRACE };


class IBaseAppender {

public:
    virtual ~IBaseAppender() = default;
    // 日志输出
    virtual void onLogger(const Log4cLevel& level,const std::string& tag,const std::string& message) = 0;
    virtual AppenderType getType() = 0;
};

#endif // LOG4A_MASTER_IBASEAPPENDER_H
