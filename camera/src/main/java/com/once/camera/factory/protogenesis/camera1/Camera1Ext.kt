package com.once.camera.factory.protogenesis.camera1

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.hardware.Camera
import android.view.TextureView
import android.view.View
import com.once.camera.parameters.CameraPreviewFormat
import kotlin.math.max

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des camera1的操作工具
 */
/**
 * 转换为camera1对应的预览格式
 */
fun CameraPreviewFormat.toFormat(): Int {
	return when (this) {
		CameraPreviewFormat.BGR -> ImageFormat.RGB_565
		CameraPreviewFormat.YUY2 -> ImageFormat.YUY2
		CameraPreviewFormat.NV21 -> ImageFormat.NV21
		CameraPreviewFormat.NV12 -> ImageFormat.NV21
		CameraPreviewFormat.RGB -> ImageFormat.RGB_565
		CameraPreviewFormat.MJPEG -> ImageFormat.JPEG
		CameraPreviewFormat.JPEG -> ImageFormat.JPEG
		CameraPreviewFormat.YUV_420_888 -> ImageFormat.YUV_420_888
	}
}

fun Int.isUserFormat(): Boolean {
	return when (this) {
		ImageFormat.NV21, ImageFormat.YUY2, ImageFormat.RGB_565, ImageFormat.YUV_420_888, ImageFormat.JPEG -> true
		else -> false
	}
}

/**
 * 返回对应的预览格式
 */
fun Int.toPreviewFormat(): CameraPreviewFormat {
	return when (this) {
		ImageFormat.NV21 -> CameraPreviewFormat.NV21
		ImageFormat.YUY2 -> CameraPreviewFormat.YUY2
		ImageFormat.RGB_565 -> CameraPreviewFormat.RGB
		ImageFormat.YUV_420_888 -> CameraPreviewFormat.YUV_420_888
		ImageFormat.JPEG -> CameraPreviewFormat.JPEG
		else -> CameraPreviewFormat.NV21
	}
}

/**
 * 获取可预览的格式
 */
fun Camera.Parameters.loadPreviewFormat(): Int? {
	val previewFormats = this.supportedPreviewFormats
	if (previewFormats.isNullOrEmpty()) return null
	//如果存在nv21
	var format = previewFormats.find { it == ImageFormat.NV21 }
	if (format == null) {
		//获取yuv420sp格式，4:2:2
		format = previewFormats.find { it == ImageFormat.YUV_420_888 }
	}
	if (format == null) {
		//获取jpeg格式
		format = previewFormats.find { it == ImageFormat.JPEG }
	}
	return format
}

/**
 * 序列号并转换当前列表
 */
inline fun <T, reified K> List<T>?.filterMap(transform: (T) -> K): Array<K>? {
	if (this.isNullOrEmpty()) return null
	val distinct = this.distinct()
	return Array(distinct.size) {
		transform(this[it])
	}
}

fun List<Camera.Size>.checkSize(width: Int, height: Int): Camera.Size {
	var resultSize = this.find { size -> size.width == width && size.height == height }
	if (resultSize != null) return resultSize
	resultSize = this.find { size -> size.height == width && size.width == height }
	if (resultSize != null) return resultSize
	return this[0]
}

/**
 * 加载对应区间
 */
fun Camera.Parameters.loadDifferenceData(): IntArray? {
	val previewFpsRange = this.supportedPreviewFpsRange
	if (previewFpsRange.isNullOrEmpty()) return null
	val optimalRange = previewFpsRange.maxByOrNull { it[1] - it[0] } ?: previewFpsRange[0]
	if (optimalRange.size != 2) return null
	return optimalRange
}

fun Camera.Parameters.loadMaxFrameRate(): Int? {
	if (this.supportedPreviewFrameRates.isNullOrEmpty()) return null
	return this.supportedPreviewFrameRates.maxOf { it }
}

/**
 * camera1的区间参数配置类
 */
fun Camera.Parameters.loadBanding(): String? {
	val antiBanding = this.supportedAntibanding
	if (antiBanding.isNullOrEmpty()) return null
	var antiSize = antiBanding.find { it == Camera.Parameters.ANTIBANDING_AUTO }
	if (antiSize.isNullOrEmpty()) {
		antiSize = antiBanding.find { it == Camera.Parameters.ANTIBANDING_OFF }
	}
	if (antiSize.isNullOrEmpty()) antiSize = antiBanding[0]
	return antiSize
}

/**
 * bytes转bitmap，数据为jpeg格式的,顺便根据原图进行镜像处理
 */
fun ByteArray.bytesToBitmap(isMirror: Boolean): Bitmap? {
	val bitmap = BitmapFactory.decodeByteArray(this, 0, this.size) ?: return null
	if (!isMirror) return bitmap
	// 2. 构建变换矩阵（跟预览 TextureView 一样的规则）
	// 3. 应用矩阵生成新 Bitmap
	// 缩放比例，保证填充整个 TextureView
	return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().also {
		it.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
	}, true)
}