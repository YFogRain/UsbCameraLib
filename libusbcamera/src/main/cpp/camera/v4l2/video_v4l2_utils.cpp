//
// Created on 2025/1/24.
//
// Node APIs are not fully supported. To solve the compilation error of the interface cannot be found,
// please include "napi/native_api.h".
#include "video_v4l2_utils.h"
#include "camera_constants.h"
#include "linux/v4l2-subdev.h"
#include <sys/ioctl.h>
#include "cstring"
#include "Log.h"
#include <vector>

std::string VideoV4L2Utils::getSupportPreviewSize(int fd) {
    bool isHaveVideoCapture = isSupportVideoCapture(fd);
    if (!isHaveVideoCapture) {
        LOG_E("VideoV4L2Utils","当前不支持videoCapture类型，说明不是摄像头程序");
        return {};
    }
    // 创建json缓存buffer
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
    // 开始读取format的列表
    struct v4l2_fmtdesc fmt{};                // 初始化当前的码流格式对象
    memset(&fmt, 0, sizeof(fmt));           // 初始化内存空间
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE; // 设置为videoCapture类型
    fmt.index = 0;                          // 初始化index状态，确保从0开始
    writer.StartArray();                    // 开始读取列表
    // 获取对应的码流格式信息
    while (ioctl(fd, VIDIOC_ENUM_FMT, &fmt) == 0) {
        getFormatPreviewSize(fd, fmt.pixelformat, writer);
        fmt.index++;
    }
    writer.EndArray();
    return {buffer.GetString()};
}

int VideoV4L2Utils::v4l2FormatToInt(int format) {
    if (format == V4L2_PIX_FMT_YUYV) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:yuyv");
        return PREVIEW_FORMAT_YUY2;
    } else if (format == V4L2_PIX_FMT_NV12) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:nv12");
        return PREVIEW_FORMAT_NV12;
    } else if (format == V4L2_PIX_FMT_NV21) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:nv21");
        return PREVIEW_FORMAT_NV21;
    } else if (format == V4L2_PIX_FMT_MJPEG) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:mjpeg");
        return PREVIEW_FORMAT_MJPEG;
    } else if (format == V4L2_PIX_FMT_JPEG) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:jpeg");
        return PREVIEW_FORMAT_JPEG;
    } else if (format == V4L2_PIX_FMT_RGB24) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:rgb24");
        return PREVIEW_FORMAT_RGB;
    } else if (format == V4L2_PIX_FMT_BGR24) {
        LOG_D("VideoV4L2Utils","当前使用的分辨率为:bgr24");
        return PREVIEW_FORMAT_BGR;
    }
    return -1;
}

int VideoV4L2Utils::intToV4l2Format(int formatType) {
    if (formatType == PREVIEW_FORMAT_NV12) {
        return V4L2_PIX_FMT_NV12;
    } else if (formatType == PREVIEW_FORMAT_NV21) {
        return V4L2_PIX_FMT_NV21;
    } else if (formatType == PREVIEW_FORMAT_MJPEG) {
        return V4L2_PIX_FMT_MJPEG;
    } else if (formatType == PREVIEW_FORMAT_JPEG) {
        return V4L2_PIX_FMT_JPEG;
    } else if (formatType == PREVIEW_FORMAT_RGB) {
        return V4L2_PIX_FMT_RGB24;
    } else if (formatType == PREVIEW_FORMAT_BGR) {
        return V4L2_PIX_FMT_BGR24;
    }
    return V4L2_PIX_FMT_YUYV;
}

void VideoV4L2Utils::getFormatPreviewSize(int fd, int format,
                                          rapidjson::Writer<rapidjson::StringBuffer> &writer) {
    auto detailFormat = v4l2FormatToInt(format);
    if (detailFormat == -1) { // 如果是-1，则不执行
        return;
    }
    struct v4l2_frmsizeenum frmSize{};      // 获取分辨率信息存储的位置
    memset(&frmSize, 0, sizeof(frmSize)); // 初始化内存空间

    frmSize.pixel_format = format; // 赋值类型
    frmSize.index = 0;             // 初始化index状态，确保从0开始
    // 开始读取分辨率
    // 当前类型
    writer.StartObject();
    writer.String("format");
    writer.Uint64(detailFormat);

    writer.String("sizes");
    writer.StartArray();

    while (ioctl(fd, VIDIOC_ENUM_FRAMESIZES, &frmSize) == 0) {
        if (frmSize.type == V4L2_FRMSIZE_TYPE_DISCRETE) {
            LOG_D("VideoV4L2Utils","当前支持的分辨率为:%d*%d", frmSize.discrete.width, frmSize.discrete.height);
            // 设置宽高等信息
            writer.StartObject();

            // width
            writer.String("width");
            writer.Uint64(frmSize.discrete.width);

            // height
            writer.String("height");
            writer.Uint64(frmSize.discrete.height);
            writer.EndObject();
        }
        frmSize.index++;
    }
    writer.EndArray();
    writer.EndObject();
}

