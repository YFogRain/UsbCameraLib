
//
// Created on 2025/1/23.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_CONSTANTS_H
#define UVCCAMERA_CAMERA_CONSTANTS_H

#define MAX_FRAME 2

// 支持的预览参数类型
#define CAMERA_PARAMETER_PREVIEW_SIZE 0        // 预览分辨率
#define CAMERA_PARAMETER_DISPLAY_TRANSFORM 1   // 方向
#define CAMERA_PARAMETER_AUTO_EXPOSURE 2       // 自动曝光
#define CAMERA_PARAMETER_EXPOSURE 3            // 曝光度
#define CAMERA_PARAMETER_BRIGHTNESS 4          // 亮度
#define CAMERA_PARAMETER_CONTRAST 5            // 对比度
#define CAMERA_PARAMETER_GAIN 6                // 增益值
#define CAMERA_PARAMETER_SATURATION 7          // 饱和度
#define CAMERA_PARAMETER_ZOOM 8                // 缩放
#define CAMERA_PARAMETER_AUTO_FOCUS 9          // 自动对焦
#define CAMERA_PARAMETER_FOCUS 10              // 焦距
#define CAMERA_PARAMETER_IRIS 11               // 光圈
#define CAMERA_PARAMETER_AUTO_HUE 12           // 自动变化色调
#define CAMERA_PARAMETER_HUE 13                // 色调
#define CAMERA_PARAMETER_AUTO_WHITE_BALANCE 14 // 自动白平衡
#define CAMERA_PARAMETER_WHITE_BALANCE 15      // 白平衡
#define CAMERA_PARAMETER_SCENE_MODE 16         // 场景模式
#define CAMERA_PARAMETER_PRIVACY 17            // 隐私模式

#define  UVC_FORMAT_FRAME_WINDOW  WINDOW_FORMAT_RGBA_8888

/// 通过下面参数可组合镜像方式+旋转角度
#define TRANSFORM_IDENTITY 0          // 不做变化
#define TRANSFORM_MIRROR_HORIZONTAL 1 // 水平镜像
#define TRANSFORM_MIRROR_VERTICAL 2   // 垂直镜像

#define TRANSFORM_ROTATE_90 3  // 旋转90度
#define TRANSFORM_ROTATE_180 4 // 旋转180度
#define TRANSFORM_ROTATE_270 5 // 旋转270度

#define TRANSFORM_FLIP_H_ROTATE_90 6  // 旋转90度+水平镜像
#define TRANSFORM_FLIP_H_ROTATE_180 7 // 旋转180度+水平镜像
#define TRANSFORM_FLIP_H_ROTATE_270 8 // 旋转270度+水平镜像

#define TRANSFORM_FLIP_V_ROTATE_90 9   // 旋转90度+垂直镜像
#define TRANSFORM_FLIP_V_ROTATE_180 10 // 旋转180度+垂直镜像
#define TRANSFORM_FLIP_V_ROTATE_270 11 // 旋转270度+垂直镜像


// 支持的输出格式，目前支持三种
#define UVC_DATA_FORMAT_BGR 0
#define UVC_DATA_FORMAT_NV21 1
#define UVC_DATA_FORMAT_RGBA 2


// 支持的预览格式类型
#define PREVIEW_FORMAT_YUY2 0
#define PREVIEW_FORMAT_NV12 1
#define PREVIEW_FORMAT_NV21 2
#define PREVIEW_FORMAT_MJPEG 3
#define PREVIEW_FORMAT_JPEG 4
#define PREVIEW_FORMAT_RGB24 5
#define PREVIEW_FORMAT_BGR24 6

#endif // UVCCAMERA_CAMERA_CONSTANTS_H
