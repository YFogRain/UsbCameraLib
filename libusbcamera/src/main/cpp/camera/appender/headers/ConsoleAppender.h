//
// Created on 2025/4/12.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef LOG4A_MASTER_CONSOLEAPPENDER_H
#define LOG4A_MASTER_CONSOLEAPPENDER_H

#include <android/log.h>
#include "IBaseAppender.h"

class ConsoleAppender : public IBaseAppender {
private:
    android_LogPriority getLevelType(const Log4cLevel& level);
public:
    ~ConsoleAppender() override;
    void onLogger(const Log4cLevel& level,const std::string& tag,const std::string& message) override;
    AppenderType getType() override{
        return CONSOLE;
    };
};
#endif // LOG4A_MASTER_CONSOLEAPPENDER_H
