package com.rain.uvc.mode;

/**
 * 当前支持的格式
 */
public enum FormatRequestMode {
    RGBA(0),YUV420SP(1);
    private final int value;

    FormatRequestMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

