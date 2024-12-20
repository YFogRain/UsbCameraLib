package com.rain.uvc.utils;

import com.rain.uvc.mode.CameraSize;
import com.rain.uvc.mode.FormatModeState;

import java.util.HashMap;
import java.util.List;

/**
 * @author yuan
 * @createTime: 2024/12/20
 * @des
 */
public class CameraUtils {
    /**
     * 获取当前宽高对应使用的分辨率属性
     *
     * @param width  宽
     * @param height 高
     * @return 返回当前可使用的分辨率参数
     */
    public static CameraSize loadUseCameraSize(CameraSize[] cameraSizes, int width, int height, FormatModeState formatState) {
        if (cameraSizes == null) {
            return null;
        }
        for (CameraSize size : cameraSizes) {
            if (size.getWidth() == width && size.getHeight() == height && size.getFormat() == formatState) {
                return size;
            }
        }
        return null;

    }
}

