package com.rain.uvc.demo.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.rain.uvc.provider.OverallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * @author yuan
 * @createTime: 2025/2/25
 * @des
 */
object UsbPermissionHelper {
	const val ACTION_USB_PERMISSION: String = "com.usb.permission.helper.USB_PERMISSION"
	
	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	suspend fun requestPermission(usbDevice: UsbDevice, timeout: Long): Boolean {
		val manager = OverallContext.baseContext.getSystemService(Context.USB_SERVICE) as UsbManager
		if (manager.hasPermission(usbDevice)) return true
		return withTimeoutOrNull(timeout) {
			val comparable = CompletableDeferred<Boolean>()
			val usbReceiver = object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					val action = intent?.action
					Log.d("UsbPermissionHelper", "action:$action")
					if (action.isNullOrEmpty() || action != ACTION_USB_PERMISSION) {
						//如果不是权限申请的类型，则直接跳过
						return
					}
					val receiverDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
					if (receiverDevice == null || receiverDevice.deviceName != usbDevice.deviceName) {
						//当前返回的权限值不是我们正在申请的
						return
					}
					comparable.complete(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
				}
			}
			//注册广播，接受对应的结果
			OverallContext.baseContext.registerReceiver(usbReceiver, IntentFilter(ACTION_USB_PERMISSION))
			
			//触发权限申请
			manager.requestPermission(usbDevice, PendingIntent.getBroadcast(OverallContext.baseContext, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT))
			try {
				return@withTimeoutOrNull comparable.await()
			} catch (e: Exception) {
				return@withTimeoutOrNull false
			} finally {
				OverallContext.baseContext.unregisterReceiver(usbReceiver)
			}
		} ?: false
	}
}