//
// Created by MI T on 2024/12/30.
//


#include "img_util.h"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "Log.h"
#include "camera_constants.h"
#include "android/native_window.h"
#include "fstream"

cv::Mat ImgUtils::any2Bgr(uint8_t *inFrame, size_t data_size, uint32_t width, uint32_t height, int format) {
    cv::Mat outImg(height, width, CV_8UC3);
    switch (format) {
        case PREVIEW_FORMAT_YUY2:
            cv::cvtColor(cv::Mat(height, width, CV_8UC2, inFrame), outImg, cv::COLOR_YUV2BGR_YUYV);
            break;
        case PREVIEW_FORMAT_NV12:
            cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg, cv::COLOR_YUV2BGR_NV12);
            break;
        case PREVIEW_FORMAT_NV21:
            cv::cvtColor(cv::Mat(height + height / 2, width, CV_8UC1, inFrame), outImg, cv::COLOR_YUV2BGR_NV21);
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


std::vector<uint8_t> ImgUtils::format(uint8_t *inFrame, uint32_t width, uint32_t height, int outFormat) {
    cv::Mat inImg(height, width, CV_8UC3, inFrame);
    cv::Mat outImg;
    switch (outFormat) {
        case PREVIEW_FORMAT_BGR:
            return std::vector<uint8_t>(inFrame, inFrame + width * height * 3);
        case PREVIEW_FORMAT_MJPEG:
        case PREVIEW_FORMAT_JPEG: {
            std::vector<uint8_t> mjpegData;
            if (!cv::imencode(".jpeg", inImg, mjpegData, {cv::IMWRITE_JPEG_QUALITY, 100})) { // 如果转码失败，则return
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
        } break;
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

cv::Mat ImgUtils::rotation(uint8_t *inFrame, int width, int height, int rotation) {
    if (!inFrame || width == 0 || height == 0) {
        LOG_E("当前数据不对劲啊～");
        return cv::Mat();
    }
    cv::Mat dst = cv::Mat(height, width, CV_8UC3, inFrame);
    // 旋转
    switch (rotation) {
        case TRANSFORM_ROTATE_90:        // 旋转90度
        case TRANSFORM_FLIP_H_ROTATE_90: // 旋转90度+水平镜像
        case TRANSFORM_FLIP_V_ROTATE_90: // 旋转90度+垂直镜像
            cv::rotate(dst, dst, cv::ROTATE_90_CLOCKWISE);
            break;
        case TRANSFORM_ROTATE_180:        // 旋转180度
        case TRANSFORM_FLIP_H_ROTATE_180: // 旋转180度+水平镜像
        case TRANSFORM_FLIP_V_ROTATE_180: // 旋转180度+垂直镜像
            cv::rotate(dst, dst, cv::ROTATE_180);
            break;
        case TRANSFORM_ROTATE_270:        // 旋转270度
        case TRANSFORM_FLIP_H_ROTATE_270: // 旋转270度+水平镜像
        case TRANSFORM_FLIP_V_ROTATE_270: // 旋转270度+垂直镜像
            cv::rotate(dst, dst, cv::ROTATE_90_COUNTERCLOCKWISE);
            break;
        default:
            break; // 不旋转
    }

    // 镜像翻转
    switch (rotation) {
        case TRANSFORM_MIRROR_HORIZONTAL: // 水平镜像
        case TRANSFORM_FLIP_H_ROTATE_90:  // 旋转90度+水平镜像
        case TRANSFORM_FLIP_H_ROTATE_180: // 旋转180度+水平镜像
        case TRANSFORM_FLIP_H_ROTATE_270: // 旋转270度+水平镜像
            cv::flip(dst, dst, 1);
            break;
        case TRANSFORM_MIRROR_VERTICAL:   // 垂直镜像
        case TRANSFORM_FLIP_V_ROTATE_90:  // 旋转90度+垂直镜像
        case TRANSFORM_FLIP_V_ROTATE_180: // 旋转180度+垂直镜像
        case TRANSFORM_FLIP_V_ROTATE_270: // 旋转270度+垂直镜像
            cv::flip(dst, dst, 0);
            break;
        default:
            break; // 不镜像
    }

    return dst;
}

std::vector<uint8_t> ImgUtils::bgr2Mjpeg(uint8_t *inFrame, int width, int height, int rotation) {
    // 先旋转数据
    cv::Mat img = ImgUtils::rotation(inFrame, width, height, rotation);
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

bool ImgUtils::writeMjpeg(uint8_t *inFrame, int width, int height, int rotation, const std::string &savePath) {
    std::vector<uint8_t> img = bgr2Mjpeg(inFrame, width, height, rotation);
    if (img.empty()) {
        return false;
    }
    // 创建并打开输出文件流，默认会创建文件（如果不存在）并清空文件内容
    std::ofstream ofs(savePath, std::ios::binary);
    if (!ofs.is_open()) {
        return false;
    }
    bool isSuccess;
    try {
        size_t totalSize = img.size();
        const size_t blockSize = 1024 * 64; // 50KB 分块写入
        size_t written = 0;
        while (written < totalSize) {
            size_t sizeToWrite = std::min(blockSize, totalSize - written);
            ofs.write(reinterpret_cast<const char *>(img.data() + written), sizeToWrite);
            if (!ofs) {
                // 写入失败，直接返回 false
                return false;
            }
            written += sizeToWrite;
        }
        ofs.flush();
        isSuccess = true;
    } catch (const std::exception &e) {
        isSuccess = false;
    }
    ofs.close();
    if (!isSuccess) {
        // 写入失败，尝试删除目标文件
        try {
            std::filesystem::remove(savePath);
        } catch (const std::exception &e) {
            // 删除失败可选择记录日志，但通常不影响流程
        }
    }
    return isSuccess;
}