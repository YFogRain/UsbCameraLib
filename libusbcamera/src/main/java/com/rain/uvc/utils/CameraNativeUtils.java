package com.rain.uvc.utils;

import android.text.TextUtils;
import android.util.Range;
import android.view.Surface;

import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.mode.PreviewModeState;
import com.rain.uvc.mode.SupportSize;
import com.rain.uvc.state.CameraNativeState;
import com.rain.uvc.state.CameraRangeState;
import com.rain.uvc.state.OrientationState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 调用的native方法
 */
public class CameraNativeUtils {

    static {
        System.loadLibrary("usb100");
//        System.loadLibrary("libjpeg-turbo");
        System.loadLibrary("uvc");
        System.loadLibrary("uvcCamera");
    }

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
     * @param fps      对应使用的fps
     * @param isMjpeg  是否使用mjpeg格式，通过获取分辨率列表获取是否支持
     * @return 是否成功
     */
    public static native boolean nativeSetPreviewSize(long nativeId, int width, int height, int fps, boolean isMjpeg);

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
    public static native void setPreviewListener(long nativeId, IFrameListener listener);

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
     * 获取对应类型的boolean类型值
     *
     * @param nativeId 对应的内存地址值
     * @param type     对应获取的类型
     * @return 返回值
     */
    private static native boolean nativeGetBoolValue(long nativeId, int type);

    /**
     * 设置对应类型的boolean类型值
     *
     * @param nativeId 对应的内存地址值
     * @param type     对应获取的类型
     * @param value    对应的值
     * @return 是否成功
     */
    private static native boolean nativeSetBoolValue(long nativeId, int type, boolean value);

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
     * 获取当前预览分辨率信息
     *
     * @param nativeId 对应的内存地址值
     * @return 对应的预览分辨率信息
     */
    public static SupportSize getCurrentPreviewSize(long nativeId) {
        if (nativeId == 0L) return null;
        int[] ints = nativeGetPreviewSize(nativeId);
        if (ints == null || ints.length != 4) {
            return null;
        }
        return new SupportSize(ints[0], ints[1], ints[2], ints[3] == 1);

    }

    /**
     * 获取当前支持的分辨率列表
     *
     * @param nativeId 对应的内存地址值
     * @return 返回mjpeg和yuv的格式数据列表
     */
    public static HashMap<PreviewModeState, List<SupportSize>> getSupportPreviewSizes(long nativeId) {
        if (nativeId == 0L) return null;
        String value = nativeGetSupportPreviewSizes(nativeId);
        if (TextUtils.isEmpty(value)) return null;
        HashMap<PreviewModeState, List<SupportSize>> maps = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(value);
            JSONArray yuvFormats = jsonObject.optJSONArray("yuv_formats");
            if (yuvFormats != null && yuvFormats.length() > 0) {

                List<SupportSize> yuvSizes = new ArrayList<>();
                for (int i = 0; i < yuvFormats.length(); i++) {
                    JSONObject size = yuvFormats.optJSONObject(i);
                    int width = size.optInt("width");
                    int height = size.optInt("height");
                    int fps = size.optInt("fps");
                    yuvSizes.add(new SupportSize(width, height, fps, false));
                }
                maps.put(PreviewModeState.YUV, yuvSizes);
            }

            JSONArray mjpegFormats = jsonObject.optJSONArray("mjpeg_formats");
            if (mjpegFormats != null && mjpegFormats.length() > 0) {
                List<SupportSize> mjpegSizes = new ArrayList<>();
                for (int i = 0; i < mjpegFormats.length(); i++) {
                    JSONObject size = mjpegFormats.optJSONObject(i);
                    int width = size.optInt("width");
                    int height = size.optInt("height");
                    int fps = size.optInt("fps");
                    mjpegSizes.add(new SupportSize(width, height, fps, true));
                }
                maps.put(PreviewModeState.MJPEG, mjpegSizes);
            }
            return maps;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取对应参数的范围
     *
     * @param nativeId 对应的内存地址值
     * @param state    对应支持的参数范围值
     * @return 参数范围信息，如果不存在，则返回null
     */
    public static Range<Integer> getParameterRange(long nativeId, CameraRangeState state) {
        if (nativeId == 0L) return null;
        int[] ints = nativeGetParameterRange(nativeId, state.getState());
        if (ints == null || ints.length != 2) {
            return null;
        }
        return new Range<>(ints[0], ints[1]);
    }

    /**
     * 获取对应的参数信息
     *
     * @param nativeId 对应的内存地址值
     * @param key      对应支持的参数key
     * @return 返回值，如果不支持或者不存在，则返回null
     */
    public static <T> T getParameter(long nativeId, CameraNativeState.Key<T> key) {
        if (nativeId == 0L) return null;
        Class<T> tClass = key.getmClass();
        int number = key.getNumber();
        if (tClass.isAssignableFrom(int.class)) {
            int value = nativeGetIntValue(nativeId, number);
            return (T) Integer.valueOf(value);
        }
        if (tClass.isAssignableFrom(boolean.class)) {
            boolean value = nativeGetBoolValue(nativeId, number);
            return (T) Boolean.valueOf(value);
        }

        if (tClass.isAssignableFrom(OrientationState.class)) {
            int value = nativeGetIntValue(nativeId, number);
            return (T) OrientationState.orientationToState(value);
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
    public static <T> boolean setParameter(long nativeId, CameraNativeState.Key<T> key, T value) {
        if (nativeId == 0L) return false;
        Class<T> tClass = key.getmClass();
        int number = key.getNumber();
        if (tClass.isAssignableFrom(int.class)) {
            return nativeSetIntValue(nativeId, number, (int) value);
        }
        if (tClass.isAssignableFrom(boolean.class)) {
            return nativeSetBoolValue(nativeId, number, (boolean) value);
        }
        if (tClass.isAssignableFrom(OrientationState.class)) {
            return nativeSetIntValue(nativeId, number, ((OrientationState) value).getAngle());
        }
        return false;
    }
}

