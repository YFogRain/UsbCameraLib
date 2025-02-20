package com.rain.uvc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;

/**
 * @author yuan
 * @createTime: 2025/2/20
 * @des usb移除消息回调
 */
public class UsbDetachedReceiver extends BroadcastReceiver {
    private final UsbEventListener mListener;
    private final String mDeviceName;

    public UsbDetachedReceiver(String mDeviceName, UsbEventListener listener) {
        this.mDeviceName = mDeviceName;
        this.mListener = listener;
    }


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
        if (mListener != null) {
            mListener.onDetached();
        }
    }

    //异步卸载回调
    public interface UsbEventListener {
        void onDetached();
    }
}

