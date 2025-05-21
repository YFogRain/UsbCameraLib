package com.rain.uvc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.rain.uvc.camera.ICameraDevice;
import com.rain.uvc.listener.ICameraOpenListener;
import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.state.CameraStateException;
import com.rain.uvc.utils.CameraNativeUtils;
import com.rain.uvc.utils.UsbPermissionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author yuan
 * @createTime: 2025/1/10
 * @des
 */
public class CameraUvcManager {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final ConcurrentHashMap<ICameraDevice, CountDownLatch> runningDevices = new ConcurrentHashMap<>();

    /**
     * debug模式开关
     */
    public static boolean debuggable(boolean status) {
        return CameraNativeUtils.debuggable(status ? 1 : 0);
    }

    /**
     * 设置默认保存地址
     */
    public static boolean setDefaultRecordParent(@NonNull String path) {
        return CameraNativeUtils.nativeSetDefaultParentPath(path);
    }

    /**
     * 获取uvc的摄像头列表
     *
     * @return 返回摄像头列表
     */
    public static String[] getV4L2Devices() {
        return CameraNativeUtils.nativeLoadV4L2Devices();
    }

    /**
     * 获取uvc的摄像头列表
     *
     * @return 返回摄像头列表
     */
    public static List<UsbDevice> getCameraDevices() {
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        Collection<UsbDevice> values = manager.getDeviceList().values();
        List<UsbDevice> list = new ArrayList<>();
        for (UsbDevice device : values) {
            if (checkDeviceUvc(device)) {
                list.add(device);
            }
        }
        return list;
    }

    /**
     * 是否是摄像头类型
     */
    public static boolean checkDeviceUvc(UsbDevice usbDevice) {
        //校验类型是否是usb摄像头
        if (usbDevice.getDeviceClass() != 239 && usbDevice.getDeviceSubclass() != 2) {
            return false;
        }
        //处理特殊的情况
        if (usbDevice.getProductId() == 24581 || usbDevice.getProductId() == 33054) {
            return false;
        }
        String productName = usbDevice.getProductName();
        //检查是否存在android字样，如果存在，则说明是Android的系统设备，非摄像头
        return TextUtils.isEmpty(productName) || !productName.toLowerCase().contains("android");
    }


    public static UsbDevice getDeviceForId(int vId, int pId) {
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        Collection<UsbDevice> values = manager.getDeviceList().values();
        for (UsbDevice device : values) {
            if (device.getVendorId() == vId && device.getProductId() == pId) return device;
        }
        return null;
    }

    /**
     * video类型打开
     *
     * @param videoPath /dev/videoX路径
     * @return 打开后的操作对象
     * @throws CameraStateException 异常信息
     */
    public static ICameraDevice openCamera(@NonNull String videoPath) throws CameraStateException {
        if (!checkPermission(Manifest.permission.CAMERA)) {
            throw new CameraStateException("请检查权限");
        }
        if (TextUtils.isEmpty(videoPath)) {
            throw new CameraStateException("请检查输入的路径");
        }
        long nativeId = CameraNativeUtils.nativeOpenVideo(videoPath);
        if (nativeId == 0L) {
            throw new CameraStateException("请检查当前路径权限且为video类型");
        }
        return new ICameraDevice(nativeId, videoPath, null);
    }


    public static ICameraDevice openCamera(@NonNull UsbDevice usbDevice) throws CameraStateException {
        return openCamera(usbDevice, 10 * 1000);
    }

