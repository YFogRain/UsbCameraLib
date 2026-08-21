package com.rain.uvc.utils

import android.hardware.usb.UsbDevice

/**
 * 获取设备名称
 */
internal fun UsbDevice.loadSplit(): Array<String>? {
	val deviceName = this.deviceName
	if (deviceName.isEmpty()) return null
	return deviceName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}

internal fun Array<String>.getBusNum(): Int {
	return runCatching {
		this.getOrNull(this.size - 2)?.toInt()
	}.getOrNull() ?: -1
}

internal fun Array<String>.getDevAddress(): Int {
	return runCatching {
		this.getOrNull(this.size - 1)?.toInt()
	}.getOrNull() ?: -1
}