std::vector<int> VideoV4L2Utils::getPreviewSizeFps(int fd, int format, int width, int height) {
    struct v4l2_frmivalenum frmFps{}; // 帧率范围信息
    memset(&frmFps, 0, sizeof(frmFps));
    frmFps.index = 0;
    frmFps.width = width;
    frmFps.height = height;
    frmFps.pixel_format = format;

    std::vector<int> fpsList; // 存储帧率最大值的列表
    // 获取支持的分辨率对应的帧率范围
    while (ioctl(fd, VIDIOC_ENUM_FRAMEINTERVALS, &frmFps) == 0) {
        if (frmFps.type == V4L2_FRMIVAL_TYPE_DISCRETE) { // 固定的帧类型
            double fps = frmFps.discrete.denominator / frmFps.discrete.numerator;
            fpsList.push_back(fps);
        } else if (frmFps.type == V4L2_FRMIVAL_TYPE_STEPWISE) {
            double maxFps = frmFps.stepwise.max.denominator / frmFps.stepwise.max.numerator;
            fpsList.push_back(maxFps);
        }
        frmFps.index++;
    }
    return fpsList;
}

bool VideoV4L2Utils::isSupportVideoCapture(int fd) {
    struct v4l2_capability cap{};
    if (ioctl(fd, VIDIOC_QUERYCAP, &cap) == 0 && (cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
        return true;
    }
    return false;
}

bool VideoV4L2Utils::setStreamFps(int fd, int fps) {
    // 设置最大帧率
    struct v4l2_streamparm streamParm{};
    memset(&streamParm, 0, sizeof(streamParm));
    streamParm.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    streamParm.parm.capture.timeperframe.numerator = 1;
    streamParm.parm.capture.timeperframe.denominator = fps;
    if (ioctl(fd, VIDIOC_S_PARM, &streamParm) < 0) {
        LOG_E("CameraDeviceV4L2Impl","设置帧率失败, 错误码: %d", errno);
        return false;
    }
    LOG_D("VideoV4L2Utils","已成功设置最大帧率: %d", fps);
    return true;
}

bool VideoV4L2Utils::setStreamPreviewSize(int fd, int format, int width, int height) {
    int v4l2Format = intToV4l2Format(format); // 获取当前使用的码流格式类型

    std::vector<int> fpsList = getPreviewSizeFps(fd, format, width, height); // 获取当前支持的fps列表
    if (!fpsList.empty()) {
        auto maxElementIt = std::max_element(fpsList.begin(), fpsList.end());
        setStreamFps(fd, *maxElementIt); // 设置最大的fps支持
    }
    struct v4l2_format fmt{};
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    fmt.fmt.pix.width = width;
    fmt.fmt.pix.height = height;
    fmt.fmt.pix.pixelformat = v4l2Format; // 设置所需的像素格式
    fmt.fmt.pix.field = V4L2_FIELD_NONE;  // 通常设置为无交错字段
    if (ioctl(fd, VIDIOC_S_FMT, &fmt) < 0) {
        LOG_E("CameraDeviceV4L2Impl","设置格式失败，错误码: %d", errno);
        return false;
    }
    return true;
}

std::variant<std::monostate, std::pair<int, int>>
VideoV4L2Utils::getSupportParameter(int fd, int id) {
    struct v4l2_queryctrl qctrl{};
    memset(&qctrl, 0, sizeof(qctrl));
    qctrl.id = id;
    // 获取当前控制器支持的范围信息
    if (ioctl(fd, VIDIOC_QUERYCTRL, &qctrl) < 0) {
        LOG_E("CameraDeviceV4L2Impl","获取参数范围失败, 错误码: %d", errno);
        return std::monostate{};
    }
    LOG_D("VideoV4L2Utils","获取参数范围成功, [%d,%d],,step:%d", qctrl.minimum, qctrl.maximum, qctrl.step);
    return std::make_pair(qctrl.minimum, qctrl.maximum);
}

std::variant<std::monostate, int> VideoV4L2Utils::getParameter(int fd, int id) {
    struct v4l2_control control{};
    memset(&control, 0, sizeof(control));
    control.id = id;
    if (ioctl(fd, VIDIOC_G_CTRL, &control) < 0) {
        LOG_E("CameraDeviceV4L2Impl","获取参数失败, 错误码: %d", errno);
        return std::monostate{};
    }
    return control.value;
}

bool VideoV4L2Utils::setParameter(int fd, int id, int value) {
    struct v4l2_control control{};
    memset(&control, 0, sizeof(control));
    control.id = id;
    control.value = value;
    if (ioctl(fd, VIDIOC_S_CTRL, &control) < 0) {
        LOG_E("CameraDeviceV4L2Impl","获取参数失败, 错误码: %d", errno);
        return false;
    }
    return true;
}