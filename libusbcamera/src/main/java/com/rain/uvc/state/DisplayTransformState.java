package com.rain.uvc.state;

/**
 * 预览方向
 */
public enum DisplayTransformState {
    TRANSFORM_IDENTITY(0),//不变化
    TRANSFORM_MIRROR_HORIZONTAL(1), //水平镜像
    TRANSFORM_MIRROR_VERTICAL(2), //垂直镜像

    TRANSFORM_ROTATE_90(3), //旋转90度
    TRANSFORM_ROTATE_180(4), //旋转180度
    TRANSFORM_ROTATE_270(5), //旋转270度

    TRANSFORM_FLIP_H_ROTATE_90(6),//旋转90度+水平镜像
    TRANSFORM_FLIP_H_ROTATE_180(7), //旋转180度+水平镜像
    TRANSFORM_FLIP_H_ROTATE_270(8), //旋转270度+水平镜像

    TRANSFORM_FLIP_V_ROTATE_90(9),//旋转90度+垂直镜像
    TRANSFORM_FLIP_V_ROTATE_180(10), //旋转180度+垂直镜像
    TRANSFORM_FLIP_V_ROTATE_270(11);//旋转270度+垂直镜像

    private final int angle;

    DisplayTransformState(int angle) {
        this.angle = angle;
    }

    public int getAngle() {
        return angle;
    }

    public static DisplayTransformState orientationToState(int orientation) {
        DisplayTransformState orientationState;
        switch (orientation) {
            case 1:
                orientationState = DisplayTransformState.TRANSFORM_MIRROR_HORIZONTAL;
                break;
            case 2:
                orientationState = DisplayTransformState.TRANSFORM_MIRROR_VERTICAL;
                break;
            case 3:
                orientationState = DisplayTransformState.TRANSFORM_ROTATE_90;
                break;
            case 4:
                orientationState = DisplayTransformState.TRANSFORM_ROTATE_180;
                break;
            case 5:
                orientationState = DisplayTransformState.TRANSFORM_ROTATE_270;
                break;
            case 6:
                orientationState = DisplayTransformState.TRANSFORM_FLIP_H_ROTATE_90;
                break;
            case 7:
                orientationState = DisplayTransformState.TRANSFORM_FLIP_H_ROTATE_180;
                break;
            case 8:
                orientationState = DisplayTransformState.TRANSFORM_FLIP_H_ROTATE_270;
                break;
            case 9:
                orientationState = DisplayTransformState.TRANSFORM_FLIP_V_ROTATE_90;
                break;
            case 10:
                orientationState = DisplayTransformState.TRANSFORM_FLIP_V_ROTATE_180;
                break;
            case 11:
                orientationState = DisplayTransformState.TRANSFORM_FLIP_V_ROTATE_270;
                break;
            default:
                orientationState = DisplayTransformState.TRANSFORM_IDENTITY;
                break;
        }
        return orientationState;
    }
}

