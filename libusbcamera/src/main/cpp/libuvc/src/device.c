/*********************************************************************
* Software License Agreement (BSD License)
*
*  Copyright (C) 2010-2012 Ken Tossell
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions
*  are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above
*     copyright notice, this list of conditions and the following
*     disclaimer in the documentation and/or other materials provided
*     with the distribution.
*   * Neither the name of the author nor other contributors may be
*     used to endorse or promote products derived from this software
*     without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
*  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
*  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
*  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
*  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
*  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
*  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
*  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
*  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
*  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
*  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*********************************************************************/
/**
 * @defgroup device Device handling and enumeration
 * @brief Support for finding, inspecting and opening UVC devices
 */

#include "libuvc/libuvc.h"
#include "libuvc/libuvc_internal.h"
#include "../../log/Log.h"

int uvc_already_open(uvc_context_t *ctx, struct libusb_device *usb_dev);

void uvc_free_devh(uvc_device_handle_t *devh);

uvc_error_t uvc_get_device_info(uvc_device_handle_t *devh, uvc_device_info_t **info);

void uvc_free_device_info(uvc_device_info_t *info);

uvc_error_t uvc_scan_control(uvc_device_handle_t *devh, uvc_device_info_t *info);

uvc_error_t uvc_parse_vc(uvc_device_t *dev,
                         uvc_device_info_t *info,
                         const unsigned char *block, size_t block_size);

uvc_error_t uvc_parse_vc_selector_unit(uvc_device_t *dev,
                                       uvc_device_info_t *info,
                                       const unsigned char *block, size_t block_size);

uvc_error_t uvc_parse_vc_extension_unit(uvc_device_t *dev,
                                        uvc_device_info_t *info,
                                        const unsigned char *block,
                                        size_t block_size);

uvc_error_t uvc_parse_vc_header(uvc_device_t *dev,
                                uvc_device_info_t *info,
                                const unsigned char *block, size_t block_size);

uvc_error_t uvc_parse_vc_input_terminal(uvc_device_t *dev,
                                        uvc_device_info_t *info,
                                        const unsigned char *block,
                                        size_t block_size);

uvc_error_t uvc_parse_vc_processing_unit(uvc_device_t *dev,
                                         uvc_device_info_t *info,
                                         const unsigned char *block,
                                         size_t block_size);

uvc_error_t uvc_scan_streaming(uvc_device_t *dev,
                               uvc_device_info_t *info,
                               int interface_idx);

uvc_error_t uvc_parse_vs(uvc_device_t *dev,
                         uvc_device_info_t *info,
                         uvc_streaming_interface_t *stream_if,
                         const unsigned char *block, size_t block_size);

uvc_error_t uvc_parse_vs_format_uncompressed(uvc_streaming_interface_t *stream_if,
                                             const unsigned char *block,
                                             size_t block_size);

uvc_error_t uvc_parse_vs_format_mjpeg(uvc_streaming_interface_t *stream_if,
                                      const unsigned char *block,
                                      size_t block_size);

uvc_error_t uvc_parse_vs_frame_uncompressed(uvc_streaming_interface_t *stream_if,
                                            const unsigned char *block,
                                            size_t block_size);

uvc_error_t uvc_parse_vs_frame_format(uvc_streaming_interface_t *stream_if,
                                      const unsigned char *block,
                                      size_t block_size);

uvc_error_t uvc_parse_vs_frame_frame(uvc_streaming_interface_t *stream_if,
                                     const unsigned char *block,
                                     size_t block_size);

uvc_error_t uvc_parse_vs_input_header(uvc_streaming_interface_t *stream_if,
                                      const unsigned char *block,
                                      size_t block_size);

void LIBUSB_CALL _uvc_status_callback(struct libusb_transfer *transfer);

/** @internal
 * @brief Test whether the specified USB device has been opened as a UVC device
 * @ingroup device
 *
 * @param ctx Context in which to search for the UVC device
 * @param usb_dev USB device to find
 * @return true if the device is open in this context
 */
int uvc_already_open(uvc_context_t *ctx, struct libusb_device *usb_dev) {
    uvc_device_handle_t *devh;

    DL_FOREACH(ctx->open_devices, devh) {
        if (usb_dev == devh->dev->usb_dev)
            return 1;
    }

    return 0;
}

/** @brief Finds a camera identified by vendor, product and/or serial number
 * @ingroup device
 *
 * @param[in] ctx UVC context in which to search for the camera
 * @param[out] dev Reference to the camera, or NULL if not found
 * @param[in] vid Vendor ID number, optional
 * @param[in] pid Product ID number, optional
 * @param[in] sn Serial number or NULL
 * @return Error finding device or UVC_SUCCESS
 */
uvc_error_t uvc_find_device(
        uvc_context_t *ctx, uvc_device_t **dev,
        int vid, int pid, const char *sn) {
    uvc_error_t ret = UVC_SUCCESS;

    uvc_device_t **list;
    uvc_device_t *test_dev;
    int dev_idx;
    int found_dev;

    UVC_ENTER();

    ret = uvc_get_device_list(ctx, &list);

    if (ret != UVC_SUCCESS) {
        UVC_EXIT(ret);
        return ret;
    }

    dev_idx = 0;
    found_dev = 0;

    while (!found_dev && (test_dev = list[dev_idx++]) != NULL) {
        uvc_device_descriptor_t *desc;

        if (uvc_get_device_descriptor(test_dev, &desc) != UVC_SUCCESS)
            continue;

        if ((!vid || desc->idVendor == vid)
            && (!pid || desc->idProduct == pid)
            && (!sn || (desc->serialNumber && !strcmp(desc->serialNumber, sn))))
            found_dev = 1;

        uvc_free_device_descriptor(desc);
    }

    if (found_dev)
        uvc_ref_device(test_dev);

    uvc_free_device_list(list, 1);

    if (found_dev) {
        *dev = test_dev;
        UVC_EXIT(UVC_SUCCESS);
        return UVC_SUCCESS;
    } else {
        UVC_EXIT(UVC_ERROR_NO_DEVICE);
        return UVC_ERROR_NO_DEVICE;
    }
}

/** @brief Finds all cameras identified by vendor, product and/or serial number
 * @ingroup device
 *
 * @param[in] ctx UVC context in which to search for the camera
 * @param[out] devs List of matching cameras
 * @param[in] vid Vendor ID number, optional
 * @param[in] pid Product ID number, optional
 * @param[in] sn Serial number or NULL
 * @return Error finding device or UVC_SUCCESS
 */
uvc_error_t uvc_find_devices(
        uvc_context_t *ctx, uvc_device_t ***devs,
        int vid, int pid, const char *sn) {
    uvc_error_t ret = UVC_SUCCESS;

    uvc_device_t **list;
    uvc_device_t *test_dev;
    int dev_idx;
    int found_dev;

    uvc_device_t **list_internal;
    int num_uvc_devices;

    UVC_ENTER();

    ret = uvc_get_device_list(ctx, &list);

    if (ret != UVC_SUCCESS) {
        UVC_EXIT(ret);
        return ret;
    }

    num_uvc_devices = 0;
    dev_idx = 0;
    found_dev = 0;

    list_internal = malloc(sizeof(*list_internal));
    *list_internal = NULL;

    while ((test_dev = list[dev_idx++]) != NULL) {
        uvc_device_descriptor_t *desc;

        if (uvc_get_device_descriptor(test_dev, &desc) != UVC_SUCCESS)
            continue;

        if ((!vid || desc->idVendor == vid)
            && (!pid || desc->idProduct == pid)
            && (!sn || (desc->serialNumber && !strcmp(desc->serialNumber, sn)))) {
            found_dev = 1;
            uvc_ref_device(test_dev);

            num_uvc_devices++;
            list_internal = realloc(list_internal, (num_uvc_devices + 1) * sizeof(*list_internal));

            list_internal[num_uvc_devices - 1] = test_dev;
            list_internal[num_uvc_devices] = NULL;
        }

        uvc_free_device_descriptor(desc);
    }

    uvc_free_device_list(list, 1);

    if (found_dev) {
        *devs = list_internal;
        UVC_EXIT(UVC_SUCCESS);
        return UVC_SUCCESS;
    } else {
        UVC_EXIT(UVC_ERROR_NO_DEVICE);
        return UVC_ERROR_NO_DEVICE;
    }
}

