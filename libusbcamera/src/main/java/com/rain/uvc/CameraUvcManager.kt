package com.rain.uvc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.rain.uvc.camera.NCameraDevice
import com.rain.uvc.utils.CameraNativeUtils
import com.rain.uvc.utils.UsbPermissionHelper
import com.rain.uvc.utils.getBusNum
import com.rain.uvc.utils.getDevAddress
import com.rain.uvc.utils.loadSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * @author yuan
 * @createTime: 2026/2/2
 * @des 摄像头控制操作帮助类
 */
object CameraUvcManager {
	/**
	 * debug模式开关
	 */
	fun debuggable(status: Boolean): Boolean {
		return CameraNativeUtils.debuggable(if (status) 1 else 0)
	}
	
	/**
	 * 获取uvc的摄像头列表
	 *
	 * @return 返回摄像头列表
	 */
	fun getV4L2Devices(): Array<String>? {
		return CameraNativeUtils.nativeLoadV4L2Devices()
	}
	
	/**
	 * 获取uvc的摄像头列表
	 *
	 * @return 返回摄像头列表
	 */
	fun getCameraDevices(context: Context): List<UsbDevice>? {
		val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
		return manager.deviceList?.values?.filter { checkDeviceUvc(it) }
	}
	
	/**
	 * 是否是摄像头类型
	 */
	fun checkDeviceUvc(usbDevice: UsbDevice): Boolean {
		//校验类型是否是usb摄像头
		if (usbDevice.deviceClass != 239 && usbDevice.deviceSubclass != 2) {
			return false
		}
		//处理特殊的情况
		if (usbDevice.productId == 24581 || usbDevice.productId == 33054) {
			return false
		}
		val productName = usbDevice.productName
		//检查是否存在android字样，如果存在，则说明是Android的系统设备，非摄像头
		return productName.isNullOrEmpty() || !productName.contains("android", true)
	}
	
	/**
	 * 根据vId和pId获取对应的usb设备
	 */
	fun getDeviceForId(context: Context, vId: Int, pId: Int): UsbDevice? {
		val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
		manager.deviceList.forEach {
			val device = it.value
			if (device.vendorId == vId && device.productId == pId) return device
		}
		return null
	}
	
	/**
	 * video类型打开
	 *
	 * @param videoPath /dev/videoX路径
	 * @return 打开后的操作对象
	 * @throws CameraStateException 异常信息
	 */
	suspend fun openCamera(context: Context, videoPath: String): Result<NCameraDevice> {
		if (!checkPermission(context)) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		if (videoPath.isEmpty()) {
			return Result.failure(CameraStateException("请检查输入的路径"))
		}
		val nativeId = withContext(Dispatchers.IO) {
			return@withContext CameraNativeUtils.nativeOpenVideo(videoPath)
		}
		if (nativeId == 0L) {
			return Result.failure(CameraStateException("请检查当前路径权限且为video类型"))
		}
		return Result.success(NCameraDevice(context, nativeId, videoPath, null))
	}
	
	/**
	 * 同步打开
	 * 需要在子线程内执行
	 *
	 * @param usbDevice 打开的usb设备
	 * @param timeout   超时时间
	 * @return 返回打开后的操作对象
	 */
	suspend fun openCamera(context: Context, usbDevice: UsbDevice, timeout: Long = 10 * 1000): Result<NCameraDevice> {
		if (!checkPermission(context)) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
		if (!UsbPermissionHelper.requestPermission(context, usbDevice, manager, timeout)) {
			return Result.failure(CameraStateException("申请usb临时权限失败"))
		}
		return withContext(Dispatchers.IO) {
			val deviceNames = usbDevice.loadSplit()
			if (deviceNames == null || deviceNames.size < 2) {
				return@withContext Result.failure(
					CameraStateException("获取usb驱动信息失败")
				)
			}
			val usbDeviceConnection = manager.openDevice(usbDevice) ?: return@withContext Result.failure(
				CameraStateException("打开usb设备驱动失败")
			)
			val nativeId = CameraNativeUtils.nativeOpen(
				usbDeviceConnection.fileDescriptor, deviceNames.getBusNum(), deviceNames.getDevAddress()
			)
			if (nativeId == 0L) {
				return@withContext Result.failure(
					CameraStateException("usb设备打开失败")
				)
			}
			Log.d("CameraUvcManager", usbDevice.deviceName + "-当前设备的内存映射id为:" + nativeId)
			return@withContext Result.success(
				NCameraDevice(
					context, nativeId, usbDevice.deviceName, usbDeviceConnection
				)
			)
		}
	}
	
	/**
	 * 检查相机权限
	 *
	 * @return 权限是否已经拥有，false表示未拥有
	 */
	@JvmStatic
	private fun checkPermission(context: Context): Boolean {
		return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
	}
}