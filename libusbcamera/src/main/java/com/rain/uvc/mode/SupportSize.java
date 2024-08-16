package com.rain.uvc.mode;

/**
 * 对应的分辨率信息
 */
public class SupportSize {
    //宽度
    private final int width;
    //高度
    private final int height;
    //支持的最大fps
    private final int fps;
    //是否时mjpeg格式
    private final boolean isMjpeg;

    public SupportSize(int width, int height, int fps, boolean isMjpeg) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.isMjpeg = isMjpeg;
    }

    public boolean isMjpeg() {
        return isMjpeg;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFps() {
        return fps;
    }
}

