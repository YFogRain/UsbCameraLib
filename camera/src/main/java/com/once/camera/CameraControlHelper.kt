package com.once.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.once.camera.factory.ICameraDevice
import com.once.camera.factory.protogenesis.camera1.Camera1Device
import com.once.camera.factory.protogenesis.camera2.Camera2Device
import com.once.camera.provider.OverallContext
import com.once.camera.utils.UsbPermissionHelper
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
	fun loadUvcDevices(): List<UsbDevice>? {
		val manager = OverallContext.baseContext.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
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
	fun loadNative2Cameras(): Array<String>? {
		val manager = OverallContext.baseContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
		val cameraList = runCatching {
			manager.cameraIdList
		}.getOrNull()
		if (cameraList.isNullOrEmpty()) return null
		return cameraList
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
	fun getDeviceForId(vId: Int, pId: Int): UsbDevice? {
		val manager = OverallContext.baseContext.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
		return manager.deviceList.values.find { it.vendorId == vId && it.productId == pId }
	}
	
	/**
	 * 打开摄像头
	 */
	fun openCamera(usbDevice: UsbDevice, timeout: Long = 10 * 1000): Result<ICameraDevice> {
		if (!checkPermission()) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		val manager = OverallContext.baseContext.getSystemService(Context.USB_SERVICE) as UsbManager
		if (!UsbPermissionHelper.requestPermissionSync(usbDevice, manager, timeout)) {
			return Result.failure(CameraStateException("申请usb临时权限失败"))
		}
		val usbDeviceConnection = manager.openDevice(usbDevice)
		if (usbDeviceConnection == null) {
			return Result.failure(CameraStateException("打开usb设备驱动失败"))
		}
//		val nativeId: Long = CameraNativeUtils.nativeOpen(usbDeviceConnection.getFileDescriptor(), com.rain.uvc.CameraUvcManager.getBusNum(deviceNames), com.rain.uvc.CameraUvcManager.getDevAddress(deviceNames))
//		if (nativeId == 0L) {
//			throw CameraStateException("usb设备打开失败")
//		}
//		Log.d("CameraUvcManager", usbDevice.getDeviceName() + "-当前设备的内存映射id为:" + nativeId)
//		return Camera1Device(nativeId, usbDevice.getDeviceName(), usbDeviceConnection)
		return Result.failure(CameraStateException("打开的摄像头已经超出范围"))
	}
	
	/**
	 * 打开camera1
	 */
	suspend fun openCamera(cameraId: Int, acRotation: Int): Result<ICameraDevice> {
		if (!checkPermission()) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		val native1Cameras = runCatching { Camera.getNumberOfCameras() }.getOrNull() ?: 0
		if (native1Cameras <= cameraId) {
			return Result.failure(CameraStateException("打开的摄像头已经超出范围"))
		}
		
		val cameraResult = withContext(Dispatchers.IO) {
			return@withContext runCatching { Camera.open(cameraId) }.onFailure { it.printStackTrace() }
		}
		val camera = cameraResult.getOrNull()
		if (camera == null) {
			return Result.failure(CameraStateException("打开摄像头失败"))
		}
		return Result.success(Camera1Device(camera, cameraId, acRotation))
	}
	
	/**
	 * 打开camera2
	 */
	suspend fun openCamera(cameraId: String, acRotation: Int, timeout: Long = 5 * 1000): Result<ICameraDevice> {
		if (!checkPermission()) {
			return Result.failure(CameraStateException("请检查权限"))
		}
		return try {
			withTimeout(timeout) {
				suspendCancellableCoroutine { continuation ->
					val camera2Device = Camera2Device()
					continuation.invokeOnCancellation {
						camera2Device.close()
					}
					camera2Device.open(cameraId, acRotation) { isSuccess, message ->
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
	fun checkPermission(): Boolean {
		return OverallContext.baseContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
	}
}