/** @brief Get the number of the bus to which the device is attached
 * @ingroup device
 */
uint8_t uvc_get_bus_number(uvc_device_t *dev) {
    return libusb_get_bus_number(dev->usb_dev);
}

/** @brief Get the number assigned to the device within its bus
 * @ingroup device
 */
uint8_t uvc_get_device_address(uvc_device_t *dev) {
    return libusb_get_device_address(dev->usb_dev);
}

static uvc_error_t uvc_open_internal(uvc_device_t *dev, struct libusb_device_handle *usb_devh,
                                     uvc_device_handle_t **devh);

#if LIBUSB_API_VERSION >= 0x01000107

/** @brief Wrap a platform-specific system device handle and obtain a UVC device handle.
 * The handle allows you to use libusb to perform I/O on the device in question.
 *
 * On Linux, the system device handle must be a valid file descriptor opened on the device node.
 *
 * The system device handle must remain open until uvc_close() is called. The system device handle will not be closed by uvc_close().
 * @ingroup device
 *
 * @param sys_dev the platform-specific system device handle
 * @param context UVC context to prepare the device
 * @param[out] devh Handle on opened device
 * @return Error opening device or SUCCESS
 */
uvc_error_t uvc_wrap(
        int sys_dev,
        uvc_context_t *context,
        uvc_device_handle_t **devh) {
    uvc_error_t ret;
    struct libusb_device_handle *usb_devh;

    UVC_ENTER();

    uvc_device_t *dev = NULL;
    int err = libusb_wrap_sys_device(context->usb_ctx, sys_dev, &usb_devh);
    UVC_DEBUG("libusb_wrap_sys_device() = %d", err);
    if (err != LIBUSB_SUCCESS) {
        UVC_EXIT(err);
        return err;
    }

    dev = calloc(1, sizeof(uvc_device_t));
    dev->ctx = context;
    dev->usb_dev = libusb_get_device(usb_devh);

    ret = uvc_open_internal(dev, usb_devh, devh);
    UVC_EXIT(ret);
    return ret;
}

#endif

/** @brief Open a UVC device
 * @ingroup device
 *
 * @param dev Device to open
 * @param[out] devh Handle on opened device
 * @return Error opening device or SUCCESS
 */
uvc_error_t uvc_open(
        uvc_device_t *dev,
        uvc_device_handle_t **devh) {
    uvc_error_t ret;
    struct libusb_device_handle *usb_devh;
    ret = libusb_open(dev->usb_dev, &usb_devh);
    LOG_E("libusb_open:%d", ret);
    if (ret != UVC_SUCCESS) {
        return ret;
    }
    ret = uvc_open_internal(dev, usb_devh, devh);
    LOG_E("uvc_open_internal:%d", ret);
    return ret;
}

static uvc_error_t uvc_open_internal(
        uvc_device_t *dev,
        struct libusb_device_handle *usb_devh,
        uvc_device_handle_t **devh) {
    uvc_error_t ret;
    uvc_device_handle_t *internal_devh;
    struct libusb_device_descriptor desc;
    UVC_ENTER();
    uvc_ref_device(dev);//增加设备 dev 的引用计数，确保设备在使用过程中不会被销毁
    //使用 calloc 为 internal_devh 分配内存，大小是 internal_devh 结构体的大小
    internal_devh = calloc(1, sizeof(*internal_devh));
    //分配内部设备句柄
    internal_devh->dev = dev;
    internal_devh->usb_devh = usb_devh;
    //获取设备信息
    ret = uvc_get_device_info(internal_devh, &(internal_devh->info));
    //如果设备信息获取失败
    if (ret != UVC_SUCCESS)
        goto fail;
    //声明控制接口
    ret = uvc_claim_if(internal_devh, internal_devh->info->ctrl_if.bInterfaceNumber);
    if (ret != UVC_SUCCESS)
        goto fail;
    //检查是否为 iSight 设备
    libusb_get_device_descriptor(dev->usb_dev, &desc);//获取设备描述符（包括厂商 ID 和产品 ID）
    internal_devh->is_isight = (desc.idVendor == 0x05ac &&
                                desc.idProduct == 0x8501);//检查设备是否是 Apple 的 iSight 摄像头
    //处理状态中断传输
    LOG_D("是否存在中断传输断点-bEndpointAddress:%d",internal_devh->info->ctrl_if.bEndpointAddress);
    if (internal_devh->info->ctrl_if.bEndpointAddress) {//如果设备的控制接口有中断端点
        internal_devh->status_xfer = libusb_alloc_transfer(0);
        if (!internal_devh->status_xfer) {//
            ret = UVC_ERROR_NO_MEM;
            goto fail;
        }
        //填充中断传输的参数，设置回调函数 _uvc_status_callback 来处理接收到的数据。
        libusb_fill_interrupt_transfer(internal_devh->status_xfer,
                                       usb_devh,
                                       internal_devh->info->ctrl_if.bEndpointAddress,
                                       internal_devh->status_buf,
                                       sizeof(internal_devh->status_buf),
                                       _uvc_status_callback,
                                       internal_devh,
                                       0);
        //提交传输请求
        ret = libusb_submit_transfer(internal_devh->status_xfer);
        if (ret) {
            fprintf(stderr,
                    "uvc: device has a status interrupt endpoint, but unable to read from it\n");
            goto fail;
        }
    }
    //启动事件处理线程
    if (dev->ctx->own_usb_ctx && dev->ctx->open_devices == NULL) {
        //如果这是设备上下文中的第一个设备，并且设备上下文的 open_devices 列表为空，则启动事件处理线程。这个线程用于处理设备事件，如插拔事件。
        uvc_start_handler_thread(dev->ctx);
    }
    //添加设备到已打开设备列表
    DL_APPEND(dev->ctx->open_devices, internal_devh);
    //返回设备句柄给调用者
    *devh = internal_devh;
    UVC_EXIT(ret);
    return ret;

    fail:
    if (internal_devh->info) {
        //释放控制接口
        uvc_release_if(internal_devh, internal_devh->info->ctrl_if.bInterfaceNumber);
    }
    libusb_close(usb_devh); //关闭 USB 设备句柄
    uvc_unref_device(dev);//释放设备引用
    uvc_free_devh(internal_devh);//释放设备句柄内存（
    UVC_EXIT(ret);
    return ret;
}

/**
 * @internal
 * @brief Parses the complete device descriptor for a device
 * @ingroup device
 * @note Free *info with uvc_free_device_info when you're done
 *
 * @param dev Device to parse descriptor for
 * @param info Where to store a pointer to the new info struct
 */
uvc_error_t uvc_get_device_info(uvc_device_handle_t *devh,
                                uvc_device_info_t **info) {
    uvc_error_t ret;
    uvc_device_info_t *internal_info;
    //使用 calloc 为 internal_info 分配内存空间
    internal_info = calloc(1, sizeof(*internal_info));
    if (!internal_info) {//内存分配失败
        return UVC_ERROR_NO_MEM;
    }
    //获取设备配置描述符
    if (libusb_get_config_descriptor(devh->dev->usb_dev, 0, &(internal_info->config)) != 0) {
        free(internal_info);//获取失败，则释放已分配的内存并返回
        return UVC_ERROR_IO;
    }
    //扫描控制接口
    ret = uvc_scan_control(devh, internal_info);
    if (ret != UVC_SUCCESS) {//调用 uvc_scan_control 扫描设备的控制接口
        uvc_free_device_info(internal_info);
        return ret;
    }
    *info = internal_info;
    return ret;
}

/**
 * @internal
 * @brief Frees the device descriptor for a device
 * @ingroup device
 *
 * @param info Which device info block to free
 */
