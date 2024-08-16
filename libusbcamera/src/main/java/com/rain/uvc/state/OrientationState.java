package com.rain.uvc.state;

/**
 * 预览方向
 */
public enum OrientationState {
    //0度
    ORIENTATION_0(0),
    //90度旋转
    ORIENTATION_90(90),
    //180度旋转
    ORIENTATION_180(180),
    //270度旋转
    ORIENTATION_270(270);
    private final int angle;

    OrientationState(int angle) {
        this.angle = angle;
    }

    public int getAngle() {
        return angle;
    }

    public static OrientationState orientationToState(int orientation) {
        if (orientation >= 45 && orientation <= 134) {
            return ORIENTATION_90;
        }
        if (orientation >= 135 && orientation <= 224) {
            return ORIENTATION_180;
        }
        if (orientation >= 225 && orientation <= 315) {
            return ORIENTATION_270;
        }
        return ORIENTATION_0;
    }
}

