package com.rain.uvc.utils

import android.hardware.usb.UsbDevice

/**
 * 获取设备名称
 */
fun UsbDevice.loadSplit(): Array<String>? {
	val deviceName = this.deviceName
	if (deviceName.isEmpty()) return null
	return deviceName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}

fun Array<String>.getBusNum(): Int {
	return runCatching {
		this.getOrNull(this.size - 2)?.toInt()
	}.getOrNull() ?: -1
}

fun Array<String>.getDevAddress(): Int {
	return runCatching {
		this.getOrNull(this.size - 1)?.toInt()
	}.getOrNull() ?: -1
}
