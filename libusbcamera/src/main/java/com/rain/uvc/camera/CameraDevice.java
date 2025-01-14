package com.rain.uvc.camera;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import com.rain.uvc.listener.IDetachedCloseListener;
import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.mode.CameraPreviewFormat;
import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.state.CameraParameter;
import com.rain.uvc.state.CameraSupportParameters;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yuan
 * @createTime: 2025/1/10
 * @des 对外使用的CameraDevice
 */
public abstract class CameraDevice {
    private static final String ACTION_USB_PERMISSION = "com.dc.camera.uvc.permission.request";
    //当前缓存的usb设备信息
    protected final AtomicReference<UsbDevice> mUsbDevice = new AtomicReference<>();
    //打开结果回调
    private ICameraDeviceListener iOpenListener;
    //当前是否正在运行预览
    protected boolean isPreviewRunning;
    //当前打开状态
    protected volatile boolean currentOpenIngState;
    //当前是否注册广播成功
    private volatile boolean isReceiverSuccess;
    //usb设备移除监听，正在打开时，不会回调此方法
    private IDetachedCloseListener iDetachedCloseListener;

    //usb回调广播，监听权限回调，断开连接
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) return;
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            UsbDevice localDevice = mUsbDevice.get();
            if (usbDevice == null || localDevice == null || !localDevice.getDeviceName().equals(usbDevice.getDeviceName())) {
                return;
            }
            //设备移除
            if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                if (currentOpenIngState) {
                    resultOpen(false, "usb设备被移除");
                } else {
                    close();
                    //回调设备移除监听
                    if (iDetachedCloseListener != null) {
                        iDetachedCloseListener.onDetach();
                    }

                }
                return;
            }
            //如果不是usb权限回调，则直接忽略,如果不是正在打开，则也不需要处理
            if (!action.equals(ACTION_USB_PERMISSION) || !currentOpenIngState) return;
            boolean isGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            Log.d("CameraDevice", "收到权限回调啦：：" + isGranted);
            if (isGranted) {
                openCamera();
            } else {
                resultOpen(false, "usb授权失败");
            }

        }
    };

    public CameraDevice(UsbDevice device) {
        this.mUsbDevice.set(device);
    }


    /**
     * 打开对应摄像头驱动
     *
     * @param listener 打开结果监听
     */
    public void open(ICameraDeviceListener listener) {
        if (currentOpenIngState || cameraIsOpen()) {
            //当前正在打开中，直接返回
            Log.e("UvcCamera", "当前设备正在打开，请稍后重试");
            listener.onFailed("当前设备正在打开，请稍后重试");
            return;
        }
        currentOpenIngState = true;
        initReceiver();
        this.iOpenListener = listener;
        //获取usb管理实例，来校验权限
        UsbManager manager = (UsbManager) OverallContext.baseContext.getSystemService(Context.USB_SERVICE);
        UsbDevice usbDevice = mUsbDevice.get();
        if (usbDevice == null) {
            Log.e("UvcCamera", "未获取到对应的usb设备驱动");
            resultOpen(false, "未获取到对应的usb设备驱动");
            return;
        }
        //判断是否存在usb权限，如果没有，则需要授权
        if (!manager.hasPermission(usbDevice)) {
            Log.e("UvcCamera", "没有usb权限，开始检查权限");
            PendingIntent broadcast = PendingIntent.getBroadcast(OverallContext.baseContext, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT);
            manager.requestPermission(usbDevice, broadcast);
            return;
        }
        //如果有权限，则直接打开
        openCamera();
    }

    public boolean close() {
        //移除监听器
        unReceiver();
        currentOpenIngState = false;
        iDetachedCloseListener = null;
        boolean result = closeCamera();
        iOpenListener = null;
        return result;
    }

    protected abstract void openCamera();

    protected abstract boolean closeCamera();

    public abstract boolean startPreview();


    public abstract boolean stopPreview();

    public abstract boolean setPreviewSize(int width, int height, CameraPreviewFormat formatState);


    /**
     * 设置对应的预览控件
     *
     * @param surface 当前使用的预览控件
     * @return 是否设置成功
     */
    public abstract boolean setDisplaySurface(Surface surface);

    /**
     * 设置对应的预览控件
     *
     * @param view 当前使用的预览控件
     * @return 是否设置成功
     */
    public abstract boolean setDisplaySurface(SurfaceView view);

    /**
     * 设置对应的预览控件
     *
     * @param view 当前使用的预览控件
     * @return 是否设置成功
     */
    public abstract boolean setDisplaySurface(TextureView view);

    /**
     * 设置对应的参数属性
     *
     * @param key   支持获取的参数key
     * @param <T>   当前返回的参数对应的类型
     * @param value 需要设置的值
     * @return 返回对应的值，根据key的具体类型来
     */
    public abstract <T> boolean setParameter(CameraParameter.Key<T> key, T value);

    /**
     * 获取对应的参数属性
     *
     * @param key 支持获取的参数key
     * @param <T> 当前返回的参数对应的类型
     * @return 返回对应的值，根据key的具体类型来
     */
    public abstract <T> T getParameter(CameraParameter.Key<T> key);

    /**
     * 获取支持的参数信息
     *
     * @param key 支持获取的参数key
     * @param <T> 当前返回的参数对应的类型
     * @return 返回对应的值，根据key的具体类型来
     */
    public abstract <T> T getSupportedParameter(CameraSupportParameters.Key<T> key);

    /**
     * 设置设备断开回调监听
     *
     * @param listener 回到监听器
     */
    public void setDetachedCloseListener(IDetachedCloseListener listener) {
        this.iDetachedCloseListener = listener;
    }

    /**
     * 当前是否已经打开
     */
    public abstract boolean cameraIsOpen();

    /**
     * 当前是否正在预览
     */
    public boolean cameraIsPreviewing() {
        return this.isPreviewRunning;
    }

    /**
     * 设置对应的预览回调
     *
     * @param listener 预览回调监听
     */
    public abstract boolean setPreviewListener(IFrameListener listener);

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

    protected void resultOpen(boolean result, String message) {
        currentOpenIngState = false;
        if (iOpenListener == null) return;
        if (result) {
            iOpenListener.onSuccess();
            iOpenListener = null;
            return;
        }
        iOpenListener.onFailed(message);
        iOpenListener = null;
    }

    public interface ICameraDeviceListener {
        void onSuccess();

        void onFailed(String message);
    }
}

