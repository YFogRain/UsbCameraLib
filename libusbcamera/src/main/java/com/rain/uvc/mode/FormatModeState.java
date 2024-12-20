package com.rain.uvc.mode;

/**
 * 当前支持的格式
 */
public enum FormatModeState {
    YUY2(0), MJPEG(1);
    private final int value;

    FormatModeState(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static FormatModeState valueToFormatMode(int format) {
        if (format == 1) {
            return FormatModeState.MJPEG;
        }
        return FormatModeState.YUY2;
    }
}

