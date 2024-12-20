package com.rain.uvc.state;


import com.rain.uvc.mode.CameraSize;

/**
 * @author yuan
 * @createTime: 2024/12/19
 * @des
 */
public class CameraParameter {
    public static final class Key<T> {
        public String type;
        public Class<T> mClass;

        Key(String type, Class<T> mClass) {
            this.type = type;
            this.mClass = mClass;
        }
    }

    //自动曝光开关
    public static final Key<Boolean> AUTO_EXPOSURE = new Key<>(CameraParameterType.AUTO_EXPOSURE, boolean.class);

    //曝光度
    public static final Key<Integer> EXPOSURE = new Key<>(CameraParameterType.EXPOSURE, int.class);

    //亮度
    public static final Key<Integer> BRIGHTNESS = new Key<>(CameraParameterType.BRIGHTNESS, int.class);

    public static final Key<Integer> CONTRAST = new Key<>(CameraParameterType.CONTRAST, int.class);

    public static final Key<Integer> GAIN = new Key<>(CameraParameterType.GAIN, int.class);

    public static final Key<Integer> SATURATION = new Key<>(CameraParameterType.SATURATION, int.class);

    public static final Key<Integer> ZOOM = new Key<>(CameraParameterType.ZOOM, int.class);

    public static final Key<Integer> ORIENTATION = new Key<>(CameraParameterType.ORIENTATION, int.class);

    public static final Key<CameraSize> PREVIEW_SIZE = new Key<>(CameraParameterType.PREVIEW_SIZE, CameraSize.class);

    public static final Key<String> SCENE_MODE = new Key<>(CameraParameterType.SCENE_MODE, String.class);

    public static final Key<String> WHITE_BALANCE = new Key<>(CameraParameterType.WHITE_BALANCE, String.class);

    public static final Key<String> COLOR_EFFECTS = new Key<>(CameraParameterType.COLOR_EFFECTS, String.class);

    public static final Key<String> ISO = new Key<>(CameraParameterType.ISO, String.class);

}

