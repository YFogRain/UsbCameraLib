package com.camera.uvc.utils;

import android.view.Surface;

import com.camera.uvc.listener.IFrameListener;

/**
 * @author yuan
 * @createTime: 2025/11/2
 * @des 相机调用native层接口
 */
public class CameraNativeUtils {
    static {
        System.loadLibrary("jpeg-turbo2.1.2");
        System.loadLibrary("usb1.0.27");
        System.loadLibrary("uvc0.6");
        System.loadLibrary("uvc_camera");
    }


    private static native long open(int fd);

    public static native void close(long nativeId);

    public static native boolean setDisplaySurface(long nativeId, Surface surface);

    public static native boolean startPreview(long nativeId);

    public static native boolean stopPreview(long nativeId);

    private static native String getSupportedParameters(long nativeId, int type);

    private static native String getParameter(long nativeId, int type);

    private static native boolean setParameter(long nativeId, int type, int value);

    private static native void setFrameListener(IFrameListener listener, int format);

}

