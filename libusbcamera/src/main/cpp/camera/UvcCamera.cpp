//
// Created by MI T on 2024/8/1.
//

#include <stdlib.h>
#include <unistd.h>
#include "headers/UvcCamera.h"
#include "headers/CameraParameterState.h"

/**
 * 连接设备
 * @param vId  设备的vId
 * @param pId  设备的pId
 * @return  连接结果
 */
int UvcCamera::connect(int fd) {
    LOG_E("当前连接的设备:fd:%d", fd);
    //初始化uvc的context实例
    uvc_error_t ret = uvc_init(&mContext, nullptr);
    LOG_E("初始化uvc结果:%d", ret);
    if (ret != UVC_SUCCESS || !mContext) {
        mContext = nullptr;
        return ret;
    }
    LOG_D("当前设备的文件描述符：%d", fd);
    ret = uvc_wrap(fd, mContext, &mDeviceHandle);
    LOG_E("uvc设备查询结果:%d", ret);
    if (ret != UVC_SUCCESS || !mDeviceHandle) {
        mDeviceHandle = nullptr;
        return ret;
    }
    LOG_E("uvc设备打开结果:%d", ret);
    mPreview = new UvcPreview(mDeviceHandle);
    mFd = fd;
    return UVC_SUCCESS;
}

/**
 * 连接设备
 * @param busNum  设备总线
 * @param devAddress  设备地址
 * @return  连接结果
 */
int UvcCamera::connect(int fd, int busNum, int devAddress) {
    LOG_E("当前连接的设备:fd:%d", fd);
    //初始化uvc的context实例
    uvc_error_t ret = uvc_init(&mContext, nullptr);
    LOG_E("初始化uvc结果:%d", ret);
    if (ret != UVC_SUCCESS || !mContext) {
        mContext = nullptr;
        return ret;
    }
    LOG_D("当前设备的文件描述符：%d---%d/%d", fd, busNum, devAddress);
    fd = dup(fd);
    ret = uvc_get_device_with_fd(mContext, &mDevice, fd, busNum, devAddress);
    if (ret != UVC_SUCCESS) {
        close(fd);
        return ret;
    }
    ret = uvc_open(mDevice, &mDeviceHandle);
    LOG_D("uvc设备打开结果:%d", ret);
    if (ret != UVC_SUCCESS || !mDeviceHandle) {
        mDeviceHandle = nullptr;
        uvc_exit(mContext);
        mContext = nullptr;
        close(fd);
        return ret;
    }
    mFd = fd;
    LOG_D("uvc设备打开结果:%d", ret);
    mPreview = new UvcPreview(mDeviceHandle);
    return UVC_SUCCESS;
}

/**
 * 断开连接
 * @return
 */
int UvcCamera::disConnect() {
    LOG_D("断开连接开始");
    if (mPreview) {
        mPreview->stopPreview();
        SAFE_DELETE(mPreview)
    }
    if (LIKELY(mDeviceHandle)) {
        //关闭对应的设备
        LOG_D("uvc_close mDeviceHandle");
        uvc_close(mDeviceHandle);
        mDeviceHandle = nullptr;
    }
    if (mDevice) {
        uvc_unref_device(mDevice);
        mDevice = nullptr;
    }
    if (mContext) {
        LOG_D("uvc_exit mContext");
        uvc_exit(mContext);
        mContext = nullptr;
    }
    if (mFd != 0) {
        close(mFd);
        mFd = 0;
    }
    LOG_D("断开连接结束");
    return UVC_SUCCESS;

}

int UvcCamera::startPreview() {
    int result = EXIT_FAILURE;
    if (mPreview && mDeviceHandle) {
        LOG_D("startPreview");
        result = mPreview->startPreview();
    }
    LOG_D("startPreview-result:%d", result);
    return result;
}

int UvcCamera::stopPreview() {
    int result = EXIT_FAILURE;
    if (mPreview) {
        result = mPreview->stopPreview();
    }
    return result;
}

int UvcCamera::setPreviewSize(int width, int height, int format) {
    int result = EXIT_FAILURE;
    if (mPreview) {
        result = mPreview->setPreviewSize(width, height, format);
    }
    return result;
}

int UvcCamera::setPreviewDisplay(ANativeWindow *preview_window) {
    int result = EXIT_FAILURE;
    if (mPreview) {
        result = mPreview->setDisplaySurface(preview_window);
    }
    return result;
}

