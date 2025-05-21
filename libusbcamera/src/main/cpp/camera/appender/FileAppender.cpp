//
// Created on 2025/4/11.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "FileAppender.h"
#include "Log.h"
#include <csignal>
#include <filesystem>
#include <sstream>
#include <chrono>
#include <fstream>
#include <iostream>
#include <thread>
#include <chrono>
#include <functional>
#include "cover_utils.h"

static bool isSameDate(std::time_t t1, std::time_t t2) {
    std::tm tm1, tm2;
    localtime_r(&t1, &tm1);
    localtime_r(&t2, &tm2);

    return (tm1.tm_year == tm2.tm_year &&
            tm1.tm_mon  == tm2.tm_mon  &&
            tm1.tm_mday == tm2.tm_mday);
}

FileAppender::FileAppender(FileOptions op) : options(std::move(op)), mIsDetectIng(true) {
    pthread_mutex_init(&messageMutex, nullptr);
    pthread_cond_init(&messageCond, nullptr);

    openLogFile(); // 打开日志文件
    if (options.isUserThread) {
        startReaderMessage();
    }
    mIsDetectIng = true;
    if (pthread_create(&fileDetectThread, nullptr, detect_thread_func, (void *)this) != 0) {
        mIsDetectIng = false;
    }
}

FileAppender::~FileAppender() {
    stopReaderMessage();

    mIsDetectIng = false;
    // 使用pthread_kill来检查线程是否存活
    detectCond.notify_all();

    pthread_mutex_destroy(&messageMutex);
    pthread_cond_destroy(&messageCond);

    if (logFile_.is_open()) {
        logFile_.close();
    }
    cacheFilesDeque.clear();
}

void FileAppender::onLogger(const Log4cLevel &level, const std::string &tag, const std::string &message) {
    if (options.isUserThread) {
        putMessage({tag, message, level, cover_utils::getCurrentTime()});
    } else {
        checkLogFileRotate(); // 检查是否需要切换文件
        writeLogToFile(formatMessage(cover_utils::getCurrentTime(), level, tag, message));
    }
}

void FileAppender::putMessage(MessageCache message) {
    pthread_mutex_lock(&messageMutex);
    messageDeque.push_back(std::move(message));
    if (messageDeque.size() > options.maxCacheCount) {
        messageDeque.pop_front(); // 控制缓存大小
    }
    pthread_cond_signal(&messageCond);
    pthread_mutex_unlock(&messageMutex);
}

MessageCache FileAppender::waitFirstMessage() {
    MessageCache msg;
    pthread_mutex_lock(&messageMutex);
    while (messageDeque.empty()) {
        pthread_cond_wait(&messageCond, &messageMutex);
    }
    if (mIsWaitReaderIng && !messageDeque.empty()) {
        msg = messageDeque.front();
        messageDeque.pop_front();
    }
    pthread_mutex_unlock(&messageMutex);
    return msg;
}

void *FileAppender::message_thread_func(void *vptr_args) {
    auto *appender = static_cast<FileAppender *>(vptr_args);
    appender->loadTodayCacheFiles(); // 开启时检查文件
    while (appender->mIsWaitReaderIng && appender->logFile_.is_open()) {
        MessageCache msg = appender->waitFirstMessage();
        appender->checkLogFileRotate(); // 检查是否需要切换文件
        if (!msg.message.empty()) {
            appender->writeLogToFile(appender->formatMessage(msg.time, msg.level, msg.tag, msg.message));
        }
    }
    return nullptr;
}

void *FileAppender::detect_thread_func(void *vptr_args) {
    auto *appender = static_cast<FileAppender *>(vptr_args);
    do { // 间隔两小时检查一次
        appender->deleteExpiredLogs();
        std::unique_lock<std::mutex> lock(appender->detectMutex);
        if (appender->detectCond.wait_for(lock, std::chrono::minutes(appender->options.intervalLooperTime),
                                          [appender]() { return !appender->mIsDetectIng; })) {
            // 如果mIsDetectIng被设置为false，则退出
            break;
        }
    } while (appender->mIsDetectIng);
    return nullptr;
}

// 删除过期文件
void FileAppender::deleteExpiredLogs() {
    // 获取当前文件夹下的所有文件
    auto currentTimeMillis =
        std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch())
            .count();
    LOG_D("当前的检查的时间:%lld", currentTimeMillis);
    for (const auto &entry : std::filesystem::directory_iterator(options.parentPath)) {
        const std::filesystem::path &filePath = entry.path(); // 获取当前文件的路径
        LOG_D("当前文件的路径:%s", filePath.c_str());
        if (filePath == mUseFilePath) { // 如果是当前文件，则跳过
            continue;
        }
        if (!std::filesystem::is_regular_file(filePath)) { // 如果不是文件，则跳过
            continue;
        }
        std::string fileName = filePath.filename().string();
        if (fileName.find(options.name) == std::string::npos || fileName.find(".log") == std::string::npos) {
            continue; // 当前文件不符合删除规则
        }
        // 获取最后写入的时间（更新时间）
        auto lastWriteTime = std::chrono::duration_cast<std::chrono::milliseconds>(
                                 std::filesystem::last_write_time(filePath).time_since_epoch())
                                 .count();
        LOG_D("最后一次更新的时间:%lld", lastWriteTime);
        if (currentTimeMillis - lastWriteTime < options.expireTime) { // 未超过时间
            continue;
        }
        std::filesystem::remove(filePath);
    }
}

