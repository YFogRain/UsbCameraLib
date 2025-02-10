package com.rain.uvc.camera.impl;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import com.rain.uvc.camera.CameraDevice;
import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.state.CameraDataFormat;
import com.rain.uvc.state.CameraPreviewFormat;
import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.state.CameraParameter;
import com.rain.uvc.state.CameraSupportParameters;
import com.rain.uvc.utils.CameraNativeUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yuan
 * @createTime: 2025/1/10
 * @des
 */
public class CameraDeviceImpl extends CameraDevice {
    //当前打开的对应的jni层的内存地址值，操作摄像头用
    private final AtomicLong uvcNativeId = new AtomicLong(0L);
    //打开摄像头启动的子线程，保证打开不会造成主线程卡死
    private Thread openThread;
    //当前usb设备的连接驱动
    private UsbDeviceConnection iUsbDeviceConnect;

    public CameraDeviceImpl(UsbDevice device) {
        super(device);
    }

    @Override
    protected void openCamera() {
        openThread = new Thread(() -> {
            try {
                //当前已经打开成功
                UsbDevice usbDevice = mUsbDevice.get();
                if (usbDevice == null) {
                    Log.e("UvcCamera", "没有设置驱动，不知道找谁了");
                    resultOpen(false, "未传入对应的usb设备驱动");
                    return;
                }
                String[] deviceNames = loadSplit(usbDevice);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (deviceNames == null || deviceNames.length < 2) {
                    resultOpen(false, "检查usb驱动失败");
                    return;
                }
                //初始化内存空间
                UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
                UsbDeviceConnection usbDeviceConnection = manager.openDevice(usbDevice);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (usbDeviceConnection == null) {
                    Log.e("UvcCamera", "打开usb设备驱动失败");
                    close();
                    resultOpen(false, "打开usb设备驱动失败");
                    return;
                }
                iUsbDeviceConnect = usbDeviceConnection;
                long nativeId = CameraNativeUtils.nativeOpen(usbDeviceConnection.getFileDescriptor(), getBusNum(deviceNames), getDevAddress(deviceNames));
                uvcNativeId.set(nativeId);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (nativeId == 0L) {
                    Log.d("UvcCamera", "连接usb设备失败");
                    close();
                    resultOpen(false, "连接usb设备失败");
                    return;
                }
                Log.d("UvcCamera", "啊，可算打开成功了～");
                resultOpen(true, "打开成功");
            } catch (Exception e) {
                resultOpen(false, "打开摄像头发生异常");
            }
        });
        openThread.start();
    }

    @Override
    protected boolean closeCamera() {//异步打开需要异步结束，打开完成后才需要同步结束状态
        //如果当前线程正在活跃中，则中断
        if (openThread != null && openThread.isAlive()) {
            openThread.interrupt();
            try {
                // 等待线程终止
                openThread.join();
            } catch (InterruptedException e) {
                // 可以选择抛出异常或其他处理逻辑
            }
        }
        openThread = null;
        long nativeId = uvcNativeId.get();
        if (nativeId != 0L) {
            //断开连接
            CameraNativeUtils.nativeClose(nativeId);
            uvcNativeId.set(0L);
        }
        if (iUsbDeviceConnect != null) {
            iUsbDeviceConnect.close();
            iUsbDeviceConnect = null;
        }
        return true;
    }

    @Override
    public boolean startPreview() {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        if (isPreviewRunning) {
            return true;
        }
        boolean result = CameraNativeUtils.nativeStartPreview(nativeId);
        if (result) {
            isPreviewRunning = true;
        }
        return result;
    }

    @Override
    public boolean stopPreview() {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        boolean result = CameraNativeUtils.nativeStopPreview(nativeId);
        if (result) {
            isPreviewRunning = false;
        }
        return result;
    }

    @Override
    public boolean setPreviewSize(int width, int height, CameraPreviewFormat formatState) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        return CameraNativeUtils.nativeSetPreviewSize(nativeId, width, height, formatState.getValue());
    }

    @Override
    public boolean setDisplaySurface(Surface surface) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        return CameraNativeUtils.nativeSetDisplaySurface(nativeId, surface);
    }

    @Override
    public boolean setDisplaySurface(SurfaceView view) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        Surface surface = view.getHolder().getSurface();
        if (surface == null) {
            return false;
        }
        return CameraNativeUtils.nativeSetDisplaySurface(nativeId, surface);
    }

    @Override
    public boolean setDisplaySurface(TextureView view) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        return CameraNativeUtils.nativeSetDisplaySurface(nativeId, new Surface(view.getSurfaceTexture()));
    }

    @Override
    public <T> boolean setParameter(CameraParameter.Key<T> key, T value) {
        return CameraNativeUtils.setParameter(uvcNativeId.get(), key, value);
    }

    @Override
    public <T> T getParameter(CameraParameter.Key<T> key) {
        return CameraNativeUtils.getParameter(uvcNativeId.get(), key);
    }

    @Override
    public <T> T getSupportedParameter(CameraSupportParameters.Key<T> key) {
        return CameraNativeUtils.getSupportedParameter(uvcNativeId.get(), key);
    }

    @Override
    public boolean cameraIsOpen() {
        return uvcNativeId.get() != 0L;
    }

    @Override
    public boolean setPreviewListener(IFrameListener listener, CameraDataFormat format) {
        return CameraNativeUtils.setPreviewListener(uvcNativeId.get(), listener, format.getValue());
    }

    private String[] loadSplit(UsbDevice device) {
        String deviceName = device.getDeviceName();
        if (TextUtils.isEmpty(deviceName)) {
            return null;
        }
        return deviceName.split("/");

    }

    private int getBusNum(String[] split) {
        try {
            return Integer.parseInt(split[split.length - 2]);
        } catch (Exception e) {
            return -1;
        }
    }

    private int getDevAddress(String[] split) {
        try {
            return Integer.parseInt(split[split.length - 1]);
        } catch (Exception e) {
            return -1;
        }
    }
}

