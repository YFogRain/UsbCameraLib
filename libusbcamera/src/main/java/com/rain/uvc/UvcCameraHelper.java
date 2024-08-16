package com.rain.uvc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.rain.uvc.provider.OverallContext;

import java.util.Collection;

/**
 * @author yuan
 * @createTime: 2024/8/16
 * @des uvc摄像头操作帮助类
 */
public class UvcCameraHelper {

    /**
     * 打开对应的uvc摄像头驱动
     *
     * @param device 当前需要打开的uvc设备
     * @return 返回当前可操作的uvcCamera对象
     */
    public static UvcCamera create(UsbDevice device) {
        boolean isHavePermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission = OverallContext.baseContext.checkSelfPermission(Manifest.permission.CAMERA);
            isHavePermission = permission == PackageManager.PERMISSION_GRANTED;
        } else {
            isHavePermission = true;
        }
        if (!isHavePermission) {
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
}

