//
// Created by MI T on 2024/6/18.
//

#ifndef UVCCAMERA_LOG_H
#define UVCCAMERA_LOG_H

#include "android/log.h"

extern int DEBUG_ENABLE;

#define LOG_D(...) do { if(DEBUG_ENABLE) __android_log_print(ANDROID_LOG_INFO, "uvc_camera", __VA_ARGS__); } while(0)
#define LOG_E(...) do { if(DEBUG_ENABLE) __android_log_print(ANDROID_LOG_ERROR, "uvc_camera", __VA_ARGS__);} while(0)
#define        SAFE_FREE(p)                { if (p) { free((p)); (p) = NULL; } }
#define        SAFE_DELETE(p)                { if (p) { delete (p); (p) = NULL; } }
#define        SAFE_DELETE_ARRAY(p)        { if (p) { delete [](p); (p) = NULL; } }
#define        NUM_ARRAY_ELEMENTS(p)        ((int) sizeof(p) / sizeof(p[0]))
#define		MARK(...)
#define FPRINTF(stream, ...) MARK(__VA_ARGS__); usleep(1000);
#define FPRINTF_ERR(stream, ...) LOG_D(__VA_ARGS__)

#define		ENTER()				LOG_D("begin")
#define		RETURN(code,type)	{type RESULT = code; LOG_D("end (%d)", (int)RESULT); return RESULT;}
#define		RET(code)			{LOGD("end"); return code;}
#define		EXIT()				{LOG_D("end"); return;}
#define		PRE_EXIT()			LOGD("end")

#if defined(__GNUC__)
// the macro for branch prediction optimaization for gcc(-O2/-O3 required)
#define        CONDITION(cond)                ((__builtin_expect((cond)!=0, 0)))
#define        LIKELY(x)                    ((__builtin_expect(!!(x), 1)))    // x is likely true
#define        UNLIKELY(x)                    ((__builtin_expect(!!(x), 0)))    // x is likely false
#else
#define		CONDITION(cond)				((cond))
#define		LIKELY(x)					((x))
#define		UNLIKELY(x)					((x))
#endif

#endif //UVCCAMERA_LOG_H
