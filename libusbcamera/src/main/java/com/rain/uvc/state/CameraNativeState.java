package com.rain.uvc.state;

/**
 * 对应支持获取设置的参数
 */
public class CameraNativeState {

    public static class Key<T> {
        private final int number;
        private final Class<T> mClass;

        public Key(int number, Class<T> mClass) {
            this.number = number;
            this.mClass = mClass;
        }

        public int getNumber() {
            return number;
        }

        public Class<T> getmClass() {
            return mClass;
        }
    }

    //自动曝光
    public static CameraNativeState.Key<Boolean> AUTO_EXPOSURE = new CameraNativeState.Key<>(CameraNativeTypeNumber.AUTO_EXPOSURE, boolean.class);
    //曝光度
    public static CameraNativeState.Key<Integer> EXPOSURE = new CameraNativeState.Key<>(CameraNativeTypeNumber.EXPOSURE, int.class);
    //亮度
    public static CameraNativeState.Key<Integer> BRIGHTNESS = new CameraNativeState.Key<>(CameraNativeTypeNumber.BRIGHTNESS, int.class);
    //对比度
    public static CameraNativeState.Key<Integer> CONTRAST = new CameraNativeState.Key<>(CameraNativeTypeNumber.CONTRAST, int.class);
    //增益值
    public static CameraNativeState.Key<Integer> GAIN = new CameraNativeState.Key<>(CameraNativeTypeNumber.GAIN, int.class);
    //饱和度
    public static CameraNativeState.Key<Integer> SATURATION = new CameraNativeState.Key<>(CameraNativeTypeNumber.SATURATION, int.class);
    //缩放值
    public static CameraNativeState.Key<Integer> ZOOM = new CameraNativeState.Key<>(CameraNativeTypeNumber.ZOOM, int.class);
    //旋转方向
    public static CameraNativeState.Key<OrientationState> ORIENTATION = new CameraNativeState.Key<>(CameraNativeTypeNumber.ORIENTATION, OrientationState.class);

}



