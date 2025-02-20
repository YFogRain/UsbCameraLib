package com.rain.uvc.utils;

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

import androidx.annotation.NonNull;

import com.rain.uvc.provider.OverallContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yuan
 * @createTime: 2025/2/20
 * @des 权限申请，异步转同步操作
 */
public class UsbPermissionHelper {

    private static final String ACTION_USB_PERMISSION = "com.example.USB_PERMISSION";

    /**
     * 权限申请，等待超时时间
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static boolean requestPermissionSync(@NonNull UsbDevice usbDevice, UsbManager manager, long timeoutMs) {
        if (manager.hasPermission(usbDevice)) {//如果已经有权限，则直接返回结果，不需要申请权限
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);  // 初始化锁
        final AtomicBoolean permissionGranted = new AtomicBoolean(false);
        BroadcastReceiver usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d("UsbPermissionHelper", "action:" + action);
                if (TextUtils.isEmpty(action) || !action.equals(ACTION_USB_PERMISSION)) {
                    //如果不是权限申请的类型，则直接跳过
                    return;
                }
                UsbDevice receiverDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d("UsbPermissionHelper", "receiverDevice" + receiverDevice);
                if (receiverDevice == null || !receiverDevice.getDeviceName().equals(usbDevice.getDeviceName())) {
                    //当前返回的权限值不是我们正在申请的
                    return;
                }

                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d("UsbPermissionHelper", "已经有权限了～");
                        permissionGranted.set(true);
                    }
                    latch.countDown();  // 解锁
                }

            }
        };
        //注册广播，接受对应的结果
        OverallContext.baseContext.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        //触发权限申请
        manager.requestPermission(usbDevice, PendingIntent.getBroadcast(OverallContext.baseContext, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            // 设置超时等待权限（阻塞，等待或超时）
            boolean success = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!success) {
                Log.e("UsbPermissionHelper", "Timeout while waiting for permission.");
            }
        } catch (InterruptedException e) {
            Log.e("UsbPermissionHelper", "Permission request interrupted.", e);
        }
        // 注销广播接收器
        OverallContext.baseContext.unregisterReceiver(usbReceiver);
        return permissionGranted.get();
    }


}

