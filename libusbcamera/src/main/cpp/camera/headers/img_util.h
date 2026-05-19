//
// Created by MI T on 2024/12/30.
//

#ifndef USBCAMERALIB_IMG_UTIL_H
#define USBCAMERALIB_IMG_UTIL_H

#include "libuvc/libuvc.h"
#include "opencv2/core/mat.hpp"
#include "i_camera_factory.h"

class ImgUtils {

public:
    /// 将数据转换为BGR格式
    static cv::Mat any2Bgr(uint8_t *inFrame, size_t data_size, uint32_t width, uint32_t height, int format);

    static std::vector<uint8_t> format(uint8_t *inFrame, uint32_t width, uint32_t height, int outFormat);

    /// 图像变化处理（旋转/镜像）
    static cv::Mat transform(uint8_t *inFrame, int width, int height, int rotation,bool isMirror);

    /// bgr转jpeg格式数据，支持镜像，旋转处理
    static std::vector<uint8_t> bgr2Mjpeg(uint8_t *inFrame,  int width, int height, int rotation,bool isMirror);

};

#endif // USBCAMERALIB_IMG_UTIL_H
