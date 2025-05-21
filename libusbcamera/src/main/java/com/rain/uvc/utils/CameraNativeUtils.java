package com.rain.uvc.utils;

import android.text.TextUtils;
import android.view.Surface;

import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.mode.CameraSize;
import com.rain.uvc.state.CameraPreviewFormat;
import com.rain.uvc.state.CameraParameter;
import com.rain.uvc.state.CameraParameterType;
import com.rain.uvc.state.CameraSupportParameters;
import com.rain.uvc.mode.IntRange;
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
     * 根据对应的FileDescriptor连接指定设备
     *
     * @param fd 文件描述符
     * @return 是否成功
     */
    public static native long nativeOpen(int fd, int busNum, int devAddress);

    /**
     * 读取可使用的v4l2的列表信息
     *
     * @return 返回列表地址
     */
    public static native String[] nativeLoadV4L2Devices();

    /**
     * 根据对应的FileDescriptor连接指定设备
     *
     * @param videoPath 对应打开的路径
     * @return 是否成功
     */
    public static native long nativeOpenVideo(String videoPath);

    /**
     * 断开连接设备
     *
     * @param nativeId 对应的内存地址值
     * @return 是否成功
     */
    public static native boolean nativeClose(long nativeId);

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
     * 设置预览监听，返回的数据格式固定为RGB格式
     *
     * @param nativeId 设置的对应id
     * @param listener 监听器
     */
    public static native boolean setPreviewListener(long nativeId, IFrameListener listener, int mode);

    /**
     * 获取支持的参数信息
     */
    public static native String nativeGetSupportedParameters(long nativeId, int type);


    /**
     * 获取当前参数信息
     */
    public static native String nativeGetParameterValue(long nativeId, int type);

    /**
     * 设置当前参数信息
     */
    public static native boolean nativeSetParameterValue(long nativeId, int type, int value);

    /**
     * 获取对应的参数信息
     *
     * @param nativeId 对应的内存地址值
     * @param key      对应支持的参数key
     * @return 返回值，如果不支持或者不存在，则返回null
     */
    public static <T> T getParameter(long nativeId, CameraParameter.Key<T> key) {
        if (nativeId == 0L) return null;
        int id = ketTypeToNativeId(key.type);
        if (id == -1) {
            return null;
        }
        String value = nativeGetParameterValue(nativeId, id);
        if (CameraSize.class.isAssignableFrom(key.mClass)) {
            String[] array = null;
            if (!TextUtils.isEmpty(value)) {
                array = value.split(":");
            }
            if (array == null || array.length < 3) {
                return null;
            }
            return (T) new CameraSize(Integer.parseInt(array[0]), Integer.parseInt(array[1]), CameraPreviewFormat.valueToFormatMode(Integer.parseInt(array[2])));
        }
        if (DisplayTransformState.class.isAssignableFrom(key.mClass)) {
            int orientation = -1;
            if (!TextUtils.isEmpty(value)) {
                orientation = Integer.parseInt(value);
            }
            return (T) DisplayTransformState.orientationToState(orientation);
        }

        if (Integer.class.isAssignableFrom(key.mClass)) {
            if (!TextUtils.isEmpty(value)) {
                return (T) Integer.getInteger(value);
            }
            return null;
        }
        if (boolean.class.isAssignableFrom(key.mClass) || Boolean.class.isAssignableFrom(key.mClass)) {
            int state = 0;
            if (!TextUtils.isEmpty(value)) {
                state = Integer.parseInt(value);
            }
            return (T) Boolean.valueOf(state == 1);
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
            return nativeSetParameterValue(nativeId, ketTypeToNativeId(key.type), (int) value);
        }
        if (boolean.class.isAssignableFrom(key.mClass) || Boolean.class.isAssignableFrom(key.mClass)) {
            return nativeSetParameterValue(nativeId, ketTypeToNativeId(key.type), (boolean) value ? 1 : 0);
        }
        if (DisplayTransformState.class.isAssignableFrom(key.mClass)) {
            return nativeSetParameterValue(nativeId, ketTypeToNativeId(key.type), ((DisplayTransformState) value).getAngle());
        }
        if (CameraSize.class.isAssignableFrom(key.mClass)) {
            CameraSize size = (CameraSize) value;
            return nativeSetPreviewSize(nativeId, size.getWidth(), size.getHeight(), size.getFormat().getValue());
        }
        return false;
    }

    private static int ketTypeToNativeId(String type) {
        if (CameraParameterType.PREVIEW_SIZE.equals(type)) {
            return 0;
        } else if (CameraParameterType.DISPLAY_TRANSFORM.equals(type)) {
            return 1;
        } else if (CameraParameterType.AUTO_EXPOSURE.equals(type)) {
            return 2;
        } else if (CameraParameterType.EXPOSURE.equals(type)) {
            return 3;
        } else if (CameraParameterType.BRIGHTNESS.equals(type)) {
            return 4;
        } else if (CameraParameterType.CONTRAST.equals(type)) {
            return 5;
        } else if (CameraParameterType.GAIN.equals(type)) {
            return 6;
        } else if (CameraParameterType.SATURATION.equals(type)) {
            return 7;
        } else if (CameraParameterType.ZOOM.equals(type)) {
            return 8;
        } else if (CameraParameterType.AUTO_FOCUS.equals(type)) {
            return 9;
        } else if (CameraParameterType.FOCUS.equals(type)) {
            return 10;
        } else if (CameraParameterType.IRIS.equals(type)) {
            return 11;
        } else if (CameraParameterType.AUTO_HUE.equals(type)) {
            return 12;
        } else if (CameraParameterType.HUE.equals(type)) {
            return 13;
        } else if (CameraParameterType.AUTO_WHITE_BALANCE.equals(type)) {
            return 14;
        } else if (CameraParameterType.WHITE_BALANCE.equals(type)) {
            return 15;
        } else if (CameraParameterType.SCENE_MODE.equals(type)) {
            return 16;
        } else if (CameraParameterType.PRIVACY.equals(type)) {
            return 17;
        }
        return -1;
    }

    public static <T> T getSupportedParameter(long nativeId, CameraSupportParameters.Key<T> key) {
        if (nativeId == 0L) return null;
        String result = nativeGetSupportedParameters(nativeId, ketTypeToNativeId(key.type));
        if (key.type.equals(CameraParameterType.PREVIEW_SIZE)) {
            if (TextUtils.isEmpty(result)) {
                return null;
            }
            try {
                JSONArray jsonArray = new JSONArray(result);
                if (jsonArray.length() <= 0) {
                    return null;
                }
                List<CameraSize> lists = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.optJSONObject(i);
                    int width = jsonObject.optInt("width");
                    int height = jsonObject.optInt("height");
                    int format = jsonObject.optInt("format");
                    lists.add(new CameraSize(width, height, CameraPreviewFormat.valueToFormatMode(format)));
                }
                return (T) lists.toArray(new CameraSize[0]);
            } catch (Exception e) {
                return null;
            }
        }

        if (boolean.class.isAssignableFrom(key.mClass) || Boolean.class.isAssignableFrom(key.mClass)) {
            int state = 0;
            if (!TextUtils.isEmpty(result)) {
                state = Integer.parseInt(result);
            }
            return (T) Boolean.valueOf(state == 1);
        } else if (IntRange.class.isAssignableFrom(key.mClass)) {
            if (TextUtils.isEmpty(result) || !result.contains(":")) {
                return null;
            }
            String[] split = result.split(":");
            if (split.length != 2) {
                return null;
            }
            return (T) new IntRange(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
        }
        return null;
    }

    public static native boolean nativeStartRecord(long nativeId, String fileName);

    public static native boolean nativeStopRecord(long nativeId);

    public static native void nativeSetRecordFormat(long nativeId, int format);

    public static native void nativeSetParentPath(long nativeId, String parentPath);

    public static native boolean nativeSetDefaultParentPath(String parentPath);

    public static native String nativeGetRecordPath(long nativeId);
}