void uvc_free_device_info(uvc_device_info_t *info) {
    uvc_input_terminal_t *input_term, *input_term_tmp;
    uvc_processing_unit_t *proc_unit, *proc_unit_tmp;
    uvc_extension_unit_t *ext_unit, *ext_unit_tmp;

    uvc_streaming_interface_t *stream_if, *stream_if_tmp;
    uvc_format_desc_t *format, *format_tmp;
    uvc_frame_desc_t *frame, *frame_tmp;
    uvc_still_frame_desc_t *still_frame, *still_frame_tmp;
    uvc_still_frame_res_t *still_res, *still_res_tmp;

    UVC_ENTER();

    DL_FOREACH_SAFE(info->ctrl_if.input_term_descs, input_term, input_term_tmp) {
        DL_DELETE(info->ctrl_if.input_term_descs, input_term);
        free(input_term);
    }

    DL_FOREACH_SAFE(info->ctrl_if.processing_unit_descs, proc_unit, proc_unit_tmp) {
        DL_DELETE(info->ctrl_if.processing_unit_descs, proc_unit);
        free(proc_unit);
    }

    DL_FOREACH_SAFE(info->ctrl_if.extension_unit_descs, ext_unit, ext_unit_tmp) {
        DL_DELETE(info->ctrl_if.extension_unit_descs, ext_unit);
        free(ext_unit);
    }

    DL_FOREACH_SAFE(info->stream_ifs, stream_if, stream_if_tmp) {
        DL_FOREACH_SAFE(stream_if->format_descs, format, format_tmp) {
            DL_FOREACH_SAFE(format->frame_descs, frame, frame_tmp) {
                if (frame->intervals)
                    free(frame->intervals);

                DL_DELETE(format->frame_descs, frame);
                free(frame);
            }

            if (format->still_frame_desc) {
                DL_FOREACH_SAFE(format->still_frame_desc, still_frame, still_frame_tmp) {
                    DL_FOREACH_SAFE(still_frame->imageSizePatterns, still_res, still_res_tmp) {
                        free(still_res);
                    }

                    if (still_frame->bCompression) {
                        free(still_frame->bCompression);
                    }
                    free(still_frame);
                }
            }

            DL_DELETE(stream_if->format_descs, format);
            free(format);
        }

        DL_DELETE(info->stream_ifs, stream_if);
        free(stream_if);
    }

    if (info->config)
        libusb_free_config_descriptor(info->config);

    free(info);

    UVC_EXIT_VOID();
}

static uvc_error_t get_device_descriptor(
        uvc_device_handle_t *devh,
        uvc_device_descriptor_t **desc) {
    uvc_device_descriptor_t *desc_internal;
    struct libusb_device_descriptor usb_desc;
    struct libusb_device_handle *usb_devh = devh->usb_devh;
    uvc_error_t ret;

    UVC_ENTER();

    ret = libusb_get_device_descriptor(devh->dev->usb_dev, &usb_desc);

    if (ret != UVC_SUCCESS) {
        UVC_EXIT(ret);
        return ret;
    }

    desc_internal = calloc(1, sizeof(*desc_internal));
    desc_internal->idVendor = usb_desc.idVendor;
    desc_internal->idProduct = usb_desc.idProduct;

    unsigned char buf[64];

    int bytes = libusb_get_string_descriptor_ascii(
            usb_devh, usb_desc.iSerialNumber, buf, sizeof(buf));

    if (bytes > 0)
        desc_internal->serialNumber = strdup((const char *) buf);

    bytes = libusb_get_string_descriptor_ascii(
            usb_devh, usb_desc.iManufacturer, buf, sizeof(buf));

    if (bytes > 0)
        desc_internal->manufacturer = strdup((const char *) buf);

    bytes = libusb_get_string_descriptor_ascii(
            usb_devh, usb_desc.iProduct, buf, sizeof(buf));

    if (bytes > 0)
        desc_internal->product = strdup((const char *) buf);

    *desc = desc_internal;

    UVC_EXIT(ret);
    return ret;
}

/**
 * @brief Get a descriptor that contains the general information about
 * a device
 * @ingroup device
 *
 * Free *desc with uvc_free_device_descriptor when you're done.
 *
 * @param dev Device to fetch information about
 * @param[out] desc Descriptor structure
 * @return Error if unable to fetch information, else SUCCESS
 */
uvc_error_t uvc_get_device_descriptor(
        uvc_device_t *dev,
        uvc_device_descriptor_t **desc) {
    uvc_device_descriptor_t *desc_internal;
    struct libusb_device_descriptor usb_desc;
    struct libusb_device_handle *usb_devh;
    uvc_error_t ret;

    UVC_ENTER();

    ret = libusb_get_device_descriptor(dev->usb_dev, &usb_desc);

    if (ret != UVC_SUCCESS) {
        UVC_EXIT(ret);
        return ret;
    }

    desc_internal = calloc(1, sizeof(*desc_internal));
    desc_internal->idVendor = usb_desc.idVendor;
    desc_internal->idProduct = usb_desc.idProduct;

    if (libusb_open(dev->usb_dev, &usb_devh) == 0) {
        unsigned char buf[64];

        int bytes = libusb_get_string_descriptor_ascii(
                usb_devh, usb_desc.iSerialNumber, buf, sizeof(buf));

        if (bytes > 0)
            desc_internal->serialNumber = strdup((const char *) buf);

        bytes = libusb_get_string_descriptor_ascii(
                usb_devh, usb_desc.iManufacturer, buf, sizeof(buf));

        if (bytes > 0)
            desc_internal->manufacturer = strdup((const char *) buf);

        bytes = libusb_get_string_descriptor_ascii(
                usb_devh, usb_desc.iProduct, buf, sizeof(buf));

        if (bytes > 0)
            desc_internal->product = strdup((const char *) buf);

        libusb_close(usb_devh);
    } else {
        UVC_DEBUG("can't open device %04x:%04x, not fetching serial etc.",
                  usb_desc.idVendor, usb_desc.idProduct);
    }

    *desc = desc_internal;

    UVC_EXIT(ret);
    return ret;
}

/**
 * @brief Frees a device descriptor created with uvc_get_device_descriptor
 * @ingroup device
 *
 * @param desc Descriptor to free
 */
void uvc_free_device_descriptor(
        uvc_device_descriptor_t *desc) {
    UVC_ENTER();

    if (desc->serialNumber)
        free((void *) desc->serialNumber);

    if (desc->manufacturer)
        free((void *) desc->manufacturer);

    if (desc->product)
        free((void *) desc->product);

    free(desc);

    UVC_EXIT_VOID();
}

/**
 * @brief Get a list of the UVC devices attached to the system
 * @ingroup device
 *
 * @note Free the list with uvc_free_device_list when you're done.
 *
 * @param ctx UVC context in which to list devices
 * @param list List of uvc_device structures
 * @return Error if unable to list devices, else SUCCESS
 */
