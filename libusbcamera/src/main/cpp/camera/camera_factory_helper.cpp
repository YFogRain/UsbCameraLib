#include "Log.h"
#include "i_camera_factory.h"
#include <linux/videodev2.h>
#include <sys/ioctl.h>
#include "camera_factory_helper.h"
#include <unistd.h>
#include <fcntl.h>
#include "libuvc/libuvc_internal.h"
#include "usb/camera_factory_usb_impl.h"
#include "v4l2/camera_factory_v4l2_impl.h"

/**
 * usb类型的打开
 * @param fd 文件描述符
 * @param busNum 设备总线
 * @param devAddress 设备地址
 * @return 打开后的对象
 */
ICameraFactory *CameraFactoryHelper::openCamera(int fd, int busNum, int devAddress) {
    if (fd == -1 || busNum == -1 || devAddress == -1) {
        return nullptr;
    }
    uvc_context_t *context;
    uvc_error_t ret = uvc_init(&context, nullptr); // 初始化
    LOG_D("初始化uvc结果:%d", ret);
    if (ret != UVC_SUCCESS || !context) {
        return nullptr;
    }
    LOG_D("当前设备的文件描述符：%d---%d/%d", fd, busNum, devAddress);
    fd = dup(fd);
    uvc_device_t *device;
    ret = uvc_get_device_with_fd(context, &device, fd, busNum, devAddress); // 获取对应的文件描述符
    if (ret != UVC_SUCCESS || !device) {
        close(fd);
        return nullptr;
    }
    uvc_device_handle_t *deviceHandle;
    ret = uvc_open(device, &deviceHandle); // 打开设备
    LOG_D("uvc设备打开结果:%d", ret);
    if (ret != UVC_SUCCESS || !deviceHandle) {
        deviceHandle = nullptr;
        uvc_unref_device(device); // 释放device
        uvc_exit(context);        // 释放context
        close(fd);                // 释放文件描述符
        context = nullptr;
        return nullptr;
    }
    return new CameraFactoryUsbImpl(context, device, deviceHandle, fd);
}

/**
 * video类型的打开
 * @param videoPath 对应的video文件的目录地址，如 /dev/videoX
 * @return 打开后的对象
 */
ICameraFactory *CameraFactoryHelper::openCamera(const char *videoPath) {
    int fd = open(videoPath, O_RDWR);
    if (fd < 0) {
        return nullptr;
    }
    struct v4l2_capability cap;
    if (ioctl(fd, VIDIOC_QUERYCAP, &cap) < 0 || !(cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
        LOG_E("获取摄像头数据节点信息失败");
        close(fd);
        return nullptr;
    }
    LOG_D("驱动程序的名称：%s", cap.driver);
    LOG_D("硬件设备的名称：%s", cap.card);
    LOG_D("总线信息：%s", cap.bus_info);
    LOG_D("驱动版本号：%d", cap.version);
    LOG_D("设备的能力标志：%d", cap.capabilities);
    LOG_D("设备的当前能力标志：%d", cap.device_caps);
    return new CameraFactoryV4L2Impl(fd);
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
    ICameraFactory *camera = reinterpret_cast<ICameraFactory *>(cameraId);
    if (camera->isRunningPreview()) {
        camera->stopPreview();
    }
    delete camera;
    return true;
}
