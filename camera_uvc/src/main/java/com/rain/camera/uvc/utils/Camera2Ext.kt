package com.rain.camera.uvc.utils

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import java.nio.ByteBuffer

fun Array<Image.Plane>?.imageDataToBytes(format: Int, width: Int, height: Int): ByteArray? {
	if (this.isNullOrEmpty()) return null
	//如果当前是yuv的数据类型，并且，数据长度不是3个，则直接return
	if (format == ImageFormat.YUV_420_888) {
		return yuvToBytes()
	}
	val buffer = this[0].buffer
	if (!buffer.isAvailable()) return null
	//获取当前写入数据的具体长度
	val length = buffer.remaining()
	if (length <= 0) return null
	if (format == ImageFormat.NV21) {
		val bytes = ByteArray(length)
		buffer.get(bytes)
		return bytes
	}
	if (format == ImageFormat.JPEG) {
		val bytes = ByteArray(length)
		buffer.get(bytes)
		return bytes
	}
	if (format == PixelFormat.RGBA_8888) {
		val bytes = ByteArray(width * height * 4)
		buffer.get(bytes)
		return bytes
	}
	return null
}

private fun Array<Image.Plane>.yuvToBytes(): ByteArray? {
	if (this.size != 3) return null
	val yBuffer = this[0].buffer
	val uBuffer = this[1].buffer
	val vBuffer = this[2].buffer
	if (!yBuffer.isAvailable() || !uBuffer.isAvailable() || !vBuffer.isAvailable()) return null
	val lengthY = yBuffer.remaining()
	val lengthU = uBuffer.remaining()
	val lengthV = vBuffer.remaining()
	if (lengthY == 0 || lengthU == 0 || lengthV == 0) return null
	val outBytes = ByteArray(lengthY + lengthU + lengthV)
	yBuffer.get(outBytes, 0, lengthY)
	uBuffer.get(outBytes, lengthY, lengthU)
	vBuffer.get(outBytes, lengthY + lengthU, lengthV)
	return outBytes
}

fun IntArray?.loadFormatState(): Int? {
	if (this == null || this.isEmpty()) return null
	var isHaveJpegFormat = false
	var isHaveNv21Format = false
	var isHaveYuvFormat = false
	this.forEach {
		when (it) {
			ImageFormat.JPEG -> isHaveJpegFormat = true
			ImageFormat.NV21 -> isHaveNv21Format = true
			ImageFormat.YUV_420_888 -> isHaveYuvFormat = true
		}
	}
	if (isHaveJpegFormat) return ImageFormat.JPEG
	if (isHaveNv21Format) return ImageFormat.NV21
	if (isHaveYuvFormat) return ImageFormat.YUV_420_888
	return null
}

/**
 * 校验当前byteBuffer是否可用
 */
fun ByteBuffer?.isAvailable(): Boolean {
	if (this == null) return false
	return runCatching {
		this.capacity() > 0 && this.position() >= 0 && this.position() <= this.capacity() && this.limit() >= 0 && this.limit() <= this.capacity()
	}.getOrNull() ?: false
}

/**
 * 序列号并转换当前列表
 */
inline fun <reified K> IntArray?.filterMap(transform: (Int) -> K): Array<K>? {
	if (this == null || this.isEmpty()) return null
	val distinct = this.distinct()
	return Array(distinct.size) {
		transform(this[it])
	}
}

fun CameraCharacteristics.loadAEEnable(): Int? {
	val supportArray = this.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
	if (supportArray == null || supportArray.isEmpty()) return null
	return if (supportArray.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)) {
		CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE
	} else if (supportArray.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
		CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
	} else {
		CaptureRequest.CONTROL_AE_MODE_ON
	}
}

/**
 * 抽取清理方法，减少冗余
 */
fun ImageReader?.clearImageReaderQueue() {
	runCatching {
		while (true) {
			val image = this?.acquireNextImage() ?: break
			image.close()
		}
	}
}