uvc_error_t uvc_get_device_list(
        uvc_context_t *ctx,
        uvc_device_t ***list) {
    struct libusb_device **usb_dev_list;
    struct libusb_device *usb_dev;
    int num_usb_devices;

    uvc_device_t **list_internal;
    int num_uvc_devices;

    /* per device */
    int dev_idx;
    struct libusb_config_descriptor *config;
    struct libusb_device_descriptor desc;
    uint8_t got_interface;

    /* per interface */
    int interface_idx;
    const struct libusb_interface *interface;

    /* per altsetting */
    int altsetting_idx;
    const struct libusb_interface_descriptor *if_desc;

    UVC_ENTER();

    num_usb_devices = libusb_get_device_list(ctx->usb_ctx, &usb_dev_list);

    if (num_usb_devices < 0) {
        UVC_EXIT(UVC_ERROR_IO);
        return UVC_ERROR_IO;
    }

    list_internal = malloc(sizeof(*list_internal));
    *list_internal = NULL;

    num_uvc_devices = 0;
    dev_idx = -1;

    while ((usb_dev = usb_dev_list[++dev_idx]) != NULL) {
        got_interface = 0;

        if (libusb_get_config_descriptor(usb_dev, 0, &config) != 0)
            continue;

        if (libusb_get_device_descriptor(usb_dev, &desc) != LIBUSB_SUCCESS)
            continue;

        for (interface_idx = 0;
             !got_interface && interface_idx < config->bNumInterfaces;
             ++interface_idx) {
            interface = &config->interface[interface_idx];

            for (altsetting_idx = 0;
                 !got_interface && altsetting_idx < interface->num_altsetting;
                 ++altsetting_idx) {
                if_desc = &interface->altsetting[altsetting_idx];

                // Skip TIS cameras that definitely aren't UVC even though they might
                // look that way

                if (0x199e == desc.idVendor && desc.idProduct >= 0x8201 &&
                    desc.idProduct <= 0x8208) {
                    continue;
                }

                // Special case for Imaging Source cameras
                /* Video, Streaming */
                if (0x199e == desc.idVendor && (0x8101 == desc.idProduct ||
                                                0x8102 == desc.idProduct) &&
                    if_desc->bInterfaceClass == 255 &&
                    if_desc->bInterfaceSubClass == 2) {
                    got_interface = 1;
                }

                /* Video, Streaming */
                if (if_desc->bInterfaceClass == 14 && if_desc->bInterfaceSubClass == 2) {
                    got_interface = 1;
                }
            }
        }

        libusb_free_config_descriptor(config);

        if (got_interface) {
            uvc_device_t *uvc_dev = malloc(sizeof(*uvc_dev));
            uvc_dev->ctx = ctx;
            uvc_dev->ref = 0;
            uvc_dev->usb_dev = usb_dev;
            uvc_ref_device(uvc_dev);

            num_uvc_devices++;
            list_internal = realloc(list_internal, (num_uvc_devices + 1) * sizeof(*list_internal));

            list_internal[num_uvc_devices - 1] = uvc_dev;
            list_internal[num_uvc_devices] = NULL;

            UVC_DEBUG("    UVC: %d", dev_idx);
        } else {
            UVC_DEBUG("non-UVC: %d", dev_idx);
        }
    }

    libusb_free_device_list(usb_dev_list, 1);

    *list = list_internal;

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/**
 * @brief Frees a list of device structures created with uvc_get_device_list.
 * @ingroup device
 *
 * @param list Device list to free
 * @param unref_devices Decrement the reference counter for each device
 * in the list, and destroy any entries that end up with zero references
 */
void uvc_free_device_list(uvc_device_t **list, uint8_t unref_devices) {
    uvc_device_t *dev;
    int dev_idx = 0;

    UVC_ENTER();

    if (unref_devices) {
        while ((dev = list[dev_idx++]) != NULL) {
            uvc_unref_device(dev);
        }
    }

    free(list);

    UVC_EXIT_VOID();
}

/**
 * @brief Get the uvc_device_t corresponding to an open device
 * @ingroup device
 *
 * @note Unref the uvc_device_t when you're done with it
 *
 * @param devh Device handle to an open UVC device
 */
uvc_device_t *uvc_get_device(uvc_device_handle_t *devh) {
    uvc_ref_device(devh->dev);
    return devh->dev;
}

/**
 * @brief Get the underlying libusb device handle for an open device
 * @ingroup device
 *
 * This can be used to access other interfaces on the same device, e.g.
 * a webcam microphone.
 *
 * @note The libusb device handle is only valid while the UVC device is open;
 * it will be invalidated upon calling uvc_close.
 *
 * @param devh UVC device handle to an open device
 */
libusb_device_handle *uvc_get_libusb_handle(uvc_device_handle_t *devh) {
    return devh->usb_devh;
}

/**
 * @brief Get camera terminal descriptor for the open device.
 *
 * @note Do not modify the returned structure.
 * @note The returned structure is part of a linked list, but iterating through
 * it will make it no longer the camera terminal
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_input_terminal_t *uvc_get_camera_terminal(uvc_device_handle_t *devh) {
    const uvc_input_terminal_t *term = uvc_get_input_terminals(devh);
    while (term != NULL) {
        if (term->wTerminalType == UVC_ITT_CAMERA) {
            break;
        } else {
            term = term->next;
        }
    }
    return term;
}


/**
 * @brief Get input terminal descriptors for the open device.
 *
 * @note Do not modify the returned structure.
 * @note The returned structure is part of a linked list. Iterate through
 *       it by using the 'next' pointers.
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_input_terminal_t *uvc_get_input_terminals(uvc_device_handle_t *devh) {
    return devh->info->ctrl_if.input_term_descs;
}

/**
 * @brief Get output terminal descriptors for the open device.
 *
 * @note Do not modify the returned structure.
 * @note The returned structure is part of a linked list. Iterate through
 *       it by using the 'next' pointers.
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_output_terminal_t *uvc_get_output_terminals(uvc_device_handle_t *devh) {
    return NULL; /* @todo */
}

/**
 * @brief Get selector unit descriptors for the open device.
 *
 * @note Do not modify the returned structure.
 * @note The returned structure is part of a linked list. Iterate through
 *       it by using the 'next' pointers.
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_selector_unit_t *uvc_get_selector_units(uvc_device_handle_t *devh) {
    return devh->info->ctrl_if.selector_unit_descs;
}

/**
 * @brief Get processing unit descriptors for the open device.
 *
 * @note Do not modify the returned structure.
 * @note The returned structure is part of a linked list. Iterate through
 *       it by using the 'next' pointers.
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_processing_unit_t *uvc_get_processing_units(uvc_device_handle_t *devh) {
    return devh->info->ctrl_if.processing_unit_descs;
}

/**
 * @brief Get extension unit descriptors for the open device.
 *
 * @note Do not modify the returned structure.
 * @note The returned structure is part of a linked list. Iterate through
 *       it by using the 'next' pointers.
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_extension_unit_t *uvc_get_extension_units(uvc_device_handle_t *devh) {
    return devh->info->ctrl_if.extension_unit_descs;
}

/**
 * @brief Increment the reference count for a device
 * @ingroup device
 *
 * @param dev Device to reference
 */
void uvc_ref_device(uvc_device_t *dev) {
    UVC_ENTER();

    dev->ref++;
    libusb_ref_device(dev->usb_dev);

    UVC_EXIT_VOID();
}

/**
 * @brief Decrement the reference count for a device
 * @ingropu device
 * @note If the count reaches zero, the device will be discarded
 *
 * @param dev Device to unreference
 */
void uvc_unref_device(uvc_device_t *dev) {
    UVC_ENTER();

    libusb_unref_device(dev->usb_dev);
    dev->ref--;

    if (dev->ref == 0)
        free(dev);

    UVC_EXIT_VOID();
}

/** @internal
 * Claim a UVC interface, detaching the kernel driver if necessary.
 * @ingroup device
 *
 * @param devh UVC device handle
 * @param idx UVC interface index
 */
uvc_error_t uvc_claim_if(uvc_device_handle_t *devh, int idx) {
    int ret = UVC_SUCCESS;

    UVC_ENTER();

    if (devh->claimed & (1 << idx)) {
        UVC_DEBUG("attempt to claim already-claimed interface %d\n", idx);
        UVC_EXIT(ret);
        return ret;
    }

    /* Tell libusb to detach any active kernel drivers. libusb will keep track of whether
     * it found a kernel driver for this interface. */
    ret = libusb_detach_kernel_driver(devh->usb_devh, idx);

    if (ret == UVC_SUCCESS || ret == LIBUSB_ERROR_NOT_FOUND || ret == LIBUSB_ERROR_NOT_SUPPORTED) {
        UVC_DEBUG("claiming interface %d", idx);
        if (!(ret = libusb_claim_interface(devh->usb_devh, idx))) {
            devh->claimed |= (1 << idx);
        }
    } else {
        UVC_DEBUG("not claiming interface %d: unable to detach kernel driver (%s)",
                  idx, uvc_strerror(ret));
    }

    UVC_EXIT(ret);
    return ret;
}

/** @internal
 * Release a UVC interface.
 * @ingroup device
 *
 * @param devh UVC device handle
 * @param idx UVC interface index
 */
uvc_error_t uvc_release_if(uvc_device_handle_t *devh, int idx) {
    int ret = UVC_SUCCESS;

    UVC_ENTER();
    UVC_DEBUG("releasing interface %d", idx);
    if (!(devh->claimed & (1 << idx))) {
        UVC_DEBUG("attempt to release unclaimed interface %d\n", idx);
        UVC_EXIT(ret);
        return ret;
    }

    /* libusb_release_interface *should* reset the alternate setting to the first available,
       but sometimes (e.g. on Darwin) it doesn't. Thus, we do it explicitly here.
       This is needed to de-initialize certain cameras. */
    libusb_set_interface_alt_setting(devh->usb_devh, idx, 0);
    ret = libusb_release_interface(devh->usb_devh, idx);

    if (UVC_SUCCESS == ret) {
        devh->claimed &= ~(1 << idx);
        /* Reattach any kernel drivers that were disabled when we claimed this interface */
        ret = libusb_attach_kernel_driver(devh->usb_devh, idx);

        if (ret == UVC_SUCCESS) {
            UVC_DEBUG("reattached kernel driver to interface %d", idx);
        } else if (ret == LIBUSB_ERROR_NOT_FOUND || ret == LIBUSB_ERROR_NOT_SUPPORTED) {
            ret = UVC_SUCCESS;  /* NOT_FOUND and NOT_SUPPORTED are OK: nothing to do */
        } else {
            UVC_DEBUG("error reattaching kernel driver to interface %d: %s",
                      idx, uvc_strerror(ret));
        }
    }

    UVC_EXIT(ret);
    return ret;
}

