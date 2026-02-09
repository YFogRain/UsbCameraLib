package com.rain.uvc.camera.protogenesis.camera2

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.view.Surface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.coroutines.resume

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
inline fun <T, reified K> Array<T>?.filterMap(transform: (T) -> K): Array<K>? {
	if (this.isNullOrEmpty()) return null
	val distinct = this.distinct()
	return Array(distinct.size) {
		transform(this[it])
	}
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

fun CameraDevice.loadAEEnable(context: Context): Int? {
	val cameraId = this.id
	if (cameraId.isEmpty()) return null
	val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
	val cameraCharacteristics = manager.getCameraCharacteristics(cameraId) //获取对应的参数信息
	val supportArray = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
	if (supportArray == null || supportArray.isEmpty()) return null
	return if (supportArray.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)) {
		CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE
	} else if (supportArray.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
		CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
	} else {
		CaptureRequest.CONTROL_AE_MODE_ON
	}
}

object Camera2CommonExt {
	
	suspend fun createSession(device: CameraDevice, surface: Surface?, imageReader: ImageReader?, handler: Handler?): Result<CameraCaptureSession> {
		return runCatching {
			withTimeout(3000) {
				suspendCancellableCoroutine { continuation ->
					device.createCaptureSession(ArrayList<Surface>().also {
						if (surface != null) it.add(surface)
						if (imageReader != null) it.add(imageReader.surface)
					}, object : CameraCaptureSession.StateCallback() {
						override fun onConfigured(session: CameraCaptureSession) {
							continuation.resume(Result.success(session))
						}
						
						override fun onConfigureFailed(session: CameraCaptureSession) {
							continuation.resume(Result.failure(IllegalStateException("创建session错误")))
						}
					}, handler)
				}
			}
		}.getOrNull() ?: Result.failure(IllegalStateException("创建session错误"))
	}
	
	@JvmStatic
	fun imageDataToBytes(format: Int, planes: Array<Image.Plane>?, width: Int, height: Int): ByteArray? {
		if (planes.isNullOrEmpty()) return null
		//如果当前是yuv的数据类型，并且，数据长度不是3个，则直接return
		if (format == ImageFormat.YUV_420_888) {
			return yuvToBytes(planes)
		}
		val buffer = planes[0].buffer
		if (!buffer.isAvailable()) return null
		//获取当前写入数据的具体长度
		val length = buffer.remaining()
		if (length <= 0) return null
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
	
	private fun yuvToBytes(planes: Array<Image.Plane>): ByteArray? {
		if (planes.size != 3) return null
		val yBuffer = planes[0].buffer
		val uBuffer = planes[1].buffer
		val vBuffer = planes[2].buffer
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
	
	
	@JvmStatic
	fun loadFormatState(formats: IntArray?): Int? {
		if (formats == null || formats.isEmpty()) return null
		var isHaveJpegFormat = false
		var isHaveNv21Format = false
		var isHaveYuvFormat = false
		formats.forEach {
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
}