//
// Created by MI T on 2024/12/30.
//


#include "img_util.h"
#include "CameraParameterState.h"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "Log.h"

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

cv::Mat ImgUtils::bgr2Any(uvc_frame_t *inFrame, int mode) {
    if (!inFrame || !inFrame->data || inFrame->data_bytes <= 0 || inFrame->width <= 0 ||
        inFrame->height <= 0) {
        return cv::Mat();
    }
    if (mode == UVC_DATA_FORMAT_BGR) {
        return cv::Mat(inFrame->height, inFrame->width, CV_8UC3, inFrame->data);
    }
    if (mode == UVC_DATA_FORMAT_RGBA) {
        cv::Mat outImg = cv::Mat(inFrame->height, inFrame->width, CV_8UC4);
        cv::cvtColor(cv::Mat(inFrame->height, inFrame->width, CV_8UC3, inFrame->data), outImg,
                     cv::COLOR_BGR2RGBA);
        return outImg;
    }
    if (mode != UVC_DATA_FORMAT_NV21) {
        return cv::Mat();
    }
    cv::Mat yuvImg = cv::Mat(inFrame->height + inFrame->height / 2, inFrame->width, CV_8UC1);
    cv::cvtColor(cv::Mat(inFrame->height, inFrame->width, CV_8UC3, inFrame->data), yuvImg,
                 cv::COLOR_BGR2YUV_I420);
    if (yuvImg.empty()) {//如果没数据，直接返回失败
        return yuvImg;
    }
    uint32_t width = inFrame->width;
    uint32_t height = inFrame->height;
    //将yuv的数据值翻转一下，转换成nv21
    cv::Mat nv21Img = cv::Mat(inFrame->height + inFrame->height / 2, inFrame->width, CV_8UC1);
    int ySize = width * height;
    memcpy(nv21Img.data, yuvImg.data, ySize);
    // 将 UV 平面交织为 VU 顺序
    uint8_t *uPlane = yuvImg.data + ySize;
    uint8_t *vPlane = uPlane + (ySize / 4);
    uint8_t *uvData = nv21Img.data + ySize;
    for (int i = 0; i < ySize / 4; ++i) {
        uvData[i * 2] = vPlane[i];     // V 分量
        uvData[i * 2 + 1] = uPlane[i]; // U 分量
    }
    return nv21Img;
}