/** @internal
 * Find a device's VideoControl interface and process its descriptor
 * @ingroup device
 */
uvc_error_t uvc_scan_control(uvc_device_handle_t *devh, uvc_device_info_t *info) {
    const struct libusb_interface_descriptor *if_desc;
    uvc_error_t parse_ret, ret;
    int interface_idx;
    const unsigned char *buffer;
    size_t buffer_left, block_size;

    UVC_ENTER();

    ret = UVC_SUCCESS;
    if_desc = NULL;

    uvc_device_descriptor_t *dev_desc;
    int haveTISCamera = 0;
    get_device_descriptor(devh, &dev_desc);//获取设备描述符
    //检查设备是否为特定厂商和产品ID（例如 TIS 摄像头）。
    if (0x199e == dev_desc->idVendor && (0x8101 == dev_desc->idProduct ||
                                         0x8102 == dev_desc->idProduct)) {
        haveTISCamera = 1;
    }
    //释放设备描述符 dev_desc
    uvc_free_device_descriptor(dev_desc);
    //遍历接口，查找视频控制接口，如果找到符合条件的接口，跳出循环
    for (interface_idx = 0; interface_idx < info->config->bNumInterfaces; ++interface_idx) {
        if_desc = &info->config->interface[interface_idx].altsetting[0];
        //检查其接口类 (bInterfaceClass) 和接口子类 (bInterfaceSubClass) 是否符合视频控制接口的标准
        //对于 TIS 摄像头，接口类是 255，子类是 1
        if (haveTISCamera && if_desc->bInterfaceClass == 255 && if_desc->bInterfaceSubClass == 1)
            break;
        //对于其他 UVC 设备，接口类是 14（视频类），子类是 1（控制类）
        if (if_desc->bInterfaceClass == 14 && if_desc->bInterfaceSubClass == 1) // Video, Control
            break;

        if_desc = NULL;
    }
    //检查是否找到控制接口
    if (if_desc == NULL) {
        return UVC_ERROR_INVALID_DEVICE;
    }
    //设置控制接口信息
    info->ctrl_if.bInterfaceNumber = interface_idx;//将找到的控制接口的索引 (interface_idx) 存储在 info->ctrl_if.bInterfaceNumber 中
    if (if_desc->bNumEndpoints != 0) {//将第一个端点的地址保存到 info->ctrl_if.bEndpointAddress 中
        info->ctrl_if.bEndpointAddress = if_desc->endpoint[0].bEndpointAddress;
    }
    buffer = if_desc->extra;//解析控制接口的附加数据
    buffer_left = if_desc->extra_length;//数据的长度 extra_length
    //循环读取附加数据，并按照每个数据块的长度解析
    while (buffer_left >= 3) { //
        block_size = buffer[0];
        // 解析视频控制数据
        parse_ret = uvc_parse_vc(devh->dev, info, buffer, block_size);

        if (parse_ret != UVC_SUCCESS) {
            ret = parse_ret;
            break;
        }

        buffer_left -= block_size;
        buffer += block_size;
    }

    UVC_EXIT(ret);
    return ret;
}

/** @internal
 * @brief Parse a VideoControl header.
 * @ingroup device
 */
uvc_error_t uvc_parse_vc_header(uvc_device_t *dev,
                                uvc_device_info_t *info,
                                const unsigned char *block, size_t block_size) {
    size_t i;
    uvc_error_t scan_ret, ret = UVC_SUCCESS;
    info->ctrl_if.bcdUVC = SW_TO_SHORT(&block[3]);//解析 UVC 版本号
    LOG_D("获取到的uvc的版本号:%d", info->ctrl_if.bcdUVC);
    switch (info->ctrl_if.bcdUVC) {//根据 UVC 版本号设置时钟频率：
        case 0x0100:
            info->ctrl_if.dwClockFrequency = DW_TO_INT(block + 7);
            break;
        case 0x010a://
            info->ctrl_if.dwClockFrequency = DW_TO_INT(block + 7);
            break;
        case 0x0110:
            break;
        default:
            UVC_EXIT(UVC_ERROR_NOT_SUPPORTED);
            return UVC_ERROR_NOT_SUPPORTED;
    }
    //扫描视频流接口：
    for (i = 12;
         i < block_size; ++i) {//从 block 的第 12 个字节开始。uvc_scan_streaming 函数用于处理视频流相关的信息（如分辨率、格式等）
        scan_ret = uvc_scan_streaming(dev, info, block[i]);
        if (scan_ret != UVC_SUCCESS) {
            ret = scan_ret;
            break;
        }
    }

    UVC_EXIT(ret);
    return ret;
}

/** @internal
 * @brief Parse a VideoControl input terminal.
 * @ingroup device
 */
