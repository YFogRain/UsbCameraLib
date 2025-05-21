//
// Created on 2025/4/11.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef LOG4A_MASTER_FILEAPPENDER_H
#define LOG4A_MASTER_FILEAPPENDER_H
#include "IBaseAppender.h"
#include "cover_utils.h"
#include <deque>
#include <fstream>

struct FileOptions {
    std::string name;        // 文件名，无需后缀
    std::string parentPath;  // 文件保存的路径
    bool isUserThread;       // 是否使用线程
    int maxCacheCount;       // 最大缓存数量
    long expireTime;         // 最大保存时间
    long maxFileSize;        // 文件最大写入大小
    long intervalLooperTime; // 间隔轮训时间（秒）
};

// 当前日志的缓存类
struct MessageCache {
    std::string tag;
    std::string message;
    Log4cLevel level;
    long time;
};

class FileAppender : public IBaseAppender {
public:
    void onLogger(const Log4cLevel& level,const std::string& tag,const std::string& message) override;
    FileAppender(FileOptions options);
    ~FileAppender() override;

    bool setCustomPattern(std::string pattern) {
        if (pattern.find("@message") == std::string::npos) {
            return false;
        }
        mPattern = pattern;
        return true;
    }; // 设置自定义样式
    AppenderType getType() override { return LOCAL_FILE; };

private:
    std::string mPattern = "@time [@level] - [@tag] : @message";
    std::deque<MessageCache> messageDeque;   // 消息打印缓存池
    std::deque<std::string> cacheFilesDeque; // 缓存文件
    std::string mUseFilePath;                // 文件路径,删除时检测是否为当前正在操作的文件
    volatile bool mIsWaitReaderIng = false;  // 当前读取打印消息的状态
    volatile bool mIsDetectIng;              // 当前读取打印消息的状态
    std::ofstream logFile_;                  // 日志文件的流操作
    FileOptions options;                     // 当前配置信息
    pthread_t fileDetectThread;              // 文本文件检测线程

    pthread_t messageThread;      // 消息线程
    pthread_mutex_t messageMutex; // 消息线程的互斥锁
    pthread_cond_t messageCond;   // 消息线程等待专用的条件变量

    std::mutex detectMutex;             // 消息线程的互斥锁
    std::condition_variable detectCond; // 条件变量

    void putMessage(MessageCache message); // 发送消息

    MessageCache waitFirstMessage(); // 等待获取一个消息

    void clearMessage(); // 清空消息池

    static void *message_thread_func(void *vptr_args); // 当前消息线程的回调
    static void *detect_thread_func(void *vptr_args);  // 当前检测线程的回调
    void startReaderMessage();                         // 开启读取线程
    void stopReaderMessage();                          // 开启读取线程
    void openLogFile();                                // 打开需要写入的日志文件

    std::string levelToString(Log4cLevel level) {
        switch (level) {
        case DEBUG:
            return "DEBUG";
        case INFO:
            return "INFO";
        case WARN:
            return "WARN";
        case ERROR:
            return "ERROR";
        case TRACE:
            return "TRACE";
        default:
            return "UNKNOWN";
        }
    }
    // 格式化需要打印的字符串
    std::string formatMessage(const long& time, const Log4cLevel& level,const std::string& tag,const std::string& message) {
        if (level == OFF) {
            return std::string();
        }
        // 可以先调用监听，如果他支持自定义格式化，则使用他的数据，否则，自己格式化
        std::string formattedMessage = mPattern;
        size_t pos = formattedMessage.find("@time");
        if (pos != std::string::npos) { // 设置时间
            auto timeFormat = cover_utils::formatTime(DEFAULT_TIME_FORMAT, time);
            formattedMessage.replace(pos, 5, timeFormat);
        }
        pos = formattedMessage.find("@level");
        if (pos != std::string::npos) { // 设置日志等级打印
            formattedMessage.replace(pos, 6, levelToString(level));
        }
        pos = formattedMessage.find("@tag");
        if (pos != std::string::npos) { // 设置时间
            formattedMessage.replace(pos, 4, tag);
        }
        pos = formattedMessage.find("@message");
        if (pos != std::string::npos) { // 设置消息
            formattedMessage.replace(pos, 8, message);
        }
        return formattedMessage;
    }

    void rotateLogFileIfNeeded(); // 切日志，保存旧日志，新建文件

    void writeLogToFile(std::string message); // 写入文件

    void deleteExpiredLogs(); // 删除过期文件

    void checkLogFileRotate(); // 检查是否需要切换日志文件

    void loadTodayCacheFiles(); // 加载今天的所有日志缓存文件路径
};

#endif // LOG4A_MASTER_FILEAPPENDER_H