bool UvcCamera::setParameterIntValue(int type, int value) {
    if (!mDeviceHandle)return false;
    uvc_error_t ret = UVC_ERROR_IO;
    switch (type) {
        case AUTO_EXPOSURE:
            ret = uvc_set_ae_mode(mDeviceHandle, value == 1 ? 8 : 0);
            break;
        case EXPOSURE:
            ret = uvc_set_exposure_abs(mDeviceHandle, value);
            break;
        case BRIGHTNESS:
            ret = uvc_set_brightness(mDeviceHandle, value);
            break;
        case CONTRAST:
            ret = uvc_set_contrast(mDeviceHandle, value);
            break;
        case GAIN:
            ret = uvc_set_gain(mDeviceHandle, value);
            break;
        case SATURATION:
            ret = uvc_set_saturation(mDeviceHandle, value);
            break;
        case ZOOM:
            ret = uvc_set_zoom_abs(mDeviceHandle, value);
            break;
        case DISPLAY_TRANSFORM:
            if (mPreview) {
                bool result = mPreview->setDisplayTransformState(value);
                if (result)ret = UVC_SUCCESS;
            }
            break;
    }

    return ret == UVC_SUCCESS;
}

int UvcCamera::getParameterIntValue(int type) {
    if (!mDeviceHandle)return -9999;
    int value = -9999;
    switch (type) {
        case AUTO_EXPOSURE:
            uint8_t mode;
            if (uvc_get_ae_mode(mDeviceHandle, &mode, UVC_GET_CUR) == UVC_SUCCESS) {
                value = mode == 8 ? 1 : 0;
            } else {
                value = 0;
            }
            break;
        case EXPOSURE:
            uint32_t exposure;
            if (uvc_get_exposure_abs(mDeviceHandle, &exposure, UVC_GET_CUR) == UVC_SUCCESS) {
                value = exposure;
            }
            break;
        case BRIGHTNESS:
            int16_t brightness;
            if (uvc_get_brightness(mDeviceHandle, &brightness, UVC_GET_CUR) == UVC_SUCCESS) {
                value = brightness;
            }
            break;
        case CONTRAST:
            uint16_t contrast;
            if (uvc_get_contrast(mDeviceHandle, &contrast, UVC_GET_CUR) == UVC_SUCCESS) {
                value = contrast;
            }
            break;
        case GAIN:
            uint16_t gain;
            if (uvc_get_gain(mDeviceHandle, &gain, UVC_GET_CUR) == UVC_SUCCESS) {
                value = gain;
            }
            break;
        case SATURATION:
            uint16_t saturation;
            if (uvc_get_saturation(mDeviceHandle, &saturation, UVC_GET_CUR) == UVC_SUCCESS) {
                value = saturation;
            }
            break;
        case ZOOM:
            uint16_t zoom;
            if (uvc_get_zoom_abs(mDeviceHandle, &zoom, UVC_GET_CUR) == UVC_SUCCESS) {
                value = zoom;
            }
            break;
        case DISPLAY_TRANSFORM:
            if (mPreview) {
                value = mPreview->getDisplayTransformState();
            }
            break;
    }
    return value;
}

char *UvcCamera::getSupportedPreviewSizes() {
    if (!mDeviceHandle) {
        return nullptr;
    }
    if (!mDeviceHandle->info->stream_ifs) {
        return nullptr;
    }
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);

    writer.StartArray();
    //循环读取数据
    uvc_streaming_interface_t *stream_if;
    DL_FOREACH(mDeviceHandle->info->stream_ifs, stream_if) {
        uvc_format_desc_t *fmt_desc;
        uvc_frame_desc_t *frame_desc;
        DL_FOREACH(stream_if->format_descs, fmt_desc) {
            int formatType = getFormatType(fmt_desc->bDescriptorSubtype);
            //检查格式，如果不支持则直接跳过
            if (formatType == -1)continue;
            DL_FOREACH(fmt_desc->frame_descs, frame_desc) {
                LOG_D("=======================分辨率%d=%d*%d====================", formatType,
                      frame_desc->wWidth, frame_desc->wHeight);
                writer.StartObject();
                //width
                writer.String("width");
                writer.Uint64(frame_desc->wWidth);

                //height
                writer.String("height");
                writer.Uint64(frame_desc->wHeight);
                //当前类型
                writer.String("format");
                writer.Uint64(formatType);

                writer.EndObject();
            }
        }
    }
    writer.EndArray();
    return strdup(buffer.GetString());
}

