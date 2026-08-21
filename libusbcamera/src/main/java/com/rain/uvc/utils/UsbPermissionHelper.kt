package com.rain.uvc.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * @author yuan
 * @createTime: 2026/2/2
 * @des
 */
object UsbPermissionHelper {
	// 申请权限的action
	private const val ACTION_USB_PERMISSION: String = "com.rain.usb.request.usb_permission"
	
	/**
	 * 权限申请
	 */
	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	suspend fun requestPermission(context: Context, usbDevice: UsbDevice, usbManager: UsbManager, timeoutMs: Long): Boolean {
		return withTimeoutOrNull(timeoutMs.milliseconds) {
			suspendCancellableCoroutine { continuation ->
				val usbReceiver = object : BroadcastReceiver() {
					override fun onReceive(context: Context, intent: Intent?) {
						val action = intent?.action
						if (action.isNullOrEmpty() || action != ACTION_USB_PERMISSION) return
						val receiverDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
						if (receiverDevice.deviceName != usbDevice.deviceName) return
						runCatching { context.unregisterReceiver(this) }
						if (continuation.isActive) {
							continuation.resume(
								intent.getBooleanExtra(
									UsbManager.EXTRA_PERMISSION_GRANTED, false
								)
							)
						}
					}
				}
				continuation.invokeOnCancellation {
					runCatching { context.unregisterReceiver(usbReceiver) }
				}
				val intentFilter = IntentFilter(ACTION_USB_PERMISSION)
				//注册广播，接受对应的结果
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					context.registerReceiver(usbReceiver, intentFilter, Context.RECEIVER_EXPORTED)
				} else {
					context.registerReceiver(usbReceiver, intentFilter)
				}
				// 权限申请的flag
				val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
					PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
				} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					PendingIntent.FLAG_MUTABLE
				} else PendingIntent.FLAG_UPDATE_CURRENT
				// 这里触发权限申请
				usbManager.requestPermission(
					usbDevice, PendingIntent.getBroadcast(
						context, usbDevice.deviceId, Intent(ACTION_USB_PERMISSION), flags
					)
				)
			}
		} ?: false
	}
	
}