    /**
     * 同步打开
     * 需要在子线程内执行
     *
     * @param usbDevice 打开的usb设备
     * @param timeout   超时时间
     * @return 返回打开后的操作对象
     */
    public static ICameraDevice openCamera(@NonNull UsbDevice usbDevice, long timeout) throws CameraStateException {
        if (!checkPermission(Manifest.permission.CAMERA)) {
            throw new CameraStateException("请检查权限");
        }
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        if (!UsbPermissionHelper.requestPermissionSync(usbDevice, manager, timeout)) {
            throw new CameraStateException("申请usb临时权限失败");
        }
        String[] deviceNames = loadSplit(usbDevice);
        if (deviceNames == null || deviceNames.length < 2) {
            throw new CameraStateException("获取usb驱动信息失败");
        }
        UsbDeviceConnection usbDeviceConnection = manager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            throw new CameraStateException("打开usb设备驱动失败");
        }
        long nativeId = CameraNativeUtils.nativeOpen(usbDeviceConnection.getFileDescriptor(), getBusNum(deviceNames), getDevAddress(deviceNames));
        if (nativeId == 0L) {
            throw new CameraStateException("usb设备打开失败");
        }
        return new ICameraDevice(nativeId, usbDevice.getDeviceName(), usbDeviceConnection);
    }

    public static void openCamera(UsbDevice usbDevice, ICameraOpenListener listener) {
        openCamera(usbDevice, 10 * 1000, listener);
    }

    /**
     * 异步打开
     *
     * @param usbDevice 打开的usb设备
     * @param timeout   超时时间 ms
     * @param listener  回调监听
     */
    public static void openCamera(UsbDevice usbDevice, long timeout, @NonNull ICameraOpenListener listener) {
        if (!checkPermission(Manifest.permission.CAMERA)) {
            listener.failed("请检查权限");
            return;
        }
        // 在子线程中执行打开摄像头的操作
        executor.submit(() -> {
            try {
                // 调用异步方法，等待摄像头打开
                UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
                boolean requestSync = UsbPermissionHelper.requestPermissionSync(usbDevice, manager, timeout);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (!requestSync) {
                    listener.failed("申请usb临时权限失败");
                    return;
                }

                String[] deviceNames = loadSplit(usbDevice);
                if (deviceNames == null || deviceNames.length < 2) {
                    listener.failed("获取usb驱动信息失败");
                    return;
                }
                UsbDeviceConnection usbDeviceConnection = manager.openDevice(usbDevice);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (usbDeviceConnection == null) {
                    listener.failed("打开usb设备驱动失败");
                    return;
                }

                long nativeId = CameraNativeUtils.nativeOpen(usbDeviceConnection.getFileDescriptor(), getBusNum(deviceNames), getDevAddress(deviceNames));
                // 摄像头成功打开
                //如果打开成功，当前线程没有被中断，则返回数据，如果中断，则关闭当前
                if (Thread.currentThread().isInterrupted()) {
                    if (nativeId != 0L) {
                        CameraNativeUtils.nativeClose(nativeId);
                    }
                    usbDeviceConnection.close();
                    return;
                }
                if (nativeId == 0L) {
                    listener.failed("usb设备打开失败");
                    return;
                }
                listener.success(new ICameraDevice(nativeId, usbDevice.getDeviceName(), usbDeviceConnection));
            } catch (Exception e) {
                // 捕获异常并回调失败
                listener.failed("打开摄像头时发生错误: " + e.getMessage());
            }
        });
    }

    public static void cancel() {
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            if (!runningDevices.isEmpty()) {
                for (var entry : runningDevices.entrySet()) {
                    ICameraDevice device = entry.getKey();
                    CountDownLatch latch = entry.getValue();
                    device.close();
                    latch.countDown();
                }
                runningDevices.clear();
            }
        } catch (Exception ignored) {
        }
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
        return OverallContext.baseContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static String[] loadSplit(UsbDevice device) {
        String deviceName = device.getDeviceName();
        if (TextUtils.isEmpty(deviceName)) {
            return null;
        }
        return deviceName.split("/");

    }

    private static int getBusNum(String[] split) {
        try {
            return Integer.parseInt(split[split.length - 2]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int getDevAddress(String[] split) {
        try {
            return Integer.parseInt(split[split.length - 1]);
        } catch (Exception e) {
            return -1;
        }
    }
}