uvc_error_t uvc_parse_vc_input_terminal(uvc_device_t *dev,
                                        uvc_device_info_t *info,
                                        const unsigned char *block, size_t block_size) {
    uvc_input_terminal_t *term;
    size_t i;

    UVC_ENTER();
    //检查输入终端类型
    if (SW_TO_SHORT(&block[4]) != UVC_ITT_CAMERA) {
        //如果不是，说明该输入终端不是相机类型的终端
        return UVC_SUCCESS;
    }
    //分配内存并初始化输入终端结构体
    term = calloc(1, sizeof(*term));
    //填充输入终端结构体的字段：
    term->bTerminalID = block[3];//终端的 ID
    term->wTerminalType = SW_TO_SHORT(&block[4]);//终端类型（在本函数中检查是否为相机类型）
    term->wObjectiveFocalLengthMin = SW_TO_SHORT(&block[8]);//最小目标焦距。
    term->wObjectiveFocalLengthMax = SW_TO_SHORT(&block[10]);//最大目标焦距。
    term->wOcularFocalLength = SW_TO_SHORT(&block[12]);//眼部焦距。
    //解析控制字段
    for (i = 14 + block[14]; i >= 15; --i) {
        //block[14] 是控制字段的长度，从描述符中读取并填充 bmControls 字段。
        term->bmControls = block[i] + (term->bmControls << 8);
        LOG_D("解析控制字段：%llu", term->bmControls);
    }
    //将输入终端添加到输入终端链表
    DL_APPEND(info->ctrl_if.input_term_descs, term);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoControl processing unit.
 * @ingroup device
 */
uvc_error_t uvc_parse_vc_processing_unit(uvc_device_t *dev,
                                         uvc_device_info_t *info,
                                         const unsigned char *block, size_t block_size) {
    uvc_processing_unit_t *unit;
    size_t i;

    UVC_ENTER();

    unit = calloc(1, sizeof(*unit));
    unit->bUnitID = block[3];
    unit->bSourceID = block[4];

    for (i = 7 + block[7]; i >= 8; --i)
        unit->bmControls = block[i] + (unit->bmControls << 8);

    DL_APPEND(info->ctrl_if.processing_unit_descs, unit);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoControl selector unit.
 * @ingroup device
 */
uvc_error_t uvc_parse_vc_selector_unit(uvc_device_t *dev,
                                       uvc_device_info_t *info,
                                       const unsigned char *block, size_t block_size) {
    uvc_selector_unit_t *unit;

    UVC_ENTER();

    unit = calloc(1, sizeof(*unit));
    unit->bUnitID = block[3];

    DL_APPEND(info->ctrl_if.selector_unit_descs, unit);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoControl extension unit.
 * @ingroup device
 */
uvc_error_t uvc_parse_vc_extension_unit(uvc_device_t *dev,
                                        uvc_device_info_t *info,
                                        const unsigned char *block, size_t block_size) {
    uvc_extension_unit_t *unit = calloc(1, sizeof(*unit));
    const uint8_t *start_of_controls;
    int size_of_controls, num_in_pins;
    int i;

    UVC_ENTER();

    unit->bUnitID = block[3];
    memcpy(unit->guidExtensionCode, &block[4], 16);

    num_in_pins = block[21];
    size_of_controls = block[22 + num_in_pins];
    start_of_controls = &block[23 + num_in_pins];

    for (i = size_of_controls - 1; i >= 0; --i)
        unit->bmControls = start_of_controls[i] + (unit->bmControls << 8);

    DL_APPEND(info->ctrl_if.extension_unit_descs, unit);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * Process a single VideoControl descriptor block
 * @ingroup device
 */
uvc_error_t uvc_parse_vc(
        uvc_device_t *dev,
        uvc_device_info_t *info,
        const unsigned char *block, size_t block_size) {
    int descriptor_subtype;
    uvc_error_t ret = UVC_SUCCESS;
    //36 是 UVC 控制接口描述符的标识符
    if (block[1] != 36) { // 表示该描述符不是我们期望的 CS_INTERFACE 描述符（UVC 控制接口描述符）
        return UVC_SUCCESS; // UVC_ERROR_INVALID_DEVICE;
    }
    descriptor_subtype = block[2];//获取描述符的子类型
    switch (descriptor_subtype) {
        case UVC_VC_HEADER://解析视频控制接口头部
            ret = uvc_parse_vc_header(dev, info, block, block_size);
            break;
        case UVC_VC_INPUT_TERMINAL://解析输入终端
            ret = uvc_parse_vc_input_terminal(dev, info, block, block_size);
            break;
        case UVC_VC_OUTPUT_TERMINAL://解析输出终端
            break;
        case UVC_VC_SELECTOR_UNIT://解析选择器单元
            ret = uvc_parse_vc_selector_unit(dev, info, block, block_size);
            break;
        case UVC_VC_PROCESSING_UNIT://解析处理单元
            ret = uvc_parse_vc_processing_unit(dev, info, block, block_size);
            break;
        case UVC_VC_EXTENSION_UNIT://解析扩展单元
            ret = uvc_parse_vc_extension_unit(dev, info, block, block_size);
            break;
        default:
            ret = UVC_ERROR_INVALID_DEVICE;
    }
    return ret;
}

/** @internal
 * Process a VideoStreaming interface
 * @ingroup device
 */
uvc_error_t uvc_scan_streaming(uvc_device_t *dev,
                               uvc_device_info_t *info,
                               int interface_idx) {
    const struct libusb_interface_descriptor *if_desc;
    const unsigned char *buffer;
    size_t buffer_left, block_size;
    uvc_error_t ret, parse_ret;
    uvc_streaming_interface_t *stream_if;

    ret = UVC_SUCCESS;
    //指向当前接口的描述符（通过 interface_idx 获取）
    if_desc = &(info->config->interface[interface_idx].altsetting[0]);
    buffer = if_desc->extra;//指向接口描述符中的额外数据（通过 if_desc->extra 获取）
    buffer_left = if_desc->extra_length;//保存剩余数据的长度（if_desc->extra_length）
    stream_if = calloc(1, sizeof(*stream_if));//为流媒体接口分配内存并初始化
    stream_if->parent = info;//该流媒体接口属于给定的设备信息结构
    stream_if->bInterfaceNumber = if_desc->bInterfaceNumber;//设置流媒体接口的接口编号。
    LOG_D("设备信息解析完成-接口编号:%d", stream_if->bInterfaceNumber);
    DL_APPEND(info->stream_ifs, stream_if)//将新的流媒体接口添加到 info->stream_ifs 链表中。
    while (buffer_left >= 3) {//循环遍历剩余的附加数据块，直到没有足够的字节可供解析。
        block_size = buffer[0];//获取当前数据块的大小
        //函数来解析当前的数据块。该函数会根据数据块的内容提取视频流相关的信息（如分辨率、帧率等）
        parse_ret = uvc_parse_vs(dev, info, stream_if, buffer, block_size);
        if (parse_ret != UVC_SUCCESS) {
            ret = parse_ret;
            break;
        }
        //更新 buffer_left 和 buffer，指向下一个数据块
        buffer_left -= block_size;
        buffer += block_size;
    }
    return ret;
}

/** @internal
 * @brief Parse a VideoStreaming header block.
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_input_header(uvc_streaming_interface_t *stream_if,
                                      const unsigned char *block,
                                      size_t block_size) {
    UVC_ENTER();

    stream_if->bEndpointAddress = block[6] & 0x8f;
    stream_if->bTerminalLink = block[8];
    stream_if->bStillCaptureMethod = block[9];

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoStreaming uncompressed format block.
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_format_uncompressed(uvc_streaming_interface_t *stream_if,
                                             const unsigned char *block,
                                             size_t block_size) {
    UVC_ENTER();

    uvc_format_desc_t *format = calloc(1, sizeof(*format));

    format->parent = stream_if;
    format->bDescriptorSubtype = block[2];
    format->bFormatIndex = block[3];
    //format->bmCapabilities = block[4];
    //format->bmFlags = block[5];
    memcpy(format->guidFormat, &block[5], 16);
    format->bBitsPerPixel = block[21];
    format->bDefaultFrameIndex = block[22];
    format->bAspectRatioX = block[23];
    format->bAspectRatioY = block[24];
    format->bmInterlaceFlags = block[25];
    format->bCopyProtect = block[26];

    DL_APPEND(stream_if->format_descs, format);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoStreaming frame format block.
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_frame_format(uvc_streaming_interface_t *stream_if,
                                      const unsigned char *block,
                                      size_t block_size) {
    UVC_ENTER();

    uvc_format_desc_t *format = calloc(1, sizeof(*format));

    format->parent = stream_if;
    format->bDescriptorSubtype = block[2];
    format->bFormatIndex = block[3];
    format->bNumFrameDescriptors = block[4];
    memcpy(format->guidFormat, &block[5], 16);
    format->bBitsPerPixel = block[21];
    format->bDefaultFrameIndex = block[22];
    format->bAspectRatioX = block[23];
    format->bAspectRatioY = block[24];
    format->bmInterlaceFlags = block[25];
    format->bCopyProtect = block[26];
    format->bVariableSize = block[27];

    DL_APPEND(stream_if->format_descs, format);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoStreaming MJPEG format block.
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_format_mjpeg(uvc_streaming_interface_t *stream_if,
                                      const unsigned char *block,
                                      size_t block_size) {
    UVC_ENTER();

    uvc_format_desc_t *format = calloc(1, sizeof(*format));

    format->parent = stream_if;
    format->bDescriptorSubtype = block[2];
    format->bFormatIndex = block[3];
    memcpy(format->fourccFormat, "MJPG", 4);
    format->bmFlags = block[5];
    format->bBitsPerPixel = 0;
    format->bDefaultFrameIndex = block[6];
    format->bAspectRatioX = block[7];
    format->bAspectRatioY = block[8];
    format->bmInterlaceFlags = block[9];
    format->bCopyProtect = block[10];

    DL_APPEND(stream_if->format_descs, format);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoStreaming uncompressed frame block.
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_frame_frame(uvc_streaming_interface_t *stream_if,
                                     const unsigned char *block,
                                     size_t block_size) {
    uvc_format_desc_t *format;
    uvc_frame_desc_t *frame;

    const unsigned char *p;
    int i;

    UVC_ENTER();

    format = stream_if->format_descs->prev;
    frame = calloc(1, sizeof(*frame));

    frame->parent = format;

    frame->bDescriptorSubtype = block[2];
    frame->bFrameIndex = block[3];
    frame->bmCapabilities = block[4];
    frame->wWidth = block[5] + (block[6] << 8);
    frame->wHeight = block[7] + (block[8] << 8);
    frame->dwMinBitRate = DW_TO_INT(&block[9]);
    frame->dwMaxBitRate = DW_TO_INT(&block[13]);
    frame->dwDefaultFrameInterval = DW_TO_INT(&block[17]);
    frame->bFrameIntervalType = block[21];
    frame->dwBytesPerLine = DW_TO_INT(&block[22]);

    if (block[21] == 0) {
        frame->dwMinFrameInterval = DW_TO_INT(&block[26]);
        frame->dwMaxFrameInterval = DW_TO_INT(&block[30]);
        frame->dwFrameIntervalStep = DW_TO_INT(&block[34]);
    } else {
        frame->intervals = calloc(block[21] + 1, sizeof(frame->intervals[0]));
        p = &block[26];

        for (i = 0; i < block[21]; ++i) {
            frame->intervals[i] = DW_TO_INT(p);
            p += 4;
        }
        frame->intervals[block[21]] = 0;
    }

    DL_APPEND(format->frame_descs, frame);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoStreaming uncompressed frame block.
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_frame_uncompressed(uvc_streaming_interface_t *stream_if,
                                            const unsigned char *block,
                                            size_t block_size) {
    uvc_format_desc_t *format;
    uvc_frame_desc_t *frame;

    const unsigned char *p;
    int i;

    UVC_ENTER();

    format = stream_if->format_descs->prev;
    frame = calloc(1, sizeof(*frame));

    frame->parent = format;

    frame->bDescriptorSubtype = block[2];
    frame->bFrameIndex = block[3];
    frame->bmCapabilities = block[4];
    frame->wWidth = block[5] + (block[6] << 8);
    frame->wHeight = block[7] + (block[8] << 8);
    frame->dwMinBitRate = DW_TO_INT(&block[9]);
    frame->dwMaxBitRate = DW_TO_INT(&block[13]);
    frame->dwMaxVideoFrameBufferSize = DW_TO_INT(&block[17]);
    frame->dwDefaultFrameInterval = DW_TO_INT(&block[21]);
    frame->bFrameIntervalType = block[25];

    if (block[25] == 0) {
        frame->dwMinFrameInterval = DW_TO_INT(&block[26]);
        frame->dwMaxFrameInterval = DW_TO_INT(&block[30]);
        frame->dwFrameIntervalStep = DW_TO_INT(&block[34]);
    } else {
        frame->intervals = calloc(block[25] + 1, sizeof(frame->intervals[0]));
        p = &block[26];

        for (i = 0; i < block[25]; ++i) {
            frame->intervals[i] = DW_TO_INT(p);
            p += 4;
        }
        frame->intervals[block[25]] = 0;
    }

    DL_APPEND(format->frame_descs, frame);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * @brief Parse a VideoStreaming still iamge frame
 * @ingroup device
 */
uvc_error_t uvc_parse_vs_still_image_frame(uvc_streaming_interface_t *stream_if,
                                           const unsigned char *block,
                                           size_t block_size) {

    struct uvc_still_frame_desc *frame;
    uvc_format_desc_t *format;

    const unsigned char *p;
    int i;

    UVC_ENTER();

    format = stream_if->format_descs->prev;
    frame = calloc(1, sizeof(*frame));

    frame->parent = format;

    frame->bDescriptorSubtype = block[2];
    frame->bEndPointAddress = block[3];
    uint8_t numImageSizePatterns = block[4];

    frame->imageSizePatterns = NULL;

    p = &block[5];

    for (i = 1; i <= numImageSizePatterns; ++i) {
        uvc_still_frame_res_t *res = calloc(1, sizeof(uvc_still_frame_res_t));
        res->bResolutionIndex = i;
        res->wWidth = SW_TO_SHORT(p);
        p += 2;
        res->wHeight = SW_TO_SHORT(p);
        p += 2;

        DL_APPEND(frame->imageSizePatterns, res);
    }

    p = &block[5 + 4 * numImageSizePatterns];
    frame->bNumCompressionPattern = *p;

    if (frame->bNumCompressionPattern) {
        frame->bCompression = calloc(frame->bNumCompressionPattern, sizeof(frame->bCompression[0]));
        for (i = 0; i < frame->bNumCompressionPattern; ++i) {
            ++p;
            frame->bCompression[i] = *p;
        }
    } else {
        frame->bCompression = NULL;
    }

    DL_APPEND(format->still_frame_desc, frame);

    UVC_EXIT(UVC_SUCCESS);
    return UVC_SUCCESS;
}

/** @internal
 * Process a single VideoStreaming descriptor block
 * @ingroup device
 */
uvc_error_t uvc_parse_vs(
        uvc_device_t *dev,
        uvc_device_info_t *info,
        uvc_streaming_interface_t *stream_if,
        const unsigned char *block, size_t block_size) {
    uvc_error_t ret;
    int descriptor_subtype;

    ret = UVC_SUCCESS;
    descriptor_subtype = block[2];

    switch (descriptor_subtype) {
        case UVC_VS_INPUT_HEADER:
            ret = uvc_parse_vs_input_header(stream_if, block, block_size);
            break;
        case UVC_VS_OUTPUT_HEADER:
            UVC_DEBUG("unsupported descriptor subtype VS_OUTPUT_HEADER");
            break;
        case UVC_VS_STILL_IMAGE_FRAME:
            ret = uvc_parse_vs_still_image_frame(stream_if, block, block_size);
            break;
        case UVC_VS_FORMAT_UNCOMPRESSED:
            ret = uvc_parse_vs_format_uncompressed(stream_if, block, block_size);
            break;
        case UVC_VS_FORMAT_MJPEG:
            ret = uvc_parse_vs_format_mjpeg(stream_if, block, block_size);
            break;
        case UVC_VS_FRAME_UNCOMPRESSED:
        case UVC_VS_FRAME_MJPEG:
            ret = uvc_parse_vs_frame_uncompressed(stream_if, block, block_size);
            break;
        case UVC_VS_FORMAT_MPEG2TS:
            UVC_DEBUG("unsupported descriptor subtype VS_FORMAT_MPEG2TS");
            break;
        case UVC_VS_FORMAT_DV:
            UVC_DEBUG("unsupported descriptor subtype VS_FORMAT_DV");
            break;
        case UVC_VS_COLORFORMAT:
            UVC_DEBUG("unsupported descriptor subtype VS_COLORFORMAT");
            break;
        case UVC_VS_FORMAT_FRAME_BASED:
            ret = uvc_parse_vs_frame_format(stream_if, block, block_size);
            break;
        case UVC_VS_FRAME_FRAME_BASED:
            ret = uvc_parse_vs_frame_frame(stream_if, block, block_size);
            break;
        case UVC_VS_FORMAT_STREAM_BASED:
            UVC_DEBUG("unsupported descriptor subtype VS_FORMAT_STREAM_BASED");
            break;
        default:
            /** @todo handle JPEG and maybe still frames or even DV... */
            //UVC_DEBUG("unsupported descriptor subtype: %d",descriptor_subtype);
            break;
    }
    return ret;
}

/** @internal
 * @brief Free memory associated with a UVC device
 * @pre Streaming must be stopped, and threads must have died
 */
void uvc_free_devh(uvc_device_handle_t *devh) {
    UVC_ENTER();

    if (devh->info)
        uvc_free_device_info(devh->info);

    if (devh->status_xfer)
        libusb_free_transfer(devh->status_xfer);

    free(devh);

    UVC_EXIT_VOID();
}

/** @brief Close a device
 *
 * @ingroup device
 *
 * Ends any stream that's in progress.
 *
 * The device handle and frame structures will be invalidated.
 */
void uvc_close(uvc_device_handle_t *devh) {
    UVC_ENTER();
    uvc_context_t *ctx = devh->dev->ctx;

    if (devh->streams)
        uvc_stop_streaming(devh);

    uvc_release_if(devh, devh->info->ctrl_if.bInterfaceNumber);

    /* If we are managing the libusb context and this is the last open device,
     * then we need to cancel the handler thread. When we call libusb_close,
     * it'll cause a return from the thread's libusb_handle_events call, after
     * which the handler thread will check the flag we set and then exit. */
    if (ctx->own_usb_ctx && ctx->open_devices == devh && devh->next == NULL) {
        ctx->kill_handler_thread = 1;
        libusb_close(devh->usb_devh);
        pthread_join(ctx->handler_thread, NULL);
    } else {
        libusb_close(devh->usb_devh);
    }

    DL_DELETE(ctx->open_devices, devh);

    uvc_unref_device(devh->dev);

    uvc_free_devh(devh);

    UVC_EXIT_VOID();
}

/** @internal
 * @brief Get number of open devices
 */
size_t uvc_num_devices(uvc_context_t *ctx) {
    size_t count = 0;

    uvc_device_handle_t *devh;

    UVC_ENTER();

    DL_FOREACH(ctx->open_devices, devh) {
        count++;
    }

    UVC_EXIT((int) count);
    return count;
}

void uvc_process_control_status(uvc_device_handle_t *devh, unsigned char *data, int len) {
    enum uvc_status_class status_class;//用于标识状态更新的类别（如控制摄像头或处理单元）
    //状态更新的来源实体 ID，状态更新的功能选择器，状态更新的事件类型
    uint8_t originator = 0, selector = 0, event = 0;
    //状态属性，初始化为未知状态
    enum uvc_status_attribute attribute = UVC_STATUS_ATTRIBUTE_UNKNOWN;
    void *content = NULL;//指向状态更新的附加数据
    size_t content_len = 0;//指向状态更新的附加数据长度
    int found_entity = 0;//标记是否找到状态更新的来源实体
    struct uvc_input_terminal *input_terminal;//输入终端的描述结构。
    struct uvc_processing_unit *processing_unit;//处理单元描述结构
    LOG_E("解析VideoControl控制传输数据:%d", len);
    //检查数据合法性
    if (len < 5) {
        LOG_E("当前数据不合法，中断处理");
        return;
    }
    originator = data[1];//状态更新来源实体的 ID
    event = data[2];//状态事件类型，例如 0 表示常规更新
    selector = data[3];//功能选择器，用于进一步标识状态更新的功能

    if (originator == 0) {//设备未指定来源，可能是某些虚拟接口的状态更新，不处理
        return;
    }

    if (event != 0) {// 表示事件类型未知或不支持，目前只处理 event == 0
        return;
    }

    //检查是否是输入终端
    //遍历 input_term_descs 链表，查找是否有 bTerminalID 匹配 originator
    DL_FOREACH(devh->info->ctrl_if.input_term_descs, input_terminal) {
        //如果匹配，将 status_class 设置为 UVC_STATUS_CLASS_CONTROL_CAMERA
        if (input_terminal->bTerminalID == originator) {
            status_class = UVC_STATUS_CLASS_CONTROL_CAMERA;
            found_entity = 1;
            break;
        }
    }
    //如果不是输入终端，继续遍历 processing_unit_descs 链表，查找 bUnitID 是否匹配。
    if (!found_entity) {
        DL_FOREACH(devh->info->ctrl_if.processing_unit_descs, processing_unit) {
            //如果匹配，将 status_class 设置为 UVC_STATUS_CLASS_CONTROL_PROCESSING
            if (processing_unit->bUnitID == originator) {
                status_class = UVC_STATUS_CLASS_CONTROL_PROCESSING;
                found_entity = 1;
                break;
            }
        }
    }
    if (!found_entity) {//如果无法匹配到已知实体，则输出调试信息并退出
        return;
    }

    attribute = data[4];//第 5 个字节表示属性值，标识状态更新的具体类型
    content = data + 5;//从第 6 个字节开始是附加数据内容，
    content_len = len - 5;//长度为 len - 5

    LOG_D("Event: class=%d, event=%d, selector=%d, attribute=%d, content_len=%zd", status_class,
          event, selector, attribute, content_len);
    //如果用户已注册状态回调函数（status_cb）
    if (devh->status_cb) {
        devh->status_cb(status_class,
                        event,
                        selector,
                        attribute,
                        content, content_len,
                        devh->status_user_ptr);
    }
}

void uvc_process_streaming_status(uvc_device_handle_t *devh, unsigned char *data, int len) {
    //检查接收到的数据长度是否有效
    if (len < 3) {
        return;
    }
    //解析状态事件
    if (data[2] == 0) {//值为 0：表示 按钮事件。
        if (len < 4) {//按钮事件需要至少 4 字节的数据。如果数据不足，直接返回
            return;
        }
        if (devh->button_cb) {//用户定义了按钮事件回调（button_cb），则调用该回调
            devh->button_cb(data[1],
                            data[3],
                            devh->button_user_ptr);
        }
    } else {
        //处理流状态错误
        LOG_E("当前流状态错误：status:%d", data[2]);
    }

}

/**
 * uvc流处理
 * VideoControl：负责管理摄像头设备的整体控制，例如设备初始化、格式配置、解析能力（如分辨率和帧率）查询等
 * VideoStreaming：主要用于传输实际的视频帧数据（例如 MJPEG 或 RAW 格式）
 * @param devh
 * @param transfer
 */
void uvc_process_status_xfer(uvc_device_handle_t *devh, struct libusb_transfer *transfer) {
    LOG_D("当前传输数据的长度为::::%d", transfer->actual_length);
    //检查传输数据的长度
    if (transfer->actual_length > 0) {
        //根据传输数据的内容处理
        switch (transfer->buffer[0] & 0x0f) {
            case 1: //这是来自 VideoControl 接口的状态数据
                uvc_process_control_status(devh, transfer->buffer, transfer->actual_length);
                break;
            case 2:  //这是来自 VideoStreaming 接口的状态数据
                uvc_process_streaming_status(devh, transfer->buffer, transfer->actual_length);
                break;
        }
    }

    UVC_EXIT_VOID();
}

/** @internal
 * @brief Process asynchronous status updates from the device.
 */
void LIBUSB_CALL _uvc_status_callback(struct libusb_transfer *transfer) {
    LOG_D("接收到控制传输数据，status:=%d", transfer->status);
    uvc_device_handle_t *devh = (uvc_device_handle_t *) transfer->user_data;
    // 判断传输的状态，并采取相应的处理
    switch (transfer->status) {
        case LIBUSB_TRANSFER_ERROR://发生错误，不再处理或重新提交传输
        case LIBUSB_TRANSFER_CANCELLED://传输被取消，忽略并返回
        case LIBUSB_TRANSFER_NO_DEVICE://设备不存在，通常是设备断开连接
            LOG_D("没有进行传输的数据信息，status:=%d", transfer->status);
            return;
        case LIBUSB_TRANSFER_COMPLETED:// 传输成功，调用 uvc_process_status_xfer 处理状态数据。
            uvc_process_status_xfer(devh, transfer);
            break;
        case LIBUSB_TRANSFER_TIMED_OUT://超时，重试传输。
        case LIBUSB_TRANSFER_STALL://传输失败，重新尝试
        case LIBUSB_TRANSFER_OVERFLOW://传输溢出，重新尝试
            LOG_D("进行传输重试，status:=%d", transfer->status);
            break;
    }

#ifdef UVC_DEBUGGING
    uvc_error_t ret =
#endif
    int ret = libusb_submit_transfer(transfer);//将传输重新提交，继续接收或发送数据
    LOG_D("提交传输结果，status:=%d", ret);
}

/** @brief Set a callback function to receive status updates
 *
 * @ingroup device
 */
void uvc_set_status_callback(uvc_device_handle_t *devh,
                             uvc_status_callback_t cb,
                             void *user_ptr) {
    UVC_ENTER();

    devh->status_cb = cb;
    devh->status_user_ptr = user_ptr;

    UVC_EXIT_VOID();
}

/** @brief Set a callback function to receive button events
 *
 * @ingroup device
 */
void uvc_set_button_callback(uvc_device_handle_t *devh,
                             uvc_button_callback_t cb,
                             void *user_ptr) {
    UVC_ENTER();

    devh->button_cb = cb;
    devh->button_user_ptr = user_ptr;

    UVC_EXIT_VOID();
}

/**
 * @brief Get format descriptions for the open device.
 *
 * @note Do not modify the returned structure.
 *
 * @param devh Device handle to an open UVC device
 */
const uvc_format_desc_t *uvc_get_format_descs(uvc_device_handle_t *devh) {
    return devh->info->stream_ifs->format_descs;
}


uvc_error_t uvc_get_device_with_fd(uvc_context_t *ctx, uvc_device_t **device, int fd, int busNum,
                                   int devAddress) {
    LOG_D("开始查找对应的fd的设备咯：%d:::%d/%d", fd, busNum, devAddress);
    struct libusb_device *usb_dev = libusb_get_device_with_fd(ctx->usb_ctx, fd, busNum, devAddress);
    if (LIKELY(usb_dev)) {
        *device = malloc(sizeof(uvc_device_t));
        (*device)->ctx = ctx;//设置为当前上下文。
        (*device)->ref = 0;//初始化为 0
        (*device)->usb_dev = usb_dev;//设置为找到的 libusb_device
        //增加设备的引用计数，确保其不会被释放
        uvc_ref_device(*device);
        return UVC_SUCCESS;
    } else {
        LOG_E("没有找到设备哦");
        *device = NULL;
        return UVC_ERROR_NO_DEVICE;
    }
}
