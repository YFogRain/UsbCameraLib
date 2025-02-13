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
    static uvc_frame_t *any2BGR(uvc_frame_t *inFrame);

    static cv::Mat any2BGR(uint8_t *inFrame, int format, int dataBytes, int width, int height);

    static cv::Mat bgr2Any(uint8_t *inFrame, int width, int height, int mode);

    static bool setDisplayTransformState(ANativeWindow *window, int orientation);


private:
    static uvc_frame_t *initOutFrame(uvc_frame_t *inFrame);
};

#endif // USBCAMERALIB_IMG_UTIL_H
