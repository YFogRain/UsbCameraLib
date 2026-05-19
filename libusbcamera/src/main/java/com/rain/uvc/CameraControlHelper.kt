@file:Suppress("DEPRECATION")

package com.rain.uvc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.rain.uvc.bridge.UvcNativeBridge
import com.rain.uvc.factory.NativeUsbDevice
import com.rain.uvc.utils.UsbPermissionHelper
import com.rain.uvc.utils.getBusNum
import com.rain.uvc.utils.getDevAddress
import com.rain.uvc.utils.loadSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des 摄像头控制操作帮助类
 */
object CameraControlHelper {
	
	/**
	 * native日志开关
	 */
	@JvmStatic
	fun debuggable(status: Boolean) {
		UvcNativeBridge.debuggable(status)
	}
	
	/**
	 * 获取uvc的摄像头列表
	 *
	 * @return 返回摄像头列表
	 */
	@JvmStatic
	fun loadUvcDevices(context: Context): List<UsbDevice>? {
		val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
		return manager.deviceList.values.filter { checkDeviceUvc(it) }
	}
	
	/**
	 * 获取uvc的摄像头列表
	 *
	 * @return 返回摄像头列表
	 */
	fun loadV4L2Devices(): Array<String>? {
		return UvcNativeBridge.nativeLoadV4L2Devices()
	}
	
	/**
	 * 是否是摄像头类型
	 */
	@JvmStatic
	fun checkDeviceUvc(usbDevice: UsbDevice): Boolean {
		if (usbDevice.deviceClass != 239 || usbDevice.deviceSubclass != 2) return false
		return usbDevice.productName?.lowercase().let {
			it.isNullOrEmpty() || !it.contains("android")
		}
	}
	
	/**
	 * 根据设备id获取usb设备
	 */
	@JvmStatic
	fun getDeviceForId(context: Context, vId: Int, pId: Int): UsbDevice? {
		val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
		return manager.deviceList.values.find { it.vendorId == vId && it.productId == pId }
	}
	
	/**
	 * 同步打开
	 * 需要在子线程内执行
	 *
	 * @param usbDevice 打开的usb设备
	 * @param timeout   超时时间
	 * @return 返回打开后的操作对象
	 */
	suspend fun openCamera(context: Context, usbDevice: UsbDevice, timeout: Long = 10 * 1000): Result<NativeUsbDevice> {
		if (!checkPermission(context)) {
			return Result.failure(IllegalAccessException("请检查权限"))
		}
		val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
		if (!UsbPermissionHelper.requestPermission(context, usbDevice, manager, timeout)) {
			return Result.failure(IllegalAccessException("申请usb临时权限失败"))
		}
		return withContext(Dispatchers.IO) {
			val deviceNames = usbDevice.loadSplit()
			if (deviceNames == null || deviceNames.size < 2) {
				return@withContext Result.failure(
					IllegalStateException("获取usb驱动信息失败")
				)
			}
			val usbDeviceConnection = manager.openDevice(usbDevice) ?: return@withContext Result.failure(
				IllegalStateException("打开usb设备驱动失败")
			)
			val nativeId = UvcNativeBridge.nativeOpen(
				usbDeviceConnection.fileDescriptor,
				deviceNames.getBusNum(),
				deviceNames.getDevAddress()
			)
			if (nativeId == 0L) {
				usbDeviceConnection.close()
				return@withContext Result.failure(
					IllegalStateException("usb设备打开失败")
				)
			}
			return@withContext Result.success(
				NativeUsbDevice(
					context, nativeId, usbDevice.deviceName, usbDeviceConnection
				)
			)
		}
	}
	
	/**
	 * video类型打开
	 *
	 * @param videoPath /dev/videoX路径
	 * @return 打开后的操作对象
	 * @throws IllegalAccessException 异常信息
	 */
	suspend fun openCamera(context: Context, videoPath: String): Result<NativeUsbDevice> {
		if (!checkPermission(context)) {
			return Result.failure(IllegalAccessException("请检查权限"))
		}
		if (videoPath.isEmpty()) {
			return Result.failure(IllegalAccessException("请检查输入的路径"))
		}
		val nativeId = withContext(Dispatchers.IO) {
			return@withContext UvcNativeBridge.nativeOpenVideo(videoPath)
		}
		if (nativeId == 0L) {
			return Result.failure(IllegalAccessException("请检查当前路径权限且为video类型"))
		}
		return Result.success(NativeUsbDevice(context, nativeId, videoPath, null))
	}
	
	/**
	 * 检查权限
	 *
	 * @return 权限是否已经拥有，false表示未拥有
	 */
	@JvmStatic
	private fun checkPermission(context: Context): Boolean {
		return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
	}
}