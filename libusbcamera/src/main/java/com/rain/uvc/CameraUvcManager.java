package com.rain.uvc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.text.TextUtils;

import com.rain.uvc.camera.ICameraDevice;
import com.rain.uvc.camera.impl.CameraDeviceImpl;
import com.rain.uvc.listener.ICameraOpenListener;
import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.state.UvcCameraAccessException;
import com.rain.uvc.utils.CameraNativeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yuan
 * @createTime: 2025/1/10
 * @des
 */
public class CameraUvcManager {

    private static final Object mLock = new Object();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final ConcurrentHashMap<ICameraDevice, CountDownLatch> runningDevices = new ConcurrentHashMap<>();

    /**
     * debug模式开关
     */
    public static boolean debuggable(boolean status) {
        return CameraNativeUtils.debuggable(status ? 1 : 0);
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
     * 检查权限
     *
     * @param permissions 对应需要验证的权限
     * @return 权限是否已经拥有，false表示未拥有
     */
    private static boolean checkPermission(String... permissions) {
        if (permissions == null) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        for (String per : permissions) {
            if (!checkSinglePermission(per)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkSinglePermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        int result = OverallContext.baseContext.checkSelfPermission(permission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 同步打开
     * 需要在suspend内执行
     *
     * @param usbDevice 打开的usb设备
     * @return 返回打开后的操作对象
     */
    public static ICameraDevice openCameraSync(UsbDevice usbDevice) throws UvcCameraAccessException {
        if (checkPermission(Manifest.permission.CAMERA)) {
            throw new UvcCameraAccessException("请检查权限");
        }
        return openCameraSync(usbDevice, 10 * 1000);
    }

    /**
     * 同步打开
     * 需要在suspend内执行
     *
     * @param usbDevice 打开的usb设备
     * @param timeout   超时时间 ms
     * @return 返回打开后的操作对象
     */
    public static ICameraDevice openCameraSync(UsbDevice usbDevice, long timeout) throws UvcCameraAccessException {
        if (checkPermission(Manifest.permission.CAMERA)) {
            throw new UvcCameraAccessException("请检查权限");
        }
        return waitOpenCamera(usbDevice, timeout);
    }

    /**
     * 异步打开
     *
     * @param usbDevice 打开的usb设备
     * @param timeout   超时时间 ms
     * @param listener  回调监听
     */
    public static void openCamera(UsbDevice usbDevice, long timeout, ICameraOpenListener listener) {
        if (checkPermission(Manifest.permission.CAMERA)) {
            listener.failed("请检查权限");
            return;
        }
        // 在子线程中执行打开摄像头的操作
        executor.submit(() -> {
            try {
                // 调用异步方法，等待摄像头打开
                ICameraDevice cameraDevice = waitOpenCamera(usbDevice, timeout);
                // 摄像头成功打开
                listener.success(cameraDevice);
            } catch (UvcCameraAccessException e) {
                // 捕获异常并回调失败
                listener.failed("打开摄像头时发生错误: " + e.getMessage());
            }
        });
    }

    /**
     * 异步打开
     *
     * @param usbDevice 打开的usb设备
     * @param listener  回调监听
     */
    public static void openCamera(UsbDevice usbDevice, ICameraOpenListener listener) {
        if (checkPermission(Manifest.permission.CAMERA)) {
            listener.failed("请检查权限");
            return;
        }
        // 在子线程中执行打开摄像头的操作
        openCamera(usbDevice, 10 * 1000, listener);
    }


    /**
     * 等待打开设备
     *
     * @param usbDevice usb设备
     * @param timeout   超时时间ms
     * @return 打开后的操作对象
     */
    private static ICameraDevice waitOpenCamera(UsbDevice usbDevice, long timeout) throws UvcCameraAccessException {
        synchronized (mLock) {
            final CountDownLatch latch = new CountDownLatch(1);
            CameraDeviceImpl cameraDevice = new CameraDeviceImpl(usbDevice);
            AtomicBoolean isOpenSuccess = new AtomicBoolean(false);  // 使用 AtomicBoolean 来标识成功或失败
            StringBuilder errorMessage = new StringBuilder(); // 用于存储失败的错误信息
            runningDevices.put(cameraDevice, latch);//添加到打开队列
            cameraDevice.open(new ICameraDevice.ICameraDeviceListener() {
                @Override
                public void onSuccess() {
                    isOpenSuccess.set(true);
                    latch.countDown();
                }

                @Override
                public void onFailed(String message) {
                    //打开失败
                    isOpenSuccess.set(false);
                    errorMessage.append(message);
                    latch.countDown();
                }
            });
            try {
                if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                    cameraDevice.close();
                    throw new UvcCameraAccessException("打开usb设备超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();//恢复中断标志
                throw new UvcCameraAccessException("等待设备响应时发生中断");
            } finally {
                runningDevices.remove(cameraDevice);//打开完毕，移除队列
            }
            if (!isOpenSuccess.get()) {
                cameraDevice.close();
                throw new UvcCameraAccessException(errorMessage.length() > 0 ? errorMessage.toString() : "设备打开失败");
            }
            return cameraDevice;
        }
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
}

