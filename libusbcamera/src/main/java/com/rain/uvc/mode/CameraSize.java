package com.rain.uvc.mode;

import com.rain.uvc.state.CameraPreviewFormat;

/**
 * 对应的分辨率信息
 */
public class CameraSize {
    //宽度
    private final int width;
    //高度
    private final int height;
    //支持的最大fps
    //是否时mjpeg格式
    private final CameraPreviewFormat format;

    public CameraSize(int width, int height, CameraPreviewFormat format) {
        this.width = width;
        this.height = height;
        this.format = format;
    }

    public CameraPreviewFormat getFormat() {
        return format;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

