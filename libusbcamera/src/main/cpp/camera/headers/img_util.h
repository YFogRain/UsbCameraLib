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
    // 将数据转换为BGR格式
    static cv::Mat any2Bgr(uint8_t *inFrame, size_t data_size, uint32_t width, uint32_t height, int format);

    static cv::Mat format(uint8_t *inFrame, uint32_t width, uint32_t height, int outFormat);

    static bool setDisplayTransformState(ANativeWindow *window, int orientation);

    static cv::Mat rotation(uint8_t *inFrame, int width, int height, int rotation);
};

#endif // USBCAMERALIB_IMG_UTIL_H
