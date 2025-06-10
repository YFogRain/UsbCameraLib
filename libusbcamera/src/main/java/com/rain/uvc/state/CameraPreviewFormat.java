package com.rain.uvc.state;

/**
 * 当前支持的格式
 */
public enum CameraPreviewFormat {
    BGR(0), YUY2(1), NV21(2), NV12(3), RGB(5), MJPEG(6), JPEG(7);
    private final int value;

    CameraPreviewFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static CameraPreviewFormat valueToFormatMode(int format) {
        switch (format) {
            case 0:
                return CameraPreviewFormat.BGR;
            case 2:
                return CameraPreviewFormat.NV21;
            case 3:
                return CameraPreviewFormat.NV12;
            case 5:
                return CameraPreviewFormat.RGB;
            case 6:
                return CameraPreviewFormat.MJPEG;
            case 7:
                return CameraPreviewFormat.JPEG;
            default:
                return CameraPreviewFormat.YUY2;
        }
    }
}
