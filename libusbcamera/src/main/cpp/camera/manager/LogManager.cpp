//
// Created on 2025/4/12.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "LogManager.h"
#include "ConsoleAppender.h"
#include "FileAppender.h"
#include "Log.h"
#include "unordered_map"

LogManager LogManager::m_loggerManager;

LogManager::LogManager() {
    std::list<std::any> paramList; // 参数集合，只有local_file类型有
    addAppender("console", CONSOLE, paramList);
}

LogManager::~LogManager() { clearAppender(); }

bool LogManager::addAppender(const std::string &name, AppenderType type, std::list<std::any> param) {
    std::lock_guard<std::mutex> lock(mapMutex); // ✅ 加锁
    if (loggerMap.find(name) != loggerMap.end()) {
        LOG_E("存在指定名称的构建器");
        return false; // 如果已经存在，则直接return
    }
    auto appender = createAppender(type, param);
    if (!appender) {
        LOG_E("创建指定类型的构建器失败");
        return false;
    }
    loggerMap.emplace(std::move(name), std::move(appender)); // 更高效
    LOG_D("创建指定类型的构建器成功");
    return true;
}

std::unique_ptr<IBaseAppender> LogManager::createAppender(AppenderType type, std::list<std::any> param) {
    if (type == CONSOLE) {
        if (consoleAppenderCreated) {
            return nullptr;
        }
        consoleAppenderCreated = true;
        return std::make_unique<ConsoleAppender>();
    }
    if (param.size() < 7) {
        return nullptr; // 参数不足
    }
    auto iter = param.begin();
    try {
        FileOptions opts;
        opts.name = std::any_cast<std::string>(*iter++);
        opts.parentPath = std::any_cast<std::string>(*iter++);
        opts.isUserThread = std::any_cast<bool>(*iter++);
        opts.maxCacheCount = std::any_cast<int32_t>(*iter++);
        opts.expireTime = std::any_cast<int32_t>(*iter++) * 60 * 60 * 1000; // 这里转化为毫秒值
        opts.maxFileSize = std::any_cast<int32_t>(*iter++);
        opts.intervalLooperTime = std::any_cast<int32_t>(*iter++);
        LOG_D("文件构建器参数-name:%s", opts.name.c_str());
        LOG_D("文件构建器参数-parentPath:%s", opts.parentPath.c_str());
        LOG_D("文件构建器参数-maxCacheCount:%d", opts.maxCacheCount);
        LOG_D("文件构建器参数-expireTime:%ld", opts.expireTime);
        LOG_D("文件构建器参数-maxFileSize:%ld", opts.maxFileSize);
        LOG_D("文件构建器参数-intervalLooperTime:%ld", opts.intervalLooperTime);
        return std::make_unique<FileAppender>(opts);
    } catch (const std::bad_any_cast &e) {
        // 类型转换失败
        LOG_E("构建器创建失败，错误信息:%s", e.what());
        return nullptr;
    }
}

bool LogManager::removeAppender(const std::string &name) {
    std::lock_guard<std::mutex> lock(mapMutex); // ✅ 加锁
    auto it = loggerMap.find(name);             // 查找 appender
    if (it == loggerMap.end()) {
        return false; // 如果没有找到，则直接返回
    }
    auto appender = it->second.get(); // 获取 appender
    if (appender && appender->getType() == CONSOLE) {
        consoleAppenderCreated = false; // 如果是 ConsoleAppender，更新标志
    }
    loggerMap.erase(it); // 删除指定的 appender
    return true;
}

bool LogManager::clearAppender() {
    std::lock_guard<std::mutex> lock(mapMutex); // ✅ 加锁
    loggerMap.clear();
    consoleAppenderCreated = false;
    return true;
}

bool LogManager::logger(const Log4cLevel &level, const std::string &tag, const std::string &message) {
    std::lock_guard<std::mutex> lock(mapMutex); // ✅ 加锁
    for (const auto &[k, appender] : loggerMap) {
        appender.get()->onLogger(level, tag, message);
    }
    return true;
}