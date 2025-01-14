package com.rain.uvc.mode;

/**
 * 当前支持的格式
 */
public enum CameraResultFormat {
    RGBA(0), NV21(1);
    private final int value;

    CameraResultFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

