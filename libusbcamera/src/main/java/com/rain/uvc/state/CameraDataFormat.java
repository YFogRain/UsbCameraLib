package com.rain.uvc.state;

/**
 * 当前支持的格式
 */
public enum CameraDataFormat {
    BGR(0), NV21(1), RGBA(2);
    private final int value;

    CameraDataFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

