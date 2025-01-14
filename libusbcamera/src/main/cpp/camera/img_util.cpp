//
// Created by MI T on 2024/12/30.
//


#include "img_util.h"
#include "CameraParameterState.h"
#include "opencv2/core/mat.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "Log.h"

bool ImgUtils::any2Rgba(uvc_frame_t *inFrame, uvc_frame_t *outFrame) {
    if (!inFrame || !inFrame->data || inFrame->data_bytes <= 0 || inFrame->width <= 0 ||
        inFrame->height <= 0) {
        LOG_E("当前数据不对劲啊～");
        return false;
    }
    uvc_frame_format format = inFrame->frame_format;
    uint8_t *data = static_cast<uint8_t *>(inFrame->data);
    uint32_t width = inFrame->width;
    uint32_t height = inFrame->height;
    cv::Mat img;
    int cvColoType;
    if (format == UVC_FRAME_FORMAT_MJPEG) {
        std::vector<uchar> jpegData(data, data + inFrame->data_bytes);
        img = cv::imdecode(jpegData, cv::IMREAD_COLOR);
        cvColoType = cv::COLOR_BGR2RGBA;
    } else if (format == UVC_FRAME_FORMAT_YUYV) {
        img = cv::Mat(height, width, CV_8UC2, data);  // YUYV 格式
        cvColoType = cv::COLOR_YUV2RGBA_YUY2;
    } else if (format == UVC_FRAME_FORMAT_NV12) {
        img = cv::Mat(height + height / 2, width, CV_8UC1, data);  // NV21 格式
        cvColoType = cv::COLOR_YUV2RGBA_NV12;  // 转换为 RGBA 格式
    } else {
        return false;
    }
    if (img.empty()) {
        LOG_E("无法解码图像或图像为空！");
        return false;
    }
    cv::Mat rgbaImg;  // OpenCV Mat 的 BGR 图像格式
    cv::cvtColor(img, rgbaImg, cvColoType);  // 转换为 BGRA 格式
    if (rgbaImg.empty()) {
        LOG_E("转换成RGBA的类型失败啦！");
        return false;
    }
    // 5. 确保 rgbaFrame 有足够的内存来存储转换后的图像
    size_t bytesLength = rgbaImg.total() * rgbaImg.elemSize();  // 计算字节数
    if (bytesLength <= 0 || outFrame->data_bytes < bytesLength) {
        LOG_E("outFrame 数据缓冲区不足，无法存储转换后的图像！");
        return false;
    }
    outFrame->width = inFrame->width;
    outFrame->height = inFrame->height;
    outFrame->frame_format = UVC_FRAME_FORMAT_RGB;
    outFrame->step = inFrame->width * 4;
    outFrame->sequence = inFrame->sequence;
    outFrame->capture_time = inFrame->capture_time;
    outFrame->capture_time_finished = inFrame->capture_time_finished;
    outFrame->source = inFrame->source;
    // 6. 将转换后的 RGBA 图像数据复制到 rgbaFrame
    std::memcpy(outFrame->data, rgbaImg.data, outFrame->data_bytes);
    return true;
}

uvc_frame_t *ImgUtils::rgba2Nv21(uvc_frame_t *srcFrame) {
    if (!srcFrame || !srcFrame->data || srcFrame->width <= 0 || srcFrame->height <= 0) {
        return nullptr;
    }
    auto img = cv::Mat(srcFrame->height, srcFrame->width, CV_8UC4, srcFrame->data);
    cv::Mat outImg;
    cv::cvtColor(img, outImg, cv::COLOR_RGBA2YUV_I420);
    size_t bytesLength = outImg.total() * outImg.elemSize(); // 计算字节数
    if (bytesLength <= 0) {
        LOG_E("转换失败");
        return nullptr; // 内存分配失败，返回 false
    }
    uvc_frame_t *pFrame = uvc_allocate_frame(srcFrame->width * srcFrame->height * 3 / 2);
    if (!pFrame) {
        LOG_E("创建输出的uvc对象失败");
        return nullptr;
    }
    uint8_t *data = static_cast<uint8_t *>(pFrame->data);
    // 将 Y 平面复制到 NV21 缓冲区
    int ySize = srcFrame->width * srcFrame->height;
    memcpy(data, outImg.data, ySize);

    // 将 UV 平面交织为 VU 顺序
    uint8_t *uPlane = outImg.data + ySize;
    uint8_t *vPlane = uPlane + (ySize / 4);
    uint8_t *uvData = data + ySize;
    for (int i = 0; i < ySize / 4; ++i) {
        uvData[i * 2] = vPlane[i];     // V 分量
        uvData[i * 2 + 1] = uPlane[i]; // U 分量
    }

    pFrame->width = srcFrame->width;
    pFrame->height = srcFrame->height;
    pFrame->frame_format = UVC_FRAME_FORMAT_NV12;
    pFrame->step = srcFrame->width * 1;
    pFrame->sequence = srcFrame->sequence;
    pFrame->capture_time = srcFrame->capture_time;
    pFrame->capture_time_finished = srcFrame->capture_time_finished;
    pFrame->source = srcFrame->source;
    return pFrame;
}