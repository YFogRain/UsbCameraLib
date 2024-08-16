package com.rain.uvc.demo.utils

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.getSystemService
import com.rain.uvc.provider.OverallContext

/**
 */
object JudgeConfig {
	val rgbUvcIds = intArrayOf(1010, 1012, 99107, 12554, 4116, 4118, 4114, 1014, 4112, 1409, 25451, 1321, 529)
	var uvcRGBList: MutableList<Int>? = null
	var uvcIRList: MutableList<Int>? = null
	var native1RGBId: Int? = null
	var native2RGBId: String? = null
}

object UsbCameraUtils {
	/**
	 * 根据usbId获取对应的usb设备
	 */
	@JvmStatic
	fun loadUsbDeviceForId(vId: Int, pId: Int): UsbDevice? {
		if (vId == -1 || pId == -1) return null
		val devices = OverallContext.baseContext.getSystemService<UsbManager>() ?: return null
		val deviceList = runCatching { devices.deviceList }.getOrNull()?.values
		if (deviceList.isNullOrEmpty()) return null
		return deviceList.find { it.vendorId == vId && it.productId == pId }
	}
	
	/**
	 * 获取对应模式的usb设备
	 */
	@JvmStatic
	fun loadUsbCameraDevice(): UsbDevice? {
		val usbDevices = loadCameraDevices()
		//如果未找到设备列表，则直接return
		if (usbDevices.isNullOrEmpty()) return null
		var uvcIndex = -1
		//遍历获取对应的usb设备是否存在
		for (i in 0 until usbDevices.size) {
			val usbDevice = usbDevices[i]
			val usbType = loadCameraMode(usbDevice) ?: continue
			when (usbType) {
				//uvc校验是否时RGB，深度流不适用uvc
				1 -> {
					if (isUvcRGBCamera(usbDevice)) {
						uvcIndex = i
						break
					} else if (isUvcIRCamera(usbDevice)) {
						uvcIndex = i
						break
					} else {
						if (uvcIndex == -1) uvcIndex = i
					}
				}
				else -> {}
			}
		}
		//返回对应的usb设备
		return if (uvcIndex != -1) usbDevices[uvcIndex] else null
	}
	
	/**
	 * 获取当前设备对应的类型
	 * 如果匹配失败，则表示不是可识别的类型
	 */
	@JvmStatic
	fun loadCameraMode(usbDevice: UsbDevice): Int? {
		if (isPetrelNICamera(usbDevice)) return 2
		if (isHjCamera(usbDevice)) return 3
		return if (isUvcCamera(usbDevice)) 1 else null
	}
	
	/**
	 * 获取usb设备驱动列表
	 */
	@JvmStatic
	fun loadCameraDevices(): MutableList<UsbDevice>? {
		val devices = OverallContext.baseContext.getSystemService<UsbManager>() ?: return null
		val deviceList = runCatching { devices.deviceList }.getOrNull()?.values?.filter {
			loadCameraMode(it) != null
		}
		if (deviceList.isNullOrEmpty()) return null
		return deviceList.toMutableList()
	}
	
	/**
	 * 是否是摄像头类型
	 */
	@JvmStatic
	private fun isUvcCamera(usbDevice: UsbDevice): Boolean {
		return usbDevice.deviceClass == 239 && usbDevice.deviceSubclass == 2 && when (usbDevice.productId) {
			24581, 33054 -> false
			else -> true
		} && !usbDevice.productName.run { !this.isNullOrEmpty() && this.contains("Android", true) }
	}
	
	/**
	 * 判断华捷摄像头类型
	 */
	private fun isHjCamera(usbDevice: UsbDevice): Boolean {
		val manufacturerName = usbDevice.manufacturerName
		val productName = usbDevice.productName
		if (manufacturerName.isNullOrEmpty() || productName.isNullOrEmpty()) return false
		return manufacturerName.contains("Sonix Technology") && productName.contains("A200")
	}
	
	/**
	 * 判断海燕摄像头类型
	 */
	@JvmStatic
	private fun isPetrelNICamera(usbDevice: UsbDevice): Boolean {
		Log.d("cameraUsbTag", "productId:${usbDevice.productId},vendorId:${usbDevice.vendorId}")
		val isMore = (usbDevice.productId > 1279 || usbDevice.productId < 1024) && (usbDevice.productId > 1791 || usbDevice.productId < 1537)
		return usbDevice.vendorId == 11205 && !isMore && !usbDevice.productName.run { !this.isNullOrEmpty() && this.contains("I3", true) }
	}
	
	/**
	 * 是否是rgb的摄像头
	 */
	@JvmStatic
	private fun isUvcRGBCamera(usbDevice: UsbDevice): Boolean {
		val isUvcRgb = usbDevice.productName.run {
			!this.isNullOrEmpty() && this.contains("I3", true) && !this.contains("IR", true)
		}
		if (isUvcRgb) return true
		if (JudgeConfig.rgbUvcIds.contains(usbDevice.productId)) return true
		val uvcRGBList = JudgeConfig.uvcRGBList
		return !uvcRGBList.isNullOrEmpty() && uvcRGBList.contains(usbDevice.productId)
	}
	
	/**
	 * 是否是rgb的摄像头
	 */
	@JvmStatic
	private fun isUvcIRCamera(usbDevice: UsbDevice): Boolean {
		val isUvcRgb = usbDevice.productName.run {
			!this.isNullOrEmpty() && this.contains("I3", true) && !this.contains("IR", true)
		}
		if (isUvcRgb) return true
		val uvcIRList = JudgeConfig.uvcIRList
		if (!uvcIRList.isNullOrEmpty()) {
			if (uvcIRList.contains((usbDevice.productId))) return true
		}
		return !JudgeConfig.rgbUvcIds.contains(usbDevice.productId)
	}
	
}