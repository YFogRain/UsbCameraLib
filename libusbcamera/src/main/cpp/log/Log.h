//
// Created by MI T on 2024/6/18.
//

#ifndef UVCCAMERA_LOG_H
#define UVCCAMERA_LOG_H

#include "android/log.h"

extern int DEBUG_ENABLE;

#define LOG_D(...) do { if(DEBUG_ENABLE) __android_log_print(ANDROID_LOG_INFO, "uvc_camera", __VA_ARGS__); } while(0)
#define LOG_E(...) do { if(DEBUG_ENABLE) __android_log_print(ANDROID_LOG_ERROR, "uvc_camera", __VA_ARGS__);} while(0)
#if defined(__GNUC__)
#define        CONDITION(cond)                ((__builtin_expect((cond)!=0, 0)))
#define        LIKELY(x)                    ((__builtin_expect(!!(x), 1)))    // x is likely true
#define        UNLIKELY(x)                    ((__builtin_expect(!!(x), 0)))    // x is likely false
#else
#define		CONDITION(cond)				((cond))
#define		LIKELY(x)					((x))
#define		UNLIKELY(x)					((x))
#endif

#endif //UVCCAMERA_LOG_H
