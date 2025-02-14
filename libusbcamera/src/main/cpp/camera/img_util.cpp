//
// Created by MI T on 2024/12/30.
//


#include "img_util.h"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "Log.h"
#include "camera_constants.h"
#include "android/native_window.h"

uvc_frame_t *ImgUtils::initOutFrame(uvc_frame_t *inFrame) {
    // 转换为BGR格式，方便赋值
    uvc_frame_t *outFrame = uvc_allocate_frame(inFrame->width * inFrame->height * 3);
    outFrame->width = inFrame->width;              // 高度
    outFrame->height = inFrame->height;            // 宽度
    outFrame->frame_format = UVC_FRAME_FORMAT_BGR; // 设置类型为BGR
    outFrame->step = inFrame->width * 3;           // 三通道步长
    outFrame->sequence = inFrame->sequence;
    outFrame->capture_time = inFrame->capture_time;
    outFrame->capture_time_finished = inFrame->capture_time_finished;
    outFrame->source = inFrame->source;
    return outFrame;
}

uvc_frame_t *ImgUtils::any2BGR(uvc_frame_t *inFrame) {
    // 检查数据，必须要的～
    if (!inFrame || !inFrame->data || inFrame->data_bytes <= 0 || inFrame->width <= 0 ||
        inFrame->height <= 0) {
        LOG_E("当前数据不对劲啊～");
        return nullptr;
    }
    // 设置输出的参数信息
    uvc_frame_t *outFrame = initOutFrame(inFrame);
    uint8_t *data = static_cast<uint8_t *>(inFrame->data);
    uint32_t width = inFrame->width;
    uint32_t height = inFrame->height;

    uvc_frame_format format = inFrame->frame_format;            // 当前的类型啊
    cv::Mat outImg(height, width, CV_8UC3);
    if (format == UVC_FRAME_FORMAT_MJPEG) { // 当前是mjpeg类型
        // 解析mjpeg图像，耗时130ms上下，目前没有好的优化方案
        outImg = cv::imdecode(cv::Mat(1, inFrame->data_bytes, CV_8UC1, data), cv::IMREAD_COLOR);
    } else if (format == UVC_FRAME_FORMAT_YUYV) {
        // 解析yuv数据，
        cv::cvtColor(cv::Mat(height, width, CV_8UC2, data), outImg, cv::COLOR_YUV2BGR_YUY2);
    } else if (format == UVC_FRAME_FORMAT_NV12) { // nv12格式
        cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, data), outImg,
                     cv::COLOR_YUV2BGR_NV12);
    } else if (format == UVC_FRAME_FORMAT_BGR) { // 直接复制bgr格式数据，
        std::memcpy(outFrame->data, inFrame->data, inFrame->data_bytes);
        return outFrame;
    } else {
        uvc_free_frame(outFrame);
        return nullptr;
    }
    if (outImg.empty()) {
        LOG_E("无法解码图像或图像为空！");
        uvc_free_frame(outFrame);
        return nullptr;
    }
    // 因为要保证内存数据有效，这里必须进行一次内存复制
    std::memcpy(outFrame->data, outImg.data, outImg.total() * outImg.elemSize());
    return outFrame;
}

cv::Mat ImgUtils::bgr2Any(uint8_t *inFrame, int width, int height, int mode) {
    if (!inFrame || width == 0 || height == 0) {
        LOG_E("当前数据不对劲啊～");
        return cv::Mat();
    }
    cv::Mat inImg(height, width, CV_8UC3, inFrame);
    if (mode == UVC_DATA_FORMAT_BGR) { // 如果是bgr。则不需要进行转换，直接可以返回数据
        return inImg;
    }
    cv::Mat outImg;
    if (mode == UVC_DATA_FORMAT_RGBA) {
        outImg = cv::Mat(height, width, CV_8UC4);
        cv::cvtColor(inImg, outImg, cv::COLOR_BGR2RGBA);
    } else if (mode == UVC_DATA_FORMAT_NV21) {
        cv::Mat yuvImg(height + height / 2, width, CV_8UC1);
        cv::cvtColor(inImg, yuvImg, cv::COLOR_BGR2YUV_I420);
        if (!yuvImg.empty()) {
            outImg = cv::Mat(height + height / 2, width, CV_8UC1);
            // 将 Y 平面复制到 NV21 缓冲区
            int ySize = width * height;
            memcpy(outImg.data, yuvImg.data, ySize);
            // 将 UV 平面交织为 VU 顺序
            uint8_t *uPlane = yuvImg.data + ySize;
            uint8_t *vPlane = uPlane + (ySize / 4);
            uint8_t *uvData = outImg.data + ySize;
            for (int i = 0; i < ySize / 4; ++i) {
                uvData[i * 2] = vPlane[i];     // V 分量
                uvData[i * 2 + 1] = uPlane[i]; // U 分量
            }
        }
    }
    return outImg;
}


