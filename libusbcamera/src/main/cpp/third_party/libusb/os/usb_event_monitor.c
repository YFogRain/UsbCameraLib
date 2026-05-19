//#include "usb_event_monitor.h"
//#include "../../libuvc/include/libuvc/libuvc.h"
//#include <libusb.h>
//#include <stdlib.h>
//#include <string.h>
//
//#define MAX_TRANSFERS 4
//#define BUF_SIZE 64
//
//typedef struct { // 这个是event事件的注册
//    libusb_device_handle *handle;
//    struct libusb_transfer *transfers[MAX_TRANSFERS];
//    uint8_t *buffers[MAX_TRANSFERS];
//    int interface_number;
//    uint8_t endpoint;
//    int running;
//} usb_event_ctx_t;
//
//
//// JNI 回调（你后面实现）
//extern void on_usb_event(uint8_t *data, int len);
//
///**
// * 自动扫描输入端口
// */
//static int find_interrupt_ep(libusb_device_handle *handle, uint8_t *ep, int *interface_number) {
//    libusb_device *dev = libusb_get_device(handle);
//    struct libusb_config_descriptor *config;
//    if (libusb_get_active_config_descriptor(dev, &config) != 0)
//        return -1;
//    for (int i = 0; i < config->bNumInterfaces; i++) {
//        const struct libusb_interface *inter = &config->interface[i];
//        for (int j = 0; j < inter->num_altsetting; j++) {
//            const struct libusb_interface_descriptor *alt = &inter->altsetting[j];
//            for (int k = 0; k < alt->bNumEndpoints; k++) {
//                const struct libusb_endpoint_descriptor *endpoint = &alt->endpoint[k];
//                if ((endpoint->bmAttributes & LIBUSB_TRANSFER_TYPE_MASK)
//                    == LIBUSB_TRANSFER_TYPE_INTERRUPT &&
//                    (endpoint->bEndpointAddress & LIBUSB_ENDPOINT_IN)) {
//                    *ep = endpoint->bEndpointAddress;
//                    *interface_number = alt->bInterfaceNumber;
//                    libusb_free_config_descriptor(config);
//                    return 0;
//                }
//            }
//        }
//    }
//    libusb_free_config_descriptor(config);
//    return -1;
//}
//
///**
// * 事件解析
// */
//static void parse_usb_event(uint8_t *data, int len, usb_event_t *event) {
//    memset(event, 0, sizeof(*event));
//    memcpy(event->raw, data, len);
//    event->raw_len = len;
//    if (len < 1) {
//        event->type = USB_EVENT_TYPE_UNKNOWN;
//        return;
//    }
//    // 👉 示例：UVC button（常见格式）
//    // [0] = button state
//    if (len == 1) {
//        event->type = USB_EVENT_TYPE_UVC_BUTTON;
//        event->data.uvc_button.button_id = 0;
//        event->data.uvc_button.state = data[0] ? 1 : 0;
//        return;
//    }
//    // 👉 示例：HID report
//    if (len >= 2) {
//        event->type = USB_EVENT_TYPE_HID;
//        event->data.hid.report_id = data[0];
//        return;
//    }
//    event->type = USB_EVENT_TYPE_VENDOR;
//}
//
///**
// * interrupt 回调
// * @param transfer
// */
//static void LIBUSB_CALL interrupt_cb(struct libusb_transfer *transfer) {
//    usb_event_monitor_t *monitor =(usb_event_monitor_t *)transfer->user_data;
//    if (!monitor || !monitor->running) return;
//    if (transfer->status == LIBUSB_TRANSFER_COMPLETED &&
//        transfer->actual_length > 0) {
//        usb_event_t event;
//        parse_usb_event(transfer->buffer,
//                        transfer->actual_length,
//                        &event);
//        if (monitor->cb) {
//            monitor->cb(
//                    monitor->devh,
//                    &event,
//                    monitor->user_ptr
//            );
//        }
//    }
//    // 🔥 继续监听（关键）
//    libusb_submit_transfer(transfer);
//}
//
///**
// * 启动监听（无新线程）
// */
//int start_usb_event_monitor(uvc_device_handle_t *devh,usb_event_callback_t* cb,void *user_ptr) {
//    if (!devh || !cb) return -1;
//
//    usb_event_monitor_t *monitor =
//            (usb_event_monitor_t *)calloc(1, sizeof(*monitor));
//
//    monitor->devh = devh;
//    monitor->handle = devh->usb_devh;
//    monitor->cb = cb;
//    monitor->user_ptr = user_ptr;
//    monitor->running = 1;
//
//    if (find_interrupt_ep(monitor->handle,
//                          &monitor->endpoint,
//                          &monitor->interface_number) != 0) {
//        free(monitor);
//        return -1;
//    }
//
//    libusb_claim_interface(monitor->handle,
//                           monitor->interface_number);
//
//    monitor->buffer = (uint8_t *)malloc(64);
//    monitor->transfer = libusb_alloc_transfer(0);
//
//    libusb_fill_interrupt_transfer(
//            monitor->transfer,
//            monitor->handle,
//            monitor->endpoint,
//            monitor->buffer,
//            64,
//            interrupt_cb,
//            monitor,
//            0
//    );
//
//    libusb_submit_transfer(monitor->transfer);
//    // 👉 挂到 devh（建议你扩展结构体字段）
//    devh->event_monitor = monitor;
//
//    return 0;
//}
//
///**
// * 停止监听
// */
//void stop_usb_event_monitor() {
//    if (!g_ctx.running) return;
//    g_ctx.running = 0;
//    for (int i = 0; i < MAX_TRANSFERS; i++) {
//        if (g_ctx.transfers[i]) {
//            libusb_cancel_transfer(g_ctx.transfers[i]);
//            libusb_free_transfer(g_ctx.transfers[i]);
//            g_ctx.transfers[i] = NULL;
//        }
//        if (g_ctx.buffers[i]) {
//            free(g_ctx.buffers[i]);
//            g_ctx.buffers[i] = NULL;
//        }
//    }
//    if (g_ctx.handle) {
//        libusb_release_interface(g_ctx.handle, g_ctx.interface_number);
//    }
//    memset(&g_ctx, 0, sizeof(g_ctx));
//}