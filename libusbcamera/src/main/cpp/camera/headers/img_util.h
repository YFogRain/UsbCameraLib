//
// Created by MI T on 2024/12/30.
//

#ifndef USBCAMERALIB_IMG_UTIL_H
#define USBCAMERALIB_IMG_UTIL_H

#include "UvcPreview.h"
#include "libuvc/libuvc.h"
#include "opencv2/core/mat.hpp"

class ImgUtils {

public:
    // 将数据转换为BGR格式
    static uvc_frame_t *any2BGR(uvc_frame_t *inFrame);

    static cv::Mat bgr2Any(uvc_frame_t *inFrame, int mode);


private:

    static uvc_frame_t *initOutFrame(uvc_frame_t *inFrame);
};

#endif // USBCAMERALIB_IMG_UTIL_H
