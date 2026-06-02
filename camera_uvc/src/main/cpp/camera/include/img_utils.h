//
// Created by MI T on 2026/5/14.
//

#ifndef USBCAMERALIB_IMG_UTILS_H
#define USBCAMERALIB_IMG_UTILS_H
#include "libuvc/libuvc.h"
#include "opencv2/core/mat.hpp"
#include <vector>

class ImageUtils{
public:
    /// 将数据转换为BGR格式
    static cv::Mat any2Bgr(uint8_t *inFrame, size_t data_size, uint32_t width, uint32_t height, int format);

    /// 将RGBA数据转换为目标输出格式
    static std::vector<uint8_t> rgbaToTarget(const uint8_t *rgba, uint32_t width, uint32_t height,
                                             int format);

};
#endif //USBCAMERALIB_IMG_UTILS_H
