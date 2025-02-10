package com.rain.uvc.state;

/**
 * 当前支持的格式
 */
public enum CameraPreviewFormat {
    YUY2(0), NV12(1), NV21(2), MJPEG(3), JPEG(4), RGB(5), BGR(6);
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

