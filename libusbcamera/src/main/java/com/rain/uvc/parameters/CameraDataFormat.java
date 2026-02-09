package com.rain.uvc.parameters;

/**
 * 当前支持的格式
 */
public enum CameraDataFormat {
    BGR(0), YUY2(1), NV21(2), RGBA(4), RGB(5), MJPEG(6);
    private final int value;

    CameraDataFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}