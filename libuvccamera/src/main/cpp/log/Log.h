//
// Created by MI T on 2024/6/18.
//

#ifndef UVCCAMERA_LOG_H
#define UVCCAMERA_LOG_H

#include "android/log.h"

#define LOG_D(tag, ...) do { __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__); } while(0)
#define LOG_E(tag, ...) do {__android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__);} while(0)

#endif //UVCCAMERA_LOG_H
