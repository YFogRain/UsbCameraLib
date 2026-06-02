//#pragma once
//
//#include <libuvc/libuvc.h>
//
//#ifdef __cplusplus
//extern "C" {
//#endif
//typedef enum {
//    USB_EVENT_TYPE_UNKNOWN = 0,
//    USB_EVENT_TYPE_UVC_BUTTON,   // UVC 标准按键
//    USB_EVENT_TYPE_HID,          // HID 设备事件
//    USB_EVENT_TYPE_VENDOR        // 厂商自定义
//} usb_event_type_t;
//
//typedef struct {
//    usb_event_type_t type;
//    uint8_t raw[64];     // 原始数据
//    int raw_len;
//    union {
//        struct {
//            uint8_t button_id;
//            uint8_t state;   // 1=press, 0=release
//        } uvc_button;
//
//        struct {
//            uint8_t report_id;
//        } hid;
//
//        struct {
//            uint8_t code;
//        } vendor;
//
//    } data;
//
//} usb_event_t;
//
//typedef void (usb_event_callback_t)(
//        uvc_device_handle_t *devh,
//        const usb_event_t *event,
//        void *user_ptr
//);
//
//// 扩展结构，支持事件回调
//typedef struct {
//    uvc_device_handle_t *devh;
//    libusb_device_handle *handle;
//
//    uint8_t endpoint;
//    int interface_number;
//
//    struct libusb_transfer *transfer;
//    uint8_t *buffer;
//
//    int running;
//
//    usb_event_callback_t* cb;
//    void *user_ptr;
//
//} usb_event_monitor_t;
//
//int start_usb_event_monitor(uvc_device_handle_t *devh,usb_event_callback_t* cb,void *user_ptr);
//void stop_usb_event_monitor(uvc_device_handle_t *devh);
//
//#ifdef __cplusplus
//}
//#endif
