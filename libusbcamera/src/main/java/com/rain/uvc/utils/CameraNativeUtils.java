package com.rain.uvc.utils;

import android.text.TextUtils;
import android.view.Surface;

import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.mode.CameraSize;
import com.rain.uvc.mode.FormatModeState;
import com.rain.uvc.state.CameraParameter;
import com.rain.uvc.state.CameraParameterType;
import com.rain.uvc.state.CameraSupportParameters;
import com.rain.uvc.state.IntRange;
import com.rain.uvc.state.DisplayTransformState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用的native方法
 */
public class CameraNativeUtils {

    static {
        System.loadLibrary("usb100");
        System.loadLibrary("uvc");
        System.loadLibrary("uvcCamera");
    }


    public static native boolean debuggable(int status);

    /**
     * 创建对应的jni内存地址
     *
     * @return 返回对应内存地址，后续操作访问需要
     */
    public static native long nativeCreate();

    /**
     * 销毁对应的camera对象
     *
     * @param nativeId 对应的内存地址值
     * @return 是否成功
     */
    public static native boolean nativeDestroy(long nativeId);

    /**
     * 根据对应的FileDescriptor连接指定设备
     *
     * @param nativeId 对应的内存地址值
     * @param fd       文件描述符
     * @return 是否成功
     */
    public static native boolean nativeConnect(long nativeId, int fd);

    /**
     * 根据对应的FileDescriptor连接指定设备
     *
     * @param nativeId 对应的内存地址值
     * @param fd       文件描述符
     * @return 是否成功
     */
    public static native boolean nativeConnectFd(long nativeId, int fd, int busNum, int devAddress);

    /**
     * 断开连接设备
     *
     * @param nativeId 对应的内存地址值
     * @return 是否成功
     */
    public static native boolean nativeDisConnect(long nativeId);

    /**
     * 开启预览
     *
     * @param nativeId 对应的内存地址值
     * @return 是否成功
     */
    public static native boolean nativeStartPreview(long nativeId);

    /**
     * 关闭预览
     *
     * @param nativeId 对应的内存地址值
     * @return 是否成功
     */
    public static native boolean nativeStopPreview(long nativeId);

    /**
     * 设置预览控件
     *
     * @param nativeId 对应的内存地址值
     * @param surface  对应预览的surface
     * @return 是否成功
     */
    public static native boolean nativeSetDisplaySurface(long nativeId, Surface surface);

    /**
     * 设置预览的分辨率信息
     *
     * @param nativeId 对应的内存地址值
     * @param width    宽
     * @param height   高
     * @return 是否成功
     */
    public static native boolean nativeSetPreviewSize(long nativeId, int width, int height, int format);

    /**
     * 获取是否支持自动曝光
     *
     * @param nativeId 对应的内存地址值
     * @return 是否支持
     */
    public static native boolean nativeGetSupportAutoExposure(long nativeId);

    /**
     * 设置预览监听，返回的数据格式固定为RGB格式
     *
     * @param nativeId 设置的对应id
     * @param listener 监听器
     */
    public static native void setPreviewListener(long nativeId, IFrameListener listener, int mode);

    /**
     * 获取是否支持自动曝光
     *
     * @param nativeId 对应的内存地址值
     * @return 是否支持
     */
    private static native String nativeGetSupportPreviewSizes(long nativeId);

    /**
     * 获取当前的分辨率信息
     *
     * @param nativeId 对应的内存地址值
     * @return 分辨率信息数组
     */
    private static native int[] nativeGetPreviewSize(long nativeId);

    /**
     * 获取当前支持的参数范围
     *
     * @param nativeId 对应的内存地址值
     * @param type     对应获取的类型
     * @return 支持参数数组，length固定为2
     */
    private static native int[] nativeGetParameterRange(long nativeId, int type);

    /**
     * 获取对应类型的int类型值
     *
     * @param nativeId 对应的内存地址值
     * @param type     对应获取的类型
     * @return 返回值
     */
    private static native int nativeGetIntValue(long nativeId, int type);

    /**
     * 设置对应的int类型值
     *
     * @param nativeId 对应的内存地址值
     * @param type     对应获取的类型
     * @param value    对应的值
     * @return 是否成功
     */
    private static native boolean nativeSetIntValue(long nativeId, int type, int value);

