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
		return yuvToNv21(width, height)
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

private fun Array<Image.Plane>.yuvToNv21(width: Int, height: Int): ByteArray? {
	if (this.size != 3) return null
	if (width <= 0 || height <= 0) return null
	val yPlane = this[0]
	val uPlane = this[1]
	val vPlane = this[2]
	val yBuffer = yPlane.buffer.duplicate()
	val uBuffer = uPlane.buffer.duplicate()
	val vBuffer = vPlane.buffer.duplicate()
	if (!yBuffer.isAvailable() || !uBuffer.isAvailable() || !vBuffer.isAvailable()) return null
	val frameSize = width * height
	val outBytes = ByteArray(frameSize + frameSize / 2)
	var yIndex = 0
	for (row in 0 until height) {
		val rowStart = row * yPlane.rowStride
		for (col in 0 until width) {
			val srcIndex = rowStart + col * yPlane.pixelStride
			if (srcIndex >= yBuffer.limit()) return null
			outBytes[yIndex++] = yBuffer.get(srcIndex)
		}
	}
	val chromaWidth = width / 2
	val chromaHeight = height / 2
	var uvIndex = frameSize
	for (row in 0 until chromaHeight) {
		val uRowStart = row * uPlane.rowStride
		val vRowStart = row * vPlane.rowStride
		for (col in 0 until chromaWidth) {
			val vIndex = vRowStart + col * vPlane.pixelStride
			val uIndex = uRowStart + col * uPlane.pixelStride
			if (vIndex >= vBuffer.limit() || uIndex >= uBuffer.limit()) return null
			outBytes[uvIndex++] = vBuffer.get(vIndex)
			outBytes[uvIndex++] = uBuffer.get(uIndex)
		}
	}
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
