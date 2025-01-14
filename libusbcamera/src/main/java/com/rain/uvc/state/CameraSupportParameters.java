package com.rain.uvc.state;


import com.rain.uvc.mode.CameraSize;
import com.rain.uvc.mode.IntRange;

/**
 * @author yuan
 * @createTime: 2024/12/19
 * @des
 */
public class CameraSupportParameters {
    public static final class Key<T> {
        public String type;
        public Class<T> mClass;

        Key(String type, Class<T> mClass) {
            this.type = type;
            this.mClass = mClass;
        }
    }

    //是否支持人脸检测
    public static final Key<Boolean> FACE_DETECT = new Key<>(CameraParameterType.FACE_DETECT, Boolean.class);

    public static final Key<Boolean> AUTO_EXPOSURE = new Key<>(CameraParameterType.AUTO_EXPOSURE, Boolean.class);

    //曝光度
    public static final Key<IntRange> EXPOSURE = new Key<>(CameraParameterType.EXPOSURE, IntRange.class);

    //亮度
    public static final Key<IntRange> BRIGHTNESS = new Key<>(CameraParameterType.BRIGHTNESS, IntRange.class);

    public static final Key<IntRange> CONTRAST = new Key<>(CameraParameterType.CONTRAST, IntRange.class);

    public static final Key<IntRange> GAIN = new Key<>(CameraParameterType.GAIN, IntRange.class);

    public static final Key<IntRange> SATURATION = new Key<>(CameraParameterType.SATURATION, IntRange.class);

    public static final Key<IntRange> ZOOM = new Key<>(CameraParameterType.ZOOM, IntRange.class);

    public static final Key<String[]> SCENE_MODE = new Key<>(CameraParameterType.SCENE_MODE, String[].class);

    public static final Key<String[]> WHITE_BALANCE = new Key<>(CameraParameterType.WHITE_BALANCE, String[].class);

    public static final Key<String[]> COLOR_EFFECTS = new Key<>(CameraParameterType.COLOR_EFFECTS, String[].class);

    public static final Key<CameraSize[]> PREVIEW_SIZE = new Key<>(CameraParameterType.PREVIEW_SIZE, CameraSize[].class);
}