bool ImgUtils::setDisplayTransformState(ANativeWindow *window, int orientation) {
    LOG_D("当前的预览方向为:%d", orientation);
    if (!window) {
        return false;
    }
    int rotationType;
    switch (orientation) {
        case TRANSFORM_MIRROR_HORIZONTAL: //水平镜像
            rotationType = ANATIVEWINDOW_TRANSFORM_MIRROR_HORIZONTAL;
            break;
        case TRANSFORM_MIRROR_VERTICAL://垂直镜像
            rotationType = ANATIVEWINDOW_TRANSFORM_MIRROR_VERTICAL;
            break;
        case TRANSFORM_ROTATE_90://旋转90度
            rotationType = ANATIVEWINDOW_TRANSFORM_ROTATE_90;
            break;
        case TRANSFORM_ROTATE_180://旋转180度 = 水平镜像+垂直镜像
            rotationType = ANATIVEWINDOW_TRANSFORM_ROTATE_180;
            break;
        case TRANSFORM_ROTATE_270://旋转270度 = 水平镜像 + 垂直镜像 + 旋转90度
            rotationType = ANATIVEWINDOW_TRANSFORM_ROTATE_270;
            break;
        case TRANSFORM_FLIP_H_ROTATE_90: //水平镜像 + 旋转90度;
            rotationType =
                    ANATIVEWINDOW_TRANSFORM_MIRROR_HORIZONTAL | ANATIVEWINDOW_TRANSFORM_ROTATE_90;
            break;
        case TRANSFORM_FLIP_H_ROTATE_180://水平镜像 + 旋转180度 = 垂直镜像
            rotationType = ANATIVEWINDOW_TRANSFORM_MIRROR_VERTICAL;
            break;
        case TRANSFORM_FLIP_H_ROTATE_270://水平镜像 + 旋转270度 = 垂直镜像 + 旋转90度
            rotationType =
                    ANATIVEWINDOW_TRANSFORM_MIRROR_VERTICAL | ANATIVEWINDOW_TRANSFORM_ROTATE_90;
            break;
        case TRANSFORM_FLIP_V_ROTATE_90://垂直镜像 + 旋转90度
            rotationType =
                    ANATIVEWINDOW_TRANSFORM_MIRROR_VERTICAL | ANATIVEWINDOW_TRANSFORM_ROTATE_90;
            break;
        case TRANSFORM_FLIP_V_ROTATE_180://垂直镜像 + 旋转180度 = 水平镜像
            rotationType = ANATIVEWINDOW_TRANSFORM_MIRROR_HORIZONTAL;
            break;
        case TRANSFORM_FLIP_V_ROTATE_270://垂直镜像 + 旋转270度 = 水平镜像 + 旋转90度
            rotationType =
                    ANATIVEWINDOW_TRANSFORM_MIRROR_HORIZONTAL | ANATIVEWINDOW_TRANSFORM_ROTATE_90;
            break;
        default:
            rotationType = ANATIVEWINDOW_TRANSFORM_IDENTITY; //默认不使用图像变换
            break;
    }
    int32_t ret = native_window_set_buffers_sticky_transform(window, rotationType);
    LOG_D("设置window窗口方向-结果:%d", ret);
    return ret == 0;
}


cv::Mat ImgUtils::any2BGR(uint8_t *inFrame, int format, int dataBytes, int width, int height) {
    if (!inFrame || width == 0 || height == 0) {
        LOG_E("当前数据不对劲啊～");
        return cv::Mat();
    }
    cv::Mat outImg(height, width, CV_8UC4);
    if (format == PREVIEW_FORMAT_YUY2) {
        cv::cvtColor(cv::Mat(height, width, CV_8UC2, inFrame), outImg, cv::COLOR_YUV2BGR_YUYV);
    } else if (format == PREVIEW_FORMAT_NV12) {
        cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg,
                     cv::COLOR_YUV2BGR_NV12);
    } else if (format == PREVIEW_FORMAT_NV21) {
        cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg,
                     cv::COLOR_YUV2BGR_NV21);
    } else if (format == PREVIEW_FORMAT_MJPEG) {
        outImg = cv::imdecode(cv::Mat(1, dataBytes, CV_8UC1, inFrame), cv::IMREAD_COLOR);
    } else if (format == PREVIEW_FORMAT_BGR24) {
        outImg = cv::Mat(height, width, CV_8UC3, inFrame);
    } else if (format == PREVIEW_FORMAT_RGB24) {
        cv::cvtColor(cv::Mat(height, width, CV_8UC3, inFrame), outImg, cv::COLOR_RGB2BGR);
    } else {
        return cv::Mat();
    }
    if (outImg.empty()) {
        LOG_E("解析后的数据为空");
    }
    // 因为要保证内存数据有效，这里必须进行一次内存复制
    return outImg;
}
