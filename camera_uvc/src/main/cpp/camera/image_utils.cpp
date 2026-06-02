//
// Created by MI T on 2026/5/14.
//
#include "img_utils.h"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "Log.h"
#include "camera_constants.h"
#include "android/native_window.h"
#include "fstream"

cv::Mat ImageUtils::any2Bgr(uint8_t *inFrame, size_t data_size, uint32_t width, uint32_t height, int format) {
    cv::Mat outImg(height, width, CV_8UC3);
    switch (format) {
        case PREVIEW_FORMAT_YUY2:
            cv::cvtColor(cv::Mat(height, width, CV_8UC2, inFrame), outImg, cv::COLOR_YUV2BGR_YUYV);
            break;
        case PREVIEW_FORMAT_NV12:
            cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg,
                         cv::COLOR_YUV2BGR_NV12);
            break;
        case PREVIEW_FORMAT_NV21:
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
            cv::cvtColor(cv::Mat(height, width, CV_8UC3, inFrame), outImg, cv::COLOR_RGB2BGR);
            break;
        case PREVIEW_FORMAT_RGBA:
            cv::cvtColor(cv::Mat(height, width, CV_8UC4, inFrame), outImg, cv::COLOR_RGBA2BGR);
            break;
    }
    return outImg;
}

std::vector<uint8_t> ImageUtils::rgbaToTarget(const uint8_t *rgba, uint32_t width, uint32_t height,
                                              int format) {
    if (!rgba || width == 0 || height == 0) {
        return {};
    }
    cv::Mat rgbaMat(height, width, CV_8UC4, const_cast<uint8_t *>(rgba));
    switch (format) {
        case PREVIEW_FORMAT_RGBA: {
            return std::vector<uint8_t>(rgba, rgba + (static_cast<size_t>(width) * height * 4));
        }
        case PREVIEW_FORMAT_RGB: {
            cv::Mat rgbMat;
            cv::cvtColor(rgbaMat, rgbMat, cv::COLOR_RGBA2RGB);
            return std::vector<uint8_t>(rgbMat.data,
                                        rgbMat.data + rgbMat.total() * rgbMat.elemSize());
        }
        case PREVIEW_FORMAT_JPEG:
        case PREVIEW_FORMAT_MJPEG: {
            cv::Mat bgrMat;
            cv::cvtColor(rgbaMat, bgrMat, cv::COLOR_RGBA2BGR);
            std::vector<uint8_t> jpegBytes;
            cv::imencode(".jpg", bgrMat, jpegBytes,
                         {cv::IMWRITE_JPEG_QUALITY, 100});
            return jpegBytes;
        }
        case PREVIEW_FORMAT_YUY2: {
            // RGBA -> BGR -> YUYV(YUY2) 4:2:2 packed
            cv::Mat bgrMat;
            cv::cvtColor(rgbaMat, bgrMat, cv::COLOR_RGBA2BGR);
            cv::Mat yuyvMat(height, width, CV_8UC2);
            cv::cvtColor(bgrMat, yuyvMat, cv::COLOR_BGR2YUV_YUYV);
            return std::vector<uint8_t>(yuyvMat.data,
                                        yuyvMat.data + yuyvMat.total() * yuyvMat.elemSize());
        }
        case PREVIEW_FORMAT_NV21:
        case PREVIEW_FORMAT_NV12:
        case PREVIEW_FORMAT_YUV420SP: {
            // 统一走 RGBA -> I420 -> NV21/NV12 交错逻辑
            // NV21：V 在前、U 在后；NV12 / YUV420SP：U 在前、V 在后
            cv::Mat i420Mat;
            cv::cvtColor(rgbaMat, i420Mat, cv::COLOR_RGBA2YUV_I420);
            const size_t yPlaneSize = static_cast<size_t>(width) * height;
            const size_t uvPlaneSize = yPlaneSize / 4;
            std::vector<uint8_t> outBytes(yPlaneSize + uvPlaneSize * 2);
            const uint8_t *src = i420Mat.data;
            memcpy(outBytes.data(), src, yPlaneSize);
            const uint8_t *uPlane = src + yPlaneSize;
            const uint8_t *vPlane = uPlane + uvPlaneSize;
            uint8_t *uvOut = outBytes.data() + yPlaneSize;
            const bool vuOrder = (format == PREVIEW_FORMAT_NV21);
            for (size_t i = 0; i < uvPlaneSize; ++i) {
                if (vuOrder) {
                    *uvOut++ = vPlane[i]; // NV21: V
                    *uvOut++ = uPlane[i]; // NV21: U
                } else {
                    *uvOut++ = uPlane[i]; // NV12 / YUV420SP: U
                    *uvOut++ = vPlane[i]; // NV12 / YUV420SP: V
                }
            }
            return outBytes;
        }
        default:
            return {};
    }
}
