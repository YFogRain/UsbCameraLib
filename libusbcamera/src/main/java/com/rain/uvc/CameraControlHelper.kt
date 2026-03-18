@file:Suppress("DEPRECATION")

package com.rain.uvc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.camera.protogenesis.camera1.Camera1Device
import com.rain.uvc.camera.protogenesis.camera2.Camera2Device
import com.rain.uvc.camera.uvc.CameraUvcDevice
import com.rain.uvc.utils.CameraNativeUtils
import com.rain.uvc.utils.UsbPermissionHelper
import com.rain.uvc.utils.getBusNum
import com.rain.uvc.utils.getDevAddress
import com.rain.uvc.utils.loadSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des 摄像头控制操作帮助类
 */
object CameraControlHelper {
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
	 * 获取可加载的系统摄像头个数
	 */
	@JvmStatic
	fun loadNative1Cameras(): Int {
		val cameraNumber = runCatching { Camera.getNumberOfCameras() }.getOrNull()
		if (cameraNumber == null || cameraNumber <= 0) return 0
		return 1
	}
	
	/**
	 * 获取可加载的系统摄像头个数
	 */
	@JvmStatic
	fun loadNative2Cameras(context: Context): Array<String>? {
		val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
		val cameraList = runCatching {
			manager.cameraIdList
		}.getOrNull()
		if (cameraList.isNullOrEmpty()) return null
		return cameraList
	}
	
	/**
	 * 获取camera2前置摄像头的id，如果不存在，则返回第一个摄像头id
	 */
	fun loadCamera2Front(context: Context): String? {
		val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
		val cameraList = runCatching {
			manager.cameraIdList
		}.getOrNull()
		if (cameraList.isNullOrEmpty()) return null
		// 1️⃣ 优先查找前置摄像头
		return cameraList.find {
			val characteristics = runCatching { manager.getCameraCharacteristics(it) }.getOrNull() ?: return@find false
			val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
			facing == CameraCharacteristics.LENS_FACING_FRONT
		} ?: cameraList[0]
	}
	
	/**
	 * 获取camera1的前置摄像头id
	 */
	fun loadCamera1Front(): Int? {
		val cameraNumber = runCatching { Camera.getNumberOfCameras() }.getOrNull()
		if (cameraNumber == null || cameraNumber <= 0) return null
		val cameraInfo = Camera.CameraInfo()
		// 1️⃣ 优先查找前置摄像头
		for (cameraId in 0 until cameraNumber) {
			runCatching { Camera.getCameraInfo(cameraId, cameraInfo) }.getOrNull() ?: continue
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				return cameraId
			}
		}
		return 0
	}
	
	/**
	 * 是否是摄像头类型
	 */
	@JvmStatic
	fun checkDeviceUvc(usbDevice: UsbDevice): Boolean {
		//当前类型不是video类型
		if (usbDevice.deviceClass != 239 && usbDevice.deviceSubclass != 2) return false
		return usbDevice.productName?.lowercase().let {
			it.isNullOrEmpty() || !it.contains("android")
		}
	}
	
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
	suspend fun openCamera(context: Context, usbDevice: UsbDevice, timeout: Long = 10 * 1000): Result<ICameraDevice> {
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
			return@withContext Result.success(
				CameraUvcDevice(
					context, nativeId, usbDevice.deviceName, "${usbDevice.vendorId}-${usbDevice.productId}", usbDeviceConnection
				)
			)
		}
	}
	
	/**
	 * 打开camera1
	 */
	suspend fun openCamera(context: Context, cameraId: Int, acRotation: Int = 0): Result<ICameraDevice> {
		if (!checkPermission(context)) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		val native1Cameras = runCatching { Camera.getNumberOfCameras() }.getOrNull() ?: 0
		if (native1Cameras <= cameraId) {
			return Result.failure(CameraStateException("打开的摄像头已经超出范围"))
		}
		
		val cameraResult = withContext(Dispatchers.IO) {
			return@withContext runCatching { Camera.open(cameraId) }.onFailure { it.printStackTrace() }
		}
		val camera = cameraResult.getOrNull() ?: return Result.failure(CameraStateException("打开摄像头失败"))
		return Result.success(Camera1Device(camera, cameraId, acRotation))
	}
	
	/**
	 * 打开camera2
	 */
	suspend fun openCamera(context: Context, cameraId: String, acRotation: Int = 0, timeout: Long = 5 * 1000): Result<ICameraDevice> {
		if (!checkPermission(context)) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		return try {
			withTimeout(timeout) {
				suspendCancellableCoroutine { continuation ->
					val camera2Device = Camera2Device(context)
					continuation.invokeOnCancellation {
						camera2Device.cancelOpen()
					}
					camera2Device.open(context, cameraId, acRotation) { isSuccess, message ->
						if (continuation.isActive) {
							val result = if (isSuccess) {
								Result.success(camera2Device)
							} else {
								camera2Device.close() // 确保失败时释放资源
								Result.failure(CameraStateException(message))
							}
							continuation.resume(result)
						}
					}
				}
			}
		} catch (_: Exception) {
			Result.failure(CameraStateException("设备打开失败"))
		}
	}
	
	/**
	 * 检查权限
	 *
	 * @return 权限是否已经拥有，false表示未拥有
	 */
	private fun checkPermission(context: Context): Boolean {
		return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
	}
}