
//
// Created on 2025/1/23.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#ifndef UVCCAMERA_CAMERA_CONSTANTS_H
#define UVCCAMERA_CAMERA_CONSTANTS_H

/// 最大缓存图像数量
#define MAX_FRAME 2

///支持的预览参数类型分类
#define CAMERA_PARAMETER_PREVIEW_SIZE 0        // 预览分辨率
#define CAMERA_PARAMETER_ORIENTATION 1         // 预览方向
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
#define CAMERA_PARAMETER_MIRROR 18             // 镜像处理

#define  UVC_FORMAT_FRAME_WINDOW  WINDOW_FORMAT_RGBA_8888

/// 支持的预览格式
#define PREVIEW_FORMAT_BGR 0
#define PREVIEW_FORMAT_YUY2 1
#define PREVIEW_FORMAT_NV21 2
#define PREVIEW_FORMAT_NV12 3
#define PREVIEW_FORMAT_RGBA 4
#define PREVIEW_FORMAT_RGB 5
#define PREVIEW_FORMAT_MJPEG 6
#define PREVIEW_FORMAT_JPEG 7

#endif // UVCCAMERA_CAMERA_CONSTANTS_H