    /**
     * 获取当前支持的分辨率列表
     *
     * @param nativeId 对应的内存地址值
     * @return 返回mjpeg和yuv的格式数据列表
     */
    public static CameraSize[] getSupportPreviewSizes(long nativeId) {
        if (nativeId == 0L) return null;
        String value = nativeGetSupportPreviewSizes(nativeId);
        if (TextUtils.isEmpty(value)) return null;
        try {
            JSONArray jsonArray = new JSONArray(value);
            if (jsonArray.length() <= 0) {
                return null;
            }
            List<CameraSize> lists = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                int width = jsonObject.optInt("width");
                int height = jsonObject.optInt("height");
                int format = jsonObject.optInt("format");
                lists.add(new CameraSize(width, height, FormatModeState.valueToFormatMode(format)));
            }
            return lists.toArray(new CameraSize[0]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取对应的参数信息
     *
     * @param nativeId 对应的内存地址值
     * @param key      对应支持的参数key
     * @return 返回值，如果不支持或者不存在，则返回null
     */
    public static <T> T getParameter(long nativeId, CameraParameter.Key<T> key) {
        if (nativeId == 0L) return null;
        if (CameraSize.class.isAssignableFrom(key.mClass)) {
            int[] ints = nativeGetPreviewSize(nativeId);
            if (ints == null || ints.length != 3) {
                return null;
            }
            return (T) new CameraSize(ints[0], ints[1], FormatModeState.valueToFormatMode(ints[2]));
        }
        if (DisplayTransformState.class.isAssignableFrom(key.mClass)) {
            int value = nativeGetIntValue(nativeId, ketTypeToNativeId(key.type));
            return (T) DisplayTransformState.orientationToState(value);
        }
        if (int.class.isAssignableFrom(key.mClass) || Integer.class.isAssignableFrom(key.mClass)) {
            int value = nativeGetIntValue(nativeId, ketTypeToNativeId(key.type));
            return (T) Integer.valueOf(value);
        }
        if (boolean.class.isAssignableFrom(key.mClass) || Boolean.class.isAssignableFrom(key.mClass)) {
            int value = nativeGetIntValue(nativeId, ketTypeToNativeId(key.type));
            return (T) Boolean.valueOf(value == 1);
        }
        return null;
    }

    /**
     * 设置对应的参数信息
     *
     * @param nativeId 对应的内存地址值
     * @param key      对应支持的参数key
     * @param value    对应的值
     * @return 是否支持
     */
    public static <T> boolean setParameter(long nativeId, CameraParameter.Key<T> key, T value) {
        if (nativeId == 0L) return false;
        if (int.class.isAssignableFrom(key.mClass) || Integer.class.isAssignableFrom(key.mClass)) {
            return nativeSetIntValue(nativeId, ketTypeToNativeId(key.type), (int) value);
        }
        if (boolean.class.isAssignableFrom(key.mClass) || Boolean.class.isAssignableFrom(key.mClass)) {
            return nativeSetIntValue(nativeId, ketTypeToNativeId(key.type), (boolean) value ? 1 : 0);
        }
        if (DisplayTransformState.class.isAssignableFrom(key.mClass)) {
            return nativeSetIntValue(nativeId, ketTypeToNativeId(key.type), ((DisplayTransformState) value).getAngle());
        }
        if (CameraSize.class.isAssignableFrom(key.mClass)) {
            CameraSize size = (CameraSize) value;
            return nativeSetPreviewSize(nativeId, size.getWidth(), size.getHeight(), size.getFormat().getValue());
        }
        return false;
    }

    private static int ketTypeToNativeId(String type) {
        if (CameraParameterType.AUTO_EXPOSURE.equals(type)) {
            return 1;
        } else if (CameraParameterType.EXPOSURE.equals(type)) {
            return 2;
        } else if (CameraParameterType.BRIGHTNESS.equals(type)) {
            return 3;
        } else if (CameraParameterType.CONTRAST.equals(type)) {
            return 4;
        } else if (CameraParameterType.GAIN.equals(type)) {
            return 5;
        } else if (CameraParameterType.SATURATION.equals(type)) {
            return 6;
        } else if (CameraParameterType.ZOOM.equals(type)) {
            return 7;
        } else if (CameraParameterType.DISPLAY_TRANSFORM.equals(type)) {
            return 8;
        }
        return -1;
    }


    public static <T> T getSupportedParameter(long nativeId, CameraSupportParameters.Key<T> key) {
        if (nativeId == 0L) return null;
        if (key.type.equals(CameraParameterType.PREVIEW_SIZE)) {
            return (T) getSupportPreviewSizes(nativeId);
        }
        if (key.type.equals(CameraParameterType.AUTO_EXPOSURE)) {
            return (T) Boolean.valueOf(nativeGetSupportAutoExposure(nativeId));
        }
        int stateId = ketTypeToNativeId(key.type);
        if (stateId != -1) {
            int[] ints = nativeGetParameterRange(nativeId, stateId);
            if (ints == null || ints.length != 2) {
                return null;
            }
            return (T) new IntRange(ints[0], ints[1]);
        }
        return null;
    }

}

