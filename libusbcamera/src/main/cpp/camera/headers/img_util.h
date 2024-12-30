//
// Created by MI T on 2024/12/30.
//

#ifndef USBCAMERALIB_IMG_UTIL_H
#define USBCAMERALIB_IMG_UTIL_H

#include <stdint.h>
#include "libuvc/libuvc.h"

class ImgUtils {

public:
    static bool any2Rgba(uvc_frame_t *inFrame, uvc_frame_t *outFrame);

    //将rgba的数据流转换成对应输出的类型
    static uvc_frame_t *rgba2Nv21(uvc_frame_t *srcFrame);
};

#endif //USBCAMERALIB_IMG_UTIL_H