// 根据格式描述符的子类型返回格式类型
int UvcCamera::getFormatType(uint8_t descriptorSubtype) {
    switch (descriptorSubtype) {
        case UVC_VS_FORMAT_UNCOMPRESSED:
            LOG_D("当前为YUV类型");
            return 0;
        case UVC_VS_FORMAT_MJPEG:
            LOG_D("当前为MJPEG类型");
            return 1;
        default:
            LOG_D("不支持的格式类型");
            return -1;
    }
}


std::pair<int, int> UvcCamera::getPreviewSize() {
    if (mPreview) {
        return mPreview->getPreviewSize();
    }
    return std::make_pair(0, 0);
}

bool UvcCamera::getSupportAutoExposure() {
    return true;
}

//初始化类
UvcCamera::UvcCamera() : mContext(nullptr),
                         mDeviceHandle(nullptr), mDevice(nullptr),
                         mPreview(nullptr), mFd(0) {
}


UvcCamera::~UvcCamera() {
    disConnect();
}


bool UvcCamera::setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener, int mode) {
    if (mPreview) {
        jobject framePreviewListener = env->NewGlobalRef(listener);
        return mPreview->setPreviewListener(vm, env, framePreviewListener, mode);
    }
    return false;
}

std::pair<int, int> UvcCamera::getParameterRange(int type) {
    if (!mDeviceHandle)return std::make_pair(-999, -999);
    uvc_error_t ret;
    switch (type) {
        case EXPOSURE:
            uint32_t exposureMin;
            uint32_t exposureMax;
            ret = uvc_get_exposure_abs(mDeviceHandle, &exposureMin, UVC_GET_MIN);
            if (ret == UVC_SUCCESS) {
                ret = uvc_get_exposure_abs(mDeviceHandle, &exposureMax, UVC_GET_MAX);
                if (ret == UVC_SUCCESS) {
                    return std::make_pair(exposureMin, exposureMax);
                }
            }

            break;
        case BRIGHTNESS:
            int16_t brightnessMin;
            int16_t brightnessMax;
            ret = uvc_get_brightness(mDeviceHandle, &brightnessMin, UVC_GET_MIN);
            LOG_D("brightness-最小值:%d", brightnessMin);
            if (ret == UVC_SUCCESS) {
                ret = uvc_get_brightness(mDeviceHandle, &brightnessMax, UVC_GET_MAX);
                LOG_D("brightness-最大值:%d", brightnessMax);
                if (ret == UVC_SUCCESS) {
                    return std::make_pair(brightnessMin, brightnessMax);
                }
            }
            break;
        case CONTRAST:
            uint16_t contrastMin;
            uint16_t contrastMax;
            ret = uvc_get_contrast(mDeviceHandle, &contrastMin, UVC_GET_MIN);
            if (ret == UVC_SUCCESS) {
                ret = uvc_get_contrast(mDeviceHandle, &contrastMax, UVC_GET_MAX);
                if (ret == UVC_SUCCESS) {
                    return std::make_pair(contrastMin, contrastMax);
                }
            }
            break;
        case GAIN:
            uint16_t gainMin;
            uint16_t gainMax;
            ret = uvc_get_gain(mDeviceHandle, &gainMin, UVC_GET_MIN);
            if (ret == UVC_SUCCESS) {
                ret = uvc_get_gain(mDeviceHandle, &gainMax, UVC_GET_MAX);
                if (ret == UVC_SUCCESS) {
                    return std::make_pair(gainMin, gainMax);
                }
            }
            break;
        case SATURATION:
            uint16_t saturationMin;
            uint16_t saturationMax;
            ret = uvc_get_saturation(mDeviceHandle, &saturationMin, UVC_GET_MIN);
            if (ret == UVC_SUCCESS) {
                ret = uvc_get_saturation(mDeviceHandle, &saturationMax, UVC_GET_MAX);
                if (ret == UVC_SUCCESS) {
                    return std::make_pair(saturationMin, saturationMax);
                }
            }
            break;
        case ZOOM:
            uint16_t zoomMin;
            uint16_t zoomMax;
            ret = uvc_get_zoom_abs(mDeviceHandle, &zoomMin, UVC_GET_MIN);
            if (ret == UVC_SUCCESS) {
                ret = uvc_get_zoom_abs(mDeviceHandle, &zoomMax, UVC_GET_MAX);
                if (ret == UVC_SUCCESS) {
                    return std::make_pair(zoomMin, zoomMax);
                }
            }
            break;
    }
    return std::make_pair(-999, -999);
}

int UvcCamera::loadCurrentFormat() {
    if (mPreview) {
        return mPreview->loadCurrentFormat();
    }
    return -1;
}
