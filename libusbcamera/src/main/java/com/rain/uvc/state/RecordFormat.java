package com.rain.uvc.state;

/**
 * @author yuan
 * @createTime: 2025/5/21
 * @des 录制格式设置
 */
public enum RecordFormat {
    MP4V(0),
    AVC(1),
    VID(2),
    MJPEG(3),
    DIVX(4);
    private final int value;

    RecordFormat(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
