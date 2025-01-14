package com.rain.uvc.mode;

/**
 * 当前支持的格式
 */
public enum CameraPreviewFormat {
    YUY2(0), MJPEG(1);
    private final int value;

    CameraPreviewFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CameraPreviewFormat valueToFormatMode(int format) {
        if (format == 1) {
            return CameraPreviewFormat.MJPEG;
        }
        return CameraPreviewFormat.YUY2;
    }
}

