//
// Created on 2025/5/19.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".

#include "camera_device_v4l2.h"
#include "unistd.h"
#include "video_v4l2_utils.h"
#include <linux/v4l2-controls.h>

CameraDeviceV4L2Impl::CameraDeviceV4L2Impl(int fd) : mVideoFd(fd) {
    // 初始化互斥锁
    mCameraStream = new CameraStreamV4l2Impl(mVideoFd);
}

CameraDeviceV4L2Impl::~CameraDeviceV4L2Impl() {
    mCameraStream->stopPreview();
    mCameraStream->releaseWindows();
    mCameraStream->releasePreviewFunc();
    delete mCameraStream;
    if (mVideoFd != -1) {
        close(mVideoFd);
    }
    mVideoFd = -1;
}

std::variant<std::monostate, std::pair<int, int>, std::string, int> CameraDeviceV4L2Impl::getSupportParameters(int type) {
    if (mVideoFd == -1) {
        return std::monostate{};
    }
    if (type == CAMERA_PARAMETER_PREVIEW_SIZE) {
        return VideoV4L2Utils::getSupportPreviewSize(mVideoFd);
    }
    int id = loadTypeToId(type);
    if (id == -1) {
        return std::monostate{};
    }
    auto result = VideoV4L2Utils::getSupportParameter(mVideoFd, id);
    if (!std::holds_alternative<std::pair<int, int>>(result)) {
        return std::monostate{};
    }
    auto autoExposurePair = std::get<std::pair<int, int>>(result); // 获取到的范围信息
    LOG_D("当前支持%d类型参数:[%d,%d]", type, autoExposurePair.first, autoExposurePair.second);
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        return autoExposurePair.second >= V4L2_EXPOSURE_MANUAL ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_FOCUS) {
        return autoExposurePair.second >= V4L2_AUTO_FOCUS_RANGE_NORMAL ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_HUE) {
        return autoExposurePair.second >= 1;
    } else if (type == CAMERA_PARAMETER_PRIVACY) {
        return autoExposurePair.second >= 1;
    } else if (type == CAMERA_PARAMETER_AUTO_WHITE_BALANCE) {
        return autoExposurePair.second >= 1;
    } else {
        return autoExposurePair;
    }
}

bool CameraDeviceV4L2Impl::setParameter(int type, int value) {
    if (mVideoFd == -1) {
        return false;
    }
    // 如果是int类型，则可以设置下面的所有参数
    if (type == CAMERA_PARAMETER_DISPLAY_TRANSFORM) {
        return mCameraStream->setDisplayTransform(value);
    }
    int id = loadTypeToId(type);
    if (id == -1) {
        return false;
    }
    return VideoV4L2Utils::setParameter(mVideoFd, id, loadValueToPutValue(type, value));
}

int CameraDeviceV4L2Impl::loadValueToPutValue(int type, int value) {
    // 如果是int类型，则可以设置下面的所有参数
    switch (type) {
    case CAMERA_PARAMETER_AUTO_EXPOSURE:
        return value == 1 ? V4L2_EXPOSURE_AUTO : V4L2_EXPOSURE_MANUAL;
    case CAMERA_PARAMETER_AUTO_HUE:
        return value == 1 ? 1 : 0;
    case CAMERA_PARAMETER_AUTO_FOCUS:
        return value == 1 ? 1 : 0;
    case CAMERA_PARAMETER_PRIVACY:
        return value == 1 ? 1 : 0;
    default:
        return value;
    }
}


std::variant<std::monostate, int, std::string> CameraDeviceV4L2Impl::getParameter(int type) {
    if (mVideoFd == -1) {
        return std::monostate{};
    }
    if (type == CAMERA_PARAMETER_PREVIEW_SIZE) {
        return mCameraStream->getCurrentPreviewSize();
    }
    if (type == CAMERA_PARAMETER_DISPLAY_TRANSFORM) {
        return mCameraStream->getDisplayTransformState();
    }
    int id = loadTypeToId(type);
    if (id == -1) {
        return std::monostate{};
    }
    auto result = VideoV4L2Utils::getParameter(mVideoFd, id);
    if (!std::holds_alternative<int>(result)) {
        return std::monostate{};
    }
    auto value = std::get<int>(result); // 获取到的范围信息
    LOG_D("当前%d参数的值为:%d", type, value);
    if (type == CAMERA_PARAMETER_AUTO_EXPOSURE) {
        return value == V4L2_EXPOSURE_AUTO ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_FOCUS) {
        return value == 1 ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_AUTO_HUE) {
        return value == 1 ? 1 : 0;
    } else if (type == CAMERA_PARAMETER_PRIVACY) {
        return value == 1 ? 1 : 0;
    } else {
        return value;
    }
}

int CameraDeviceV4L2Impl::loadTypeToId(int type) {
    int id = -1;
    switch (type) {
    case CAMERA_PARAMETER_AUTO_EXPOSURE:
        id = V4L2_CID_EXPOSURE_AUTO;
        break;
    case CAMERA_PARAMETER_EXPOSURE:
        id = V4L2_CID_EXPOSURE_ABSOLUTE;
        break;
    case CAMERA_PARAMETER_BRIGHTNESS:
        id = V4L2_CID_BRIGHTNESS;
        break;
    case CAMERA_PARAMETER_CONTRAST:
        id = V4L2_CID_CONTRAST;
        break;
    case CAMERA_PARAMETER_SATURATION:
        id = V4L2_CID_SATURATION;
        break;
    case CAMERA_PARAMETER_GAIN:
        id = V4L2_CID_GAIN;
        break;
    case CAMERA_PARAMETER_ZOOM:
        id = V4L2_CID_ZOOM_ABSOLUTE;
        break;
    case CAMERA_PARAMETER_AUTO_FOCUS:
        id = V4L2_CID_FOCUS_AUTO;
        break;
    case CAMERA_PARAMETER_FOCUS:
        id = V4L2_CID_FOCUS_ABSOLUTE;
        break;
    case CAMERA_PARAMETER_IRIS: // 光圈
        id = V4L2_CID_IRIS_ABSOLUTE;
        break;
    case CAMERA_PARAMETER_AUTO_HUE: // 自动变化色调
        id = V4L2_CID_HUE_AUTO;
        break;
    case CAMERA_PARAMETER_HUE: // 色调
        id = V4L2_CID_HUE;
        break;
    case CAMERA_PARAMETER_WHITE_BALANCE: // 白平衡
        id = V4L2_CID_WHITE_BALANCE_TEMPERATURE;
        break;
    case CAMERA_PARAMETER_SCENE_MODE: // 场景模式
        id = V4L2_CID_SCENE_MODE;
        break;
    case CAMERA_PARAMETER_PRIVACY: // 隐私模式
        id = V4L2_CID_PRIVACY;
        break;
    }
    return id;
}