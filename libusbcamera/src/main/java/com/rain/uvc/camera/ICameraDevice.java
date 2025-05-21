package com.rain.uvc.camera;

import android.annotation.SuppressLint;
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
import android.view.SurfaceView;
import android.view.TextureView;

import com.rain.uvc.listener.IDetachedCloseListener;
import com.rain.uvc.listener.IFrameListener;
import com.rain.uvc.listener.PictureListener;
import com.rain.uvc.provider.OverallContext;
import com.rain.uvc.state.CameraDataFormat;
import com.rain.uvc.state.CameraParameter;
import com.rain.uvc.state.CameraPreviewFormat;
import com.rain.uvc.state.CameraSupportParameters;
import com.rain.uvc.state.RecordFormat;
import com.rain.uvc.utils.CameraNativeUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yuan
 * @createTime: 2025/2/20
 * @des
 */
public class ICameraDevice {
    //设备名称，如果为video时为设备路径，usb时通过name检查当前是否是同一个值的回调
    protected static AtomicLong mNativeAtomic = new AtomicLong(0L);
    private IDetachedCloseListener iDetachedCloseListener;
    //当前是否正在运行预览
    protected boolean isPreviewRunning;
    //打开结果回调
    private UsbDeviceConnection iUsbDeviceConnect;
    //当前是否注册广播成功
    private volatile boolean isReceiverSuccess;

    private final String mDeviceName;

    //usb移除回调监听
    private final BroadcastReceiver usbDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action) || !action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                //当前的action不是我们需要的action
                return;
            }
            //获取传递进来的usb设备
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (usbDevice == null || !mDeviceName.equals(usbDevice.getDeviceName())) {
                return;
            }
            //说明当前是需要的类型，直接回调结果
            if (iDetachedCloseListener != null) {
                close();
                iDetachedCloseListener.onDetach();
            }
        }
    };

    public ICameraDevice(long nativeId, String deviceName, UsbDeviceConnection connection) {
        mNativeAtomic.set(nativeId);
        this.mDeviceName = deviceName;
        this.iUsbDeviceConnect = connection;
        if (connection != null) {
            initReceiver();
        }
    }

    /**
     * 关闭摄像头
     */
    public boolean close() {
        unReceiver();
        this.iDetachedCloseListener = null;
        long nativeId = mNativeAtomic.get();
        if (nativeId != 0L) { //释放native层的资源
            CameraNativeUtils.nativeClose(nativeId);
        }
        mNativeAtomic.set(0L);
        if (iUsbDeviceConnect != null) {
            iUsbDeviceConnect.close();
            iUsbDeviceConnect = null;
        }
        return true;
    }

    /**
     * 开启预览
     */
    public boolean startPreview() {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        if (this.isPreviewRunning) {
            return true;
        }
        if (!CameraNativeUtils.nativeStartPreview(nativeId)) {//如果开启失败，则返回false
            return false;
        }
        this.isPreviewRunning = true;
        return true;
    }

    /**
     * 关闭预览
     */
    public boolean stopPreview() {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        boolean result = CameraNativeUtils.nativeStopPreview(nativeId);
        this.isPreviewRunning = false;
        return result;
    }

    /**
     * 当前相机是否已经打开
     */
    public boolean cameraIsOpen() {
        return mNativeAtomic.get() != 0L;
    }

    /**
     * 设置预览分辨率
     *
     * @param width  宽
     * @param height 高
     * @param format 使用的解码类型，当前只支持yuy2/mjpeg
     */
    public boolean setPreviewSize(int width, int height, CameraPreviewFormat format) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        return CameraNativeUtils.nativeSetPreviewSize(nativeId, width, height, format.getValue());
    }

    /**
     * 设置对应的预览控件
     *
     * @param surface 当前使用的预览控件
     * @return 是否设置成功
     */
    public boolean setDisplaySurface(Surface surface) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        return CameraNativeUtils.nativeSetDisplaySurface(nativeId, surface);
    }

    /**
     * 设置对应的预览控件
     *
     * @param view 当前使用的预览控件
     * @return 是否设置成功
     */
    public boolean setDisplaySurface(SurfaceView view) {
        return setDisplaySurface(view.getHolder().getSurface());
    }

    /**
     * 设置对应的预览控件
     *
     * @param view 当前使用的预览控件
     * @return 是否设置成功
     */
    public boolean setDisplaySurface(TextureView view) {
        return setDisplaySurface(new Surface(view.getSurfaceTexture()));
    }

    /**
     * 设置预览监听
     */
    public boolean setPreviewListener(IFrameListener listener, CameraDataFormat format) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        return CameraNativeUtils.setPreviewListener(nativeId, listener, format.getValue());
    }


    /**
     * 设置usb摄像头断开关闭回调
     */
    public boolean setDetachedCloseListener(IDetachedCloseListener listener) {
        this.iDetachedCloseListener = listener;
        return true;
    }

    /**
     * 设置参数
     */
    public <V> Boolean setParameter(CameraParameter.Key<V> key, V value) {
        return CameraNativeUtils.setParameter(mNativeAtomic.get(), key, value);
    }

    /**
     * 获取参数
     */
    public <T> T getParameter(CameraParameter.Key<T> key) {
        return CameraNativeUtils.getParameter(mNativeAtomic.get(), key);
    }

    /**
     * 获取支持的类型列表
     */
    public <T> T getSupportedParameter(CameraSupportParameters.Key<T> key) {
        return CameraNativeUtils.getSupportedParameter(mNativeAtomic.get(), key);
    }

    public boolean startRecord() {
        return startRecord(null);
    }

    public boolean startRecord(String fileName) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        return CameraNativeUtils.nativeStartRecord(nativeId, fileName);
    }

    public boolean stopRecord() {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return false;
        }
        return CameraNativeUtils.nativeStopRecord(nativeId);
    }

    public void setRecordFormat(RecordFormat format) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return;
        }
        CameraNativeUtils.nativeSetRecordFormat(nativeId, format.getValue());
    }

    public void setParentPath(String parentPath) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return;
        }
        CameraNativeUtils.nativeSetParentPath(nativeId, parentPath);
    }

    public String getRecordPath() {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return null;
        }
        return CameraNativeUtils.nativeGetRecordPath(nativeId);
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private synchronized void initReceiver() {
        if (isReceiverSuccess) return;
        try {
            OverallContext.baseContext.registerReceiver(usbDetachedReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
        } catch (Exception ignored) {
        }
        isReceiverSuccess = true;
    }


    private synchronized void unReceiver() {
        if (isReceiverSuccess) {
            try {
                OverallContext.baseContext.unregisterReceiver(usbDetachedReceiver);
            } catch (Exception ignored) {
            }
        }
        isReceiverSuccess = false;
    }


    public String takePicture(String parentPath) {
        return takePicture(parentPath, null);
    }

    public String takePicture(String parentPath, String fileName) {
        long nativeId = mNativeAtomic.get();
        if (nativeId == 0L) {
            return null;
        }
        String path = CameraNativeUtils.nativeTakePicture(nativeId, parentPath, fileName);
        Log.d("takePicture", "照片信息:" + path);
        return path;
    }
}

