//
// Created on 2025/4/12.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef LOG4A_MASTER_LOGMANAGER_H
#define LOG4A_MASTER_LOGMANAGER_H

#include <list>
#include <any>
#include <mutex>
#include "IBaseAppender.h"

class LogManager {
private:
    static LogManager m_loggerManager;
    std::unordered_map<std::string, std::unique_ptr<IBaseAppender>> loggerMap;
    std::mutex mapMutex; // ✅ 线程锁
    bool consoleAppenderCreated = false;
public:
    LogManager();

    ~LogManager();

    static LogManager *GetInstance() { return &LogManager::m_loggerManager; }

    bool addAppender(const std::string &name, AppenderType type, std::list<std::any> param);

    std::unique_ptr<IBaseAppender> createAppender(AppenderType type, std::list<std::any> param);

    bool removeAppender(const std::string &name);

    bool clearAppender();

    bool logger(const Log4cLevel &level, const std::string &tag, const std::string &message );
};

#endif // LOG4A_MASTER_LOGMANAGER_H
