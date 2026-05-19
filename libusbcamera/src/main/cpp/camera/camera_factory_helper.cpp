#include "Log.h"
#include <dirent.h>
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include "camera_factory_helper.h"
#include <unistd.h>
#include <fcntl.h>
#include "libuvc/libuvc_internal.h"
#include "usb/camera_device_usb.h"
#include "v4l2/camera_device_v4l2.h"

/**
 * usb类型的打开
 * @param fd 文件描述符
 * @param busNum 设备总线
 * @param devAddress 设备地址
 * @return 打开后的对象
 */
ICameraDevice *CameraFactoryHelper::openCamera(int fd, int busNum, int devAddress) {
    if (fd == -1 || busNum == -1 || devAddress == -1) {
        return nullptr;
    }
    uvc_context_t *context = nullptr;
    uvc_error_t ret = uvc_init(&context, nullptr); // 初始化
    LOG_D("CameraFactoryHelper", "初始化uvc结果:%d", ret);
    if (ret != UVC_SUCCESS || !context) {
        return nullptr;
    }
    LOG_D("CameraFactoryHelper", "当前设备的文件描述符：%d---%d/%d", fd, busNum, devAddress);
    fd = dup(fd);
    if (fd < 0) {
        uvc_exit(context);
        return nullptr;
    }
    uvc_device_t *device = nullptr;
    ret = uvc_get_device_with_fd(context, &device, fd, busNum, devAddress); // 获取对应的文件描述符
    if (ret != UVC_SUCCESS || !device) {
        if (device) {
            uvc_unref_device(device);
        }
        close(fd);
        uvc_exit(context);
        return nullptr;
    }
    uvc_device_handle_t *deviceHandle = nullptr;
    ret = uvc_open(device, &deviceHandle, fd); // 打开设备
    LOG_D("CameraFactoryHelper", "uvc设备打开结果:%d", ret);
    if (ret != UVC_SUCCESS || !deviceHandle) {
        deviceHandle = nullptr;
        uvc_unref_device(device); // 释放device
        uvc_exit(context);        // 释放context
        close(fd);                // 释放文件描述符
        context = nullptr;
        return nullptr;
    }
    return new CameraDeviceUsbImpl(context, device, deviceHandle, fd);
}


/**
 * video类型的打开
 * @param videoPath 对应的video文件的目录地址，如 /dev/videoX
 * @return 打开后的对象
 */
ICameraDevice *CameraFactoryHelper::openCamera(const char *videoPath) {
    int fd = open(videoPath, O_RDWR);
    if (fd < 0) {
        return nullptr;
    }
    struct v4l2_capability cap{};
    if (ioctl(fd, VIDIOC_QUERYCAP, &cap) < 0 || !(cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
        LOG_E("CameraFactoryHelper", "获取摄像头数据节点信息失败");
        close(fd);
        return nullptr;
    }
    LOG_D("CameraFactoryHelper", "驱动程序的名称：%s", cap.driver);
    LOG_D("CameraFactoryHelper", "硬件设备的名称：%s", cap.card);
    LOG_D("CameraFactoryHelper", "总线信息：%s", cap.bus_info);
    LOG_D("CameraFactoryHelper", "驱动版本号：%d", cap.version);
    LOG_D("CameraFactoryHelper", "设备的能力标志：%d", cap.capabilities);
    LOG_D("CameraFactoryHelper", "设备的当前能力标志：%d", cap.device_caps);
    return new CameraDeviceV4L2Impl(fd);
}

/**
 * 关闭设备
 * @param cameraId 打开的对象id
 * @return 关闭结果
 */
bool CameraFactoryHelper::closeCamera(int64_t cameraId) {
    if (cameraId == -1 || cameraId == 0) {
        return false;
    }
    auto *camera = reinterpret_cast<ICameraDevice *>(cameraId);
    auto *stream = camera->getUserStream();
    if (stream) {
        stream->stopRecord();
        if (stream->isRunningPreview()) {
            stream->stopPreview();
        }
    }
    delete camera;
    return true;
}

std::vector<std::string> CameraFactoryHelper::loadV4L2Devices() {
    std::vector<std::string> deviceSet;
    // 打开 /dev 目录
    DIR *dir = opendir("/dev");
    if (dir == nullptr) {
        LOG_E("CameraFactoryHelper", "打开dev列表失败了");
        return deviceSet;
    }
    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        // 检查文件名是否匹配视频设备
        std::string dev_name = entry->d_name;
        LOG_D("CameraFactoryHelper", "当前的设备名称:%s", dev_name.c_str());
        if (dev_name.rfind("video", 0) == 0) { // 找到以 "video" 开头的设备文件
            std::string dev_path = "/dev/" + dev_name;
            if (isV4L2Supported(dev_path)) { // 说明当前是视频的类
                deviceSet.push_back(dev_path);
            }
        }
    }
    closedir(dir);
    return deviceSet;
}

bool CameraFactoryHelper::isV4L2Supported(const std::string &dev_name) {
    int fd = open(dev_name.c_str(), O_RDWR);
    if (fd == -1) {
        LOG_E("CameraFactoryHelper", "当前设备无法打开:%s", dev_name.c_str());
        return false;
    }
    struct v4l2_capability cap{};
    if (ioctl(fd, VIDIOC_QUERYCAP, &cap) < 0 || !(cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
        LOG_E("CameraFactoryHelper", "获取摄像头数据节点信息失败:%s", dev_name.c_str());
        close(fd);
        return false;
    }
    close(fd);
    return true;
}
