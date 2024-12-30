package com.rain.uvc;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.rain.uvc.listener.ICameraOpenListener;
import com.rain.uvc.listener.IDetachedCloseListener;
import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.mode.FormatModeState;
import com.rain.uvc.mode.FormatRequestMode;
import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.state.CameraParameter;
import com.rain.uvc.state.CameraSupportParameters;
import com.rain.uvc.utils.CameraNativeUtils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yuan
 * @createTime: 2024/8/16
 * @des 摄像头操作类
 */
public class UvcCamera {
    private static final String ACTION_USB_PERMISSION = "com.dc.camera.uvc.permission.request";
    //当前主线程使用的handler，用于将回调等数据发送回主线程调用
    //usb设备缓存值,在需要重新打开，以及校验对应的操作时候使用
    private final AtomicReference<UsbDevice> usbCache = new AtomicReference<>();
    //当前打开的对应的jni层的内存地址值，操作摄像头用
    private final AtomicLong uvcNativeId = new AtomicLong(0L);
    //打开结果回调
    private ICameraOpenListener iOpenListener;
    //当前usb设备的连接驱动
    private UsbDeviceConnection iUsbDeviceConnect;
    //当前是否正在运行预览
    private boolean isPreviewRunning;
    //当前打开状态
    private volatile int currentOpenState; //1-打开成功，0-未打开，2-正在打开
    //当前是否注册广播成功
    private volatile boolean isReceiverSuccess;
    //usb设备移除监听，正在打开时，不会回调此方法
    private IDetachedCloseListener iDetachedCloseListener;
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            String action = intent.getAction();
            Log.d("UvcCamera", "接收到usb的action:" + action);
            if (TextUtils.isEmpty(action)) return;
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (usbDevice == null) return;
            UsbDevice localDevice = usbCache.get();
            if (localDevice == null) {
                if (currentOpenState == 2) {
                    resultOpen(false, "usb授权失败");
                }
                return;
            }
            Log.d("UvcCamera", "需要打开的设备DeviceName:" + localDevice.getDeviceName());
            Log.d("UvcCamera", "广播回调的设备DeviceName:" + usbDevice.getDeviceName());
            //当前不是当前设备的回调，则直接不处理
            if (!localDevice.getDeviceName().equals(usbDevice.getDeviceName())) {
                return;
            }
            //设备被移除
            if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                Log.i("UvcCamera", "无语，谁吧设备卸载咯～～～");
                //表示是当前设备
                if (currentOpenState == 2) {
                    resultOpen(false, "usb设备被移除");
                } else {
                    //回调设备移除监听
                    if (iDetachedCloseListener != null) {
                        iDetachedCloseListener.onDetach();
                    }
                    close();
                }
                return;
            }
            //如果不是usb权限回调，则直接忽略,如果不是正在打开，则也不需要处理
            if (!action.equals(ACTION_USB_PERMISSION) || currentOpenState != 2) return;
            boolean isGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            Log.d("UvcCamera", "收到权限回调啦：：" + isGranted);
            if (isGranted) {
                open();
            } else {
                resultOpen(false, "usb授权失败");
            }
        }
    };

    /**
     * 设置设备断开回调监听
     *
     * @param listener 回到监听器
     */
    public void setDetachedCloseListener(IDetachedCloseListener listener) {
        this.iDetachedCloseListener = listener;
    }

    /**
     * 打开对应的设备，必须要传递对应的usb设备，否则，无法打开
     *
     * @param device 当前的usb设备
     */
    protected UvcCamera(UsbDevice device) {
        usbCache.set(device);
    }

    /**
     * 打开对应摄像头驱动
     *
     * @param listener 打开结果监听
     */
    public void open(ICameraOpenListener listener) {
        if (currentOpenState == 2) {
            //当前正在打开中，直接返回
            Log.e("UvcCamera", "当前设备正在打开，请稍后重试");
            listener.failed("当前设备正在打开，请稍后重试");
            return;
        }
        currentOpenState = 2;
        initReceiver();
        this.iOpenListener = listener;
        //获取usb管理实例，来校验权限
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        UsbDevice usbDevice = usbCache.get();
        if (usbDevice == null) {
            Log.e("UvcCamera", "未获取到对应的usb设备驱动");
            resultOpen(false, "未获取到对应的usb设备驱动");
            return;
        }
        //判断是否存在usb权限，如果没有，则需要授权
        if (!manager.hasPermission(usbDevice)) {
            Log.e("UvcCamera", "没有usb权限，开始检查权限");
            PendingIntent broadcast = PendingIntent.getBroadcast(OverallContext.baseContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
            manager.requestPermission(usbDevice, broadcast);
            return;
        }
        //如果有权限，则直接打开
        open();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private synchronized void initReceiver() {
        if (isReceiverSuccess) return;
        IntentFilter intentFilter = new IntentFilter(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        try {
            OverallContext.baseContext.registerReceiver(usbReceiver, intentFilter);
        } catch (Exception ignored) {
        }
        isReceiverSuccess = true;
    }


    private synchronized void unReceiver() {
        if (isReceiverSuccess) {
            try {
                OverallContext.baseContext.unregisterReceiver(usbReceiver);
            } catch (Exception ignored) {
            }
        }
        isReceiverSuccess = false;
    }

    /**
     * 打开设备
     */
    private void open() {
        //当前已经打开成功
        if (currentOpenState == 1) {
            Log.e("UvcCamera", "涩北已经打开了，你们不要再打了～");
            resultOpen(true, "设备已经打开");
            return;
        }
        UsbDevice usbDevice = usbCache.get();
        if (usbDevice == null) {
            Log.e("UvcCamera", "没有设置驱动，不知道找谁了");
            resultOpen(false, "未传入对应的usb设备驱动");
            return;
        }
        String[] deviceNames = loadSplit(usbDevice);
        if (deviceNames == null || deviceNames.length < 2) {
            resultOpen(false, "检查usb驱动失败");
            return;
        }
        //初始化内存空间
        long nativeId = CameraNativeUtils.nativeCreate();
        uvcNativeId.set(nativeId);
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection usbDeviceConnection = manager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            Log.e("UvcCamera", "打开usb设备驱动失败");
            close();
            resultOpen(false, "打开usb设备驱动失败");
            return;
        }
        iUsbDeviceConnect = usbDeviceConnection;
        boolean result = CameraNativeUtils.nativeConnectFd(nativeId, usbDeviceConnection.getFileDescriptor(), getBusNum(deviceNames), getDevAddress(deviceNames), getUSBFSName(deviceNames));
        if (!result) {
            Log.d("UvcCamera", "连接usb设备失败");
            close();
            resultOpen(false, "连接usb设备失败");
            return;
        }
        Log.d("UvcCamera", "啊，可算打开成功了～");
        resultOpen(true, "打开成功");
    }

    /**
     * 设置对应的预览回调
     *
     * @param listener 预览回调监听
     */
    public boolean setPreviewListener(IFrameListener listener, FormatRequestMode mode) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        CameraNativeUtils.setPreviewListener(nativeId, listener, mode.getValue());
        return true;
    }

    /**
     * 设置对应的预览控件
     *
     * @param surface 当前使用的预览控件
     * @return 是否设置成功
     */
    public boolean setDisplaySurface(Surface surface) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        return CameraNativeUtils.nativeSetDisplaySurface(nativeId, surface);
    }

    /**
     * 更新当前分辨率，需要停止预览
     *
     * @param width      宽
     * @param height     高
     * @param formatSate 使用的格式类型
     */

    public boolean updatePreviewSize(int width, int height, FormatModeState formatSate) {
        boolean lastRunningState = isPreviewRunning;
        //停止预览
        stopPreview();
        //设置预览分辨率
        boolean result = setPreviewSize(width, height, formatSate);
        //恢复预览
        if (lastRunningState && result) {
            result = startPreview();
        }
        return result;
    }

    /**
     * 开启预览
     *
     * @return 是否开启成功
     */
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

    /**
     * 关闭预览
     *
     * @return 是否关闭成功
     */
    public boolean stopPreview() {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        boolean result = CameraNativeUtils.nativeStopPreview(nativeId);
        if (result) {
            isPreviewRunning = false;
        }
        return result;
    }

    /**
     * 获取对应的参数属性
     *
     * @param key 支持获取的参数key
     * @param <T> 当前返回的参数对应的类型
     * @return 返回对应的值，根据key的具体类型来
     */
    public <T> T getParameter(CameraParameter.Key<T> key) {
        return CameraNativeUtils.getParameter(uvcNativeId.get(), key);
    }

    /**
     * 获取支持的参数信息
     *
     * @param key 支持获取的参数key
     * @param <T> 当前返回的参数对应的类型
     * @return 返回对应的值，根据key的具体类型来
     */
    public <T> T getSupportedParameter(CameraSupportParameters.Key<T> key) {
        return CameraNativeUtils.getSupportedParameter(uvcNativeId.get(), key);
    }


    /**
     * 设置对应的参数属性
     *
     * @param key   支持获取的参数key
     * @param <T>   当前返回的参数对应的类型
     * @param value 需要设置的值
     * @return 返回对应的值，根据key的具体类型来
     */
    public <T> boolean setParameter(CameraParameter.Key<T> key, T value) {
        return CameraNativeUtils.setParameter(uvcNativeId.get(), key, value);
    }

    /**
     * 设置预览分辨率
     * 根据当前宽高，匹配缓存列表中的宽高信息，获取对应的fps，mode
     *
     * @param width       宽
     * @param height      高
     * @param formatState 使用的分辨率类型（当前仅支持nv21和mjpeg）
     */
    public boolean setPreviewSize(int width, int height, FormatModeState formatState) {
        long nativeId = uvcNativeId.get();
        if (nativeId == 0L) return false;
        return CameraNativeUtils.nativeSetPreviewSize(nativeId, width, height, formatState.getValue());
    }

    /**
     * 关闭摄像头
     */
    public void close() {
        //移除监听器
        unReceiver();
        long nativeId = uvcNativeId.get();
        if (nativeId != 0L) {
            //断开连接
            CameraNativeUtils.nativeDisConnect(nativeId);
            //销毁jni申请的内存
            CameraNativeUtils.nativeDestroy(nativeId);
            uvcNativeId.set(0L);
        }
        if (iUsbDeviceConnect != null) {
            iUsbDeviceConnect.close();
            iUsbDeviceConnect = null;
        }
        iDetachedCloseListener = null;
        currentOpenState = 0;

    }

    private void resultOpen(boolean result, String message) {
        currentOpenState = result ? 1 : 0;
        if (iOpenListener == null) return;
        if (result) {
            iOpenListener.success();
            iOpenListener = null;
            return;
        }
        iOpenListener.failed(message);
        iOpenListener = null;
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

    private String getUSBFSName(String[] split) {
        if (split == null || split.length <= 2) {
            return "/dev/bus/usb";
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(split[0]);
        for (int i = 1; i < split.length - 2; i++) {
            stringBuilder.append("/").append(split[i]);
        }
        return stringBuilder.toString().trim();
    }
}

