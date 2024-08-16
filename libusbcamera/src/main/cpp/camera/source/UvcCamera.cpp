//
// Created by MI T on 2024/8/1.
//

#include <stdlib.h>
#include "../UvcCamera.h"
#include "../state/CameraParameterState.h"

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
    if (ret != UVC_SUCCESS) {
        mContext = nullptr;
        return ret;
    }
    ret = uvc_wrap(fd, mContext, &mDeviceHandle);
    LOG_E("uvc设备打开结果:%d", ret);
    if (ret != UVC_SUCCESS) {
        mDeviceHandle = nullptr;
        return ret;
    }
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
    if (mContext) {
        LOG_D("uvc_exit mContext");
        uvc_exit(mContext);
        mContext = nullptr;
    }
    clearCameraParams();
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

int UvcCamera::setPreviewSize(int width, int height, int fps, bool mode) {
    int result = EXIT_FAILURE;
    if (mPreview) {
        result = mPreview->setPreviewSize(width, height, fps, mode);
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
    uvc_error_t ret = UVC_ERROR_IO;
    switch (type) {
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
        case ORIENTATION:
            if (mPreview) {
                bool result = mPreview->setDisplayOrientation(value);
                if (result)ret = UVC_SUCCESS;
            }
            break;
    }

    return ret == UVC_SUCCESS;
}

bool UvcCamera::setParameterBoolValue(int type, bool value) {
    uvc_error_t ret = UVC_ERROR_IO;
    switch (type) {
        case AUTO_EXPOSURE:
            uint8_t mode = value ? 8 : 1;
            ret = uvc_set_ae_mode(mDeviceHandle, mode);
            break;
    }
    return ret == UVC_SUCCESS;
}

bool UvcCamera::getParameterBoolValue(int type) {
    if (!mDeviceHandle)return false;
    switch (type) {
        case AUTO_EXPOSURE:
            uint8_t mode;
            uvc_error_t ret = uvc_get_ae_mode(mDeviceHandle, &mode, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                return mode == 8;
            }
            break;
    }
    return false;
}

int UvcCamera::getParameterIntValue(int type) {
    if (!mDeviceHandle)return -9999;
    uvc_error_t ret;
    int value = -9999;
    switch (type) {
        case EXPOSURE:
            uint32_t exposure;
            ret = uvc_get_exposure_abs(mDeviceHandle, &exposure, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                value = exposure;
            }
            break;
        case BRIGHTNESS:
            int16_t brightness;
            ret = uvc_get_brightness(mDeviceHandle, &brightness, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                value = brightness;
            }
            break;
        case CONTRAST:
            uint16_t contrast;
            ret = uvc_get_contrast(mDeviceHandle, &contrast, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                value = contrast;
            }
            break;
        case GAIN:
            uint16_t gain;
            ret = uvc_get_gain(mDeviceHandle, &gain, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                value = gain;
            }
            break;
        case SATURATION:
            uint16_t saturation;
            ret = uvc_get_saturation(mDeviceHandle, &saturation, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                value = saturation;
            }
            break;
        case ZOOM:
            uint16_t zoom;
            ret = uvc_get_zoom_abs(mDeviceHandle, &zoom, UVC_GET_CUR);
            if (ret == UVC_SUCCESS) {
                value = zoom;
            }
            break;
        case ORIENTATION:
            if (mPreview) {
                value = mPreview->getDisplayOrientation();
            }
            break;
    }
    return value;
}

char *UvcCamera::getSupportedSize() {
    if (!mDeviceHandle) {
        return nullptr;
    }
    if (!mDeviceHandle->info->stream_ifs) {
        return nullptr;
    }
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
    writer.StartObject();

    //循环读取数据
    uvc_streaming_interface_t *stream_if;
    DL_FOREACH(mDeviceHandle->info->stream_ifs, stream_if) {
        uvc_format_desc_t *fmt_desc;
        uvc_frame_desc_t *frame_desc;
        DL_FOREACH(stream_if->format_descs, fmt_desc) {
            switch (fmt_desc->bDescriptorSubtype) {
                case UVC_VS_FORMAT_UNCOMPRESSED:
                    writer.String("yuv_formats");
                    break;
                case UVC_VS_FORMAT_MJPEG:
                    writer.String("mjpeg_formats");
                    break;
                default:
                    continue;
            }
            writer.StartArray();
            DL_FOREACH(fmt_desc->frame_descs, frame_desc) {
                writer.StartObject();
                //width
                writer.String("width");
                writer.Uint64(frame_desc->wWidth);

                //height
                writer.String("height");
                writer.Uint64(frame_desc->wHeight);

                //fps
                writer.String("fps");
                writer.Uint64(10000000 / frame_desc->dwDefaultFrameInterval);
                writer.EndObject();
            }
            writer.EndArray();
        }
    }
    writer.EndObject();
    return strdup(buffer.GetString());
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
                         mDeviceHandle(nullptr),
                         mPreview(nullptr) {
    clearCameraParams();
}


UvcCamera::~UvcCamera() {
    disConnect();
}

void UvcCamera::clearCameraParams() {

}

void UvcCamera::setPreviewListener(JavaVM *vm, JNIEnv *env, jobject listener) {
    if (mPreview) {
        jobject framePreviewListener = env->NewGlobalRef(listener);
        mPreview->setPreviewListener(vm, env, framePreviewListener);
    }
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

bool UvcCamera::currentFrameModeIsMjpeg() {
    if (mPreview) {
        return mPreview->currentFrameModeIsMjpeg();
    }
    return false;
}

int UvcCamera::getCurrentFps() {
    if (mPreview) {
        return mPreview->getCurrentFps();
    }
    return 0;
}
