package com.rain.uvc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.utils.CameraNativeUtils;

import java.util.Collection;

/**
 * @author yuan
 * @createTime: 2024/8/16
 * @des uvc摄像头操作帮助类
 */
public class UvcCameraHelper {

    /**
     * debug模式开关
     */
    public static boolean debuggable(boolean status) {
        return CameraNativeUtils.debuggable(status ? 1 : 0);
    }

    /**
     * 打开对应的uvc摄像头驱动
     *
     * @param device 当前需要打开的uvc设备
     * @return 返回当前可操作的uvcCamera对象
     */
    public static UvcCamera create(UsbDevice device) {

        if (!checkPermission(Manifest.permission.CAMERA)) {
            Log.d("UvcCamera", "未获取到相机权限");
            return null;
        }
        return new UvcCamera(device);
    }


    /**
     * 打开对应的uvc摄像头驱动
     *
     * @param vId 对应设备的VendorId
     * @param pId 对应的设备的ProductId
     * @return 返回当前可操作的uvcCamera对象
     */
    public static UvcCamera create(int vId, int pId) {
        UsbDevice device = findDevice(vId, pId);
        if (device == null) {
            return null;
        }
        return create(device);
    }

    private static UsbDevice findDevice(int vId, int pId) {
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        Collection<UsbDevice> values = manager.getDeviceList().values();
        for (UsbDevice device : values) {
            if (device.getVendorId() == vId && device.getProductId() == pId) return device;
        }
        return null;
    }

    /**
     * 检查权限
     *
     * @param permissions 对应需要验证的权限
     * @return 权限是否已经拥有，false表示未拥有
     */
    private static boolean checkPermission(String... permissions) {
        if (permissions == null) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String per : permissions) {
            if (!checkSinglePermission(per)) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkSinglePermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        int result = OverallContext.baseContext.checkSelfPermission(permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }
}

