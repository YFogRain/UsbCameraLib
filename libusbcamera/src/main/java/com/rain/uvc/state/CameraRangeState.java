package com.rain.uvc.state;

/**
 *
 */
public enum CameraRangeState {
    //曝光度
    EXPOSURE(CameraNativeTypeNumber.EXPOSURE),
    //亮度
    BRIGHTNESS(CameraNativeTypeNumber.BRIGHTNESS),
    //对比度
    CONTRAST(CameraNativeTypeNumber.CONTRAST),
    //增益值
    GAIN(CameraNativeTypeNumber.GAIN),
    //饱和度
    SATURATION(CameraNativeTypeNumber.SATURATION),
    //缩放
    ZOOM(CameraNativeTypeNumber.ZOOM),
    ;
    private final int state;

    CameraRangeState(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }
}