void FileAppender::writeLogToFile(std::string message) {
    if (!logFile_.is_open()) {
        return;
    }
    logFile_ << message << std::endl; // 写入日志并换行
    rotateLogFileIfNeeded();          // 校验是否需要切换文件
}


void FileAppender::startReaderMessage() {
    mIsWaitReaderIng = true;
    if (pthread_create(&messageThread, nullptr, message_thread_func, (void *)this) != 0) {
        mIsWaitReaderIng = false;
    }
}

void FileAppender::stopReaderMessage() {
    mIsWaitReaderIng = false;
    pthread_cond_signal(&messageCond);
    // 使用pthread_kill来检查线程是否存活
    if (pthread_kill(messageThread, 0) == ESRCH || pthread_join(messageThread, nullptr) != 0) {
        LOG_E("UVCPreview::当前线程已经结束，或者等待结束线程失败");
    }
    clearMessage();
}

void FileAppender::clearMessage() {
    pthread_mutex_lock(&messageMutex);
    messageDeque.clear();
    pthread_mutex_unlock(&messageMutex);
}

void FileAppender::openLogFile() {
    if (logFile_.is_open()) {
        logFile_.close();
    }
    std::string path = options.parentPath;
    if (!path.empty() && path.back() != '/') {
        path += "/";
    }
    mUseFilePath = path + cover_utils::getDailyFileName(options.name);
    LOG_D("当前使用的文件的路径:%s", mUseFilePath.c_str());
    if (!std::filesystem::exists(mUseFilePath)) {
        std::filesystem::create_directories(path); // 创建父文件夹
    }
    logFile_.open(mUseFilePath, std::ios::out | std::ios::app);
}

void FileAppender::rotateLogFileIfNeeded() {
    if (cover_utils::getFileSize(mUseFilePath) < options.maxFileSize) {
        return; // 如果日志文件没有达到最大值，则不切换
    }
    logFile_.close(); // 关闭当前日志文件
    if (cacheFilesDeque.size() >= options.maxCacheCount) {
        auto file = cacheFilesDeque.front();
        cacheFilesDeque.pop_back();
        std::remove(file.c_str());
    }

    // 3. 构造新的备份路径
    std::string newPath = mUseFilePath;
    if (newPath.size() > 4 && newPath.substr(newPath.size() - 4) == ".log") {
        newPath = newPath.substr(0, newPath.size() - 4);
    }
    newPath += "_" + cover_utils::appendMillTime() + ".log";
    LOG_D("当前备份的文件的路径:%s", newPath.c_str());
    // 4. 重命名旧文件
    std::rename(mUseFilePath.c_str(), newPath.c_str());
    // 5. 重新打开 logFile_
    cacheFilesDeque.push_back(std::string(newPath));
    openLogFile();
}

void FileAppender::checkLogFileRotate() {
    // 1. 获取当前文件的创建时间/或者最后一次修改时间
    std::time_t fileModTime = cover_utils::getFileModificationTime(mUseFilePath);
    if (fileModTime == -1) {
        return; // 如果无法获取文件的修改时间，直接返回
    }
    // 2. 获取当前时间
    std::time_t currentTime = std::time(nullptr);
    // 3. 比较时间是否相同
    if (isSameDate(fileModTime, currentTime)) {
        return;
    }
    // 4. 不同的情况下关闭旧文件，重新加载新文件
    openLogFile();
    // 5. 重新加载缓存文件路径
    loadTodayCacheFiles();
}

void FileAppender::loadTodayCacheFiles() {
    cacheFilesDeque.clear();
    std::string fileNamePart = mUseFilePath.substr(mUseFilePath.find_last_of("/\\") + 1);
    for (const auto &entry : std::filesystem::directory_iterator(options.parentPath)) {
        const std::filesystem::path &filePath = entry.path(); // 获取当前文件的路径
        LOG_D("当前文件的路径:%s", filePath.c_str());
        if (!std::filesystem::is_regular_file(filePath)) { // 如果不是文件，则跳过
            continue;
        }
        std::string fileName = filePath.filename().string();
        if (fileName.find(fileNamePart) == std::string::npos || fileName.find(".log") == std::string::npos) {
            continue; // 当前文件不符合删除规则
        }
        LOG_D("加入缓存的文件地址:%s", filePath.c_str());
        cacheFilesDeque.push_back(filePath);
        // 获取最后写入的时间（更新时间）
    }
    // 4. 按文件的修改时间排序
    std::sort(cacheFilesDeque.begin(), cacheFilesDeque.end(), [](const std::string &a, const std::string &b) {
        return std::filesystem::last_write_time(a) < std::filesystem::last_write_time(b);
    });
}