//
// Created by MI T on 2024/12/30.
//


#include "img_util.h"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "Log.h"
#include "camera_constants.h"
#include "fstream"

cv::Mat
ImgUtils::any2Bgr(uint8_t *inFrame, size_t data_size, uint32_t width, uint32_t height, int format) {
    cv::Mat outImg;
    switch (format) {
        case PREVIEW_FORMAT_YUY2:
            outImg = cv::Mat(height, width, CV_8UC3);
            cv::cvtColor(cv::Mat(height, width, CV_8UC2, inFrame), outImg, cv::COLOR_YUV2BGR_YUYV);
            break;
        case PREVIEW_FORMAT_NV12:
            outImg = cv::Mat(height, width, CV_8UC3);
            cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg,
                         cv::COLOR_YUV2BGR_NV12);
            break;
        case PREVIEW_FORMAT_NV21:
            outImg = cv::Mat(height, width, CV_8UC3);
            cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg,
                         cv::COLOR_YUV2BGR_NV21);
            break;
        case PREVIEW_FORMAT_MJPEG:
        case PREVIEW_FORMAT_JPEG:
            outImg = cv::imdecode(cv::Mat(1, data_size, CV_8UC1, inFrame), cv::IMREAD_COLOR);
            break;
        case PREVIEW_FORMAT_BGR:
            outImg = cv::Mat(height, width, CV_8UC3, inFrame);
            break;
        case PREVIEW_FORMAT_RGB:
            outImg = cv::Mat(height, width, CV_8UC3);
            cv::cvtColor(cv::Mat(height, width, CV_8UC3, inFrame), outImg, cv::COLOR_RGB2BGR);
            break;
        case PREVIEW_FORMAT_RGBA:
            outImg = cv::Mat(height, width, CV_8UC3);
            cv::cvtColor(cv::Mat(height, width, CV_8UC4, inFrame), outImg, cv::COLOR_RGBA2BGR);
            break;
    }
    return outImg;
}


std::vector<uint8_t>
ImgUtils::format(uint8_t *inFrame, uint32_t width, uint32_t height, int outFormat) {
    cv::Mat inImg(height, width, CV_8UC3, inFrame);
    cv::Mat outImg;
    switch (outFormat) {
        case PREVIEW_FORMAT_BGR:
            return std::vector<uint8_t>(inFrame, inFrame + width * height * 3);
        case PREVIEW_FORMAT_MJPEG:
        case PREVIEW_FORMAT_JPEG: {
            std::vector<uint8_t> mjpegData;
            if (!cv::imencode(".jpeg", inImg, mjpegData, {cv::IMWRITE_JPEG_QUALITY,
                                                          100})) { // 如果转码失败，则return
                // 编码失败，返回空vector
                return {};
            }
            return mjpegData;
        }
        case PREVIEW_FORMAT_RGBA:
            outImg = cv::Mat(height, width, CV_8UC4);
            cv::cvtColor(inImg, outImg, cv::COLOR_BGR2RGBA);
            break;
        case PREVIEW_FORMAT_NV21:
        case PREVIEW_FORMAT_NV12: {
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
            break;
        case PREVIEW_FORMAT_YUY2:
            outImg = cv::Mat(height, width, CV_8UC2);
            cv::cvtColor(inImg, outImg, cv::COLOR_BGR2YUV_YUYV);
            break;
        case PREVIEW_FORMAT_RGB:
            outImg = cv::Mat(height, width, CV_8UC3);
            cv::cvtColor(inImg, outImg, cv::COLOR_BGR2RGB);
            break;
    }
    if (outImg.empty()) {
        return {};
    }
    return std::vector<uint8_t>(outImg.data, outImg.data + outImg.total() * outImg.elemSize());
}

cv::Mat ImgUtils::transform(uint8_t *inFrame, int width, int height, int rotation, bool isMirror) {
    if (!inFrame || width == 0 || height == 0) {
        LOG_E("ImgUtils", "当前数据不对劲啊～");
        return {};
    }
    cv::Mat dst = cv::Mat(height, width, CV_8UC3, inFrame);
    if (rotation == 90) {
        cv::rotate(dst, dst, cv::ROTATE_90_CLOCKWISE);
    } else if (rotation == 180) {
        cv::rotate(dst, dst, cv::ROTATE_180);
    } else if (rotation == 270) {
        cv::rotate(dst, dst, cv::ROTATE_90_COUNTERCLOCKWISE);
    }
    if (isMirror) {
        cv::flip(dst, dst, 1);
    }
    return dst;
}

std::vector<uint8_t>
ImgUtils::bgr2Mjpeg(uint8_t *inFrame, int width, int height, int rotation, bool isMirror) {
    // 先旋转数据
    cv::Mat img = ImgUtils::transform(inFrame, width, height, rotation, isMirror);
    if (img.empty()) {
        return {};
    }
    // 这里转为mjpeg
    std::vector<uint8_t> mjpegData;
    std::vector<int> encodeParams = {cv::IMWRITE_JPEG_QUALITY, 100}; // 质量可调，范围0-100
    if (!cv::imencode(".jpeg", img, mjpegData, encodeParams)) {      // 如果转码失败，则return
        // 编码失败，返回空vector
        return {};
    }
    return mjpegData;
}