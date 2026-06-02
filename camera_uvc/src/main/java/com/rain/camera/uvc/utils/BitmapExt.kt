package com.rain.camera.uvc.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * bytes转为bitmap图像 - 优化版（使用 RGB_565 减少内存占用）
 */
fun ByteArray.toBitmap(format: Int, width: Int, height: Int): Bitmap? {
	return when (format) {
		ImageFormat.JPEG -> BitmapFactory.decodeByteArray(this, 0, this.size)
		ImageFormat.YUV_420_888, ImageFormat.NV21 -> runCatching {
			val yuvImage = YuvImage(this@toBitmap, ImageFormat.NV21, width, height, null)
			ByteArrayOutputStream().use {
				yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, it)
				val jpegBytes = it.toByteArray()
				val options = BitmapFactory.Options().apply {
					inPreferredConfig = Bitmap.Config.RGB_565
				}
				BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
			}
			}.getOrNull()
		PixelFormat.RGBA_8888 -> runCatching {
			Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
				copyPixelsFromBuffer(ByteBuffer.wrap(this@toBitmap))
			}
		}.getOrNull()
		else -> null
	}
}

fun Bitmap.toJpegBytes(quality: Int = 100): ByteArray? {
	return runCatching {
		ByteArrayOutputStream().use { output ->
			check(compress(Bitmap.CompressFormat.JPEG, quality, output))
			output.toByteArray()
		}
	}.getOrNull()
}

fun ByteArray.rgba8888ToJpeg(width: Int, height: Int, quality: Int = 100): ByteArray? {
	val bitmap = toBitmap(PixelFormat.RGBA_8888, width, height) ?: return null
	return try {
		bitmap.toJpegBytes(quality)
	} finally {
		bitmap.recycle()
	}
}

fun ByteArray.rgba8888ToNv21(width: Int, height: Int): ByteArray? {
	if (width <= 0 || height <= 0) return null
	val rgbaSize = width * height * 4
	if (this.size < rgbaSize) return null
	val frameSize = width * height
	val out = ByteArray(frameSize + frameSize / 2)
	var rgbaIndex = 0
	var yIndex = 0
	var uvIndex = frameSize
	for (row in 0 until height) {
		for (col in 0 until width) {
			val r = this[rgbaIndex].toInt() and 0xff
			val g = this[rgbaIndex + 1].toInt() and 0xff
			val b = this[rgbaIndex + 2].toInt() and 0xff
			val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
			val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
			val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
			out[yIndex++] = y.coerceIn(0, 255).toByte()
			if ((row and 1) == 0 && (col and 1) == 0 && uvIndex + 1 < out.size) {
				out[uvIndex++] = v.coerceIn(0, 255).toByte()
				out[uvIndex++] = u.coerceIn(0, 255).toByte()
			}
			rgbaIndex += 4
		}
	}
	return out
}

fun Bitmap.toNv21Bytes(): ByteArray? {
	if (width <= 0 || height <= 0) return null
	val pixels = IntArray(width * height)
	getPixels(pixels, 0, width, 0, 0, width, height)
	val frameSize = width * height
	val out = ByteArray(frameSize + frameSize / 2)
	var yIndex = 0
	var uvIndex = frameSize
	var index = 0
	for (row in 0 until height) {
		for (col in 0 until width) {
			val color = pixels[index++]
			val r = color shr 16 and 0xff
			val g = color shr 8 and 0xff
			val b = color and 0xff
			val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
			val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
			val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
			out[yIndex++] = y.coerceIn(0, 255).toByte()
			if ((row and 1) == 0 && (col and 1) == 0 && uvIndex + 1 < out.size) {
				out[uvIndex++] = v.coerceIn(0, 255).toByte()
				out[uvIndex++] = u.coerceIn(0, 255).toByte()
			}
		}
	}
	return out
}

/**
 * 对bitmap进行裁剪缩放等处理
 */
fun Bitmap.cropBitmap(orientation: Int, isMirror: Boolean, cropWidth: Int = -1, cropHeight: Int = -1): Bitmap {
// 规范化角度（可能传入 360、-90 等）
	val normOrient = ((orientation % 360) + 360) % 360
	if (normOrient == 0 && !isMirror && (cropWidth <= 0 || cropHeight <= 0)) return this
	// 获取图像的中心坐标
	// 原图的信息
	val srcRect = RectF(0f, 0f, this.width.toFloat(), this.height.toFloat())
	// 构建变换矩阵
	val matrix = Matrix()
	// 执行旋转
	if (normOrient != 0) { // 旋转
		matrix.postRotate(normOrient.toFloat(), srcRect.centerX(), srcRect.centerY())
	}
	// 计算新的图像边界
	val mappedRect = RectF().apply {
		matrix.mapRect(this, srcRect)
	}
	// 镜像
	if (isMirror) matrix.postScale(-1f, 1f, mappedRect.centerX(), mappedRect.centerY())
	// 平移，使左上角回到原点（否则旋转后坐标不在 (0,0)）
	matrix.postTranslate(-mappedRect.left, -mappedRect.top)
	// 4.CENTER_CROP 缩放
	// 5️⃣ 如果需要缩放（CENTER_CROP 模式）
	if (cropWidth > 0 && cropHeight > 0) {
		val scale = max(cropWidth / mappedRect.width(), cropHeight / mappedRect.height())
		matrix.postScale(scale, scale, mappedRect.centerX(), mappedRect.centerY())
	}
	// 生成 Bitmap
	val scaledBitmap = Bitmap.createBitmap(
		this, 0, 0, this.width, this.height, matrix, true
	).also {
		this.recycle() // 释放旧的bitmap
	}
	// 如果没有进行缩放处理，则直接返回原图
	if (cropWidth <= 0 || cropHeight <= 0) return scaledBitmap
	// 再从中心裁剪到控件大小(因为一次矩阵变化无法做到精确裁剪，所以需要二次处理实际图)
	val startX = ((scaledBitmap.width - cropWidth) / 2f).coerceAtLeast(0f).toInt()
	val startY = ((scaledBitmap.height - cropHeight) / 2f).coerceAtLeast(0f).toInt()
	// 4️⃣ 应用变换生成新的 Bitmap
	return Bitmap.createBitmap(scaledBitmap, startX, startY, cropWidth, cropHeight).also {
		scaledBitmap.recycle()
	}
}

fun ByteArray.toBitmap(orientation: Int, isMirror: Boolean, cropWidth: Int = -1, cropHeight: Int = -1): Bitmap {
	// 1. 获取原始的bitmap图
	val decodeBitmap = BitmapFactory.decodeByteArray(this, 0, this.size)
	// 规范化角度（可能传入 360、-90 等）
	val normOrient = (((if (orientation < 0) 0 else orientation) % 360) + 360) % 360
	if (normOrient == 0 && !isMirror && (cropWidth <= 0 || cropHeight <= 0)) return decodeBitmap
	// 获取图像的中心坐标
	// 原图的信息
	val srcRect = RectF(0f, 0f, decodeBitmap.width.toFloat(), decodeBitmap.height.toFloat())
	// 构建变换矩阵
	val matrix = Matrix()
	// 执行旋转
	if (normOrient != 0) { // 旋转
		matrix.postRotate(normOrient.toFloat(), srcRect.centerX(), srcRect.centerY())
	}
	// 计算新的图像边界
	val mappedRect = RectF().apply {
		matrix.mapRect(this, srcRect)
	}
	// 镜像
	if (isMirror) matrix.postScale(-1f, 1f, mappedRect.centerX(), mappedRect.centerY())
	// 平移，使左上角回到原点（否则旋转后坐标不在 (0,0)）
	matrix.postTranslate(-mappedRect.left, -mappedRect.top)
	// 4.CENTER_CROP 缩放
	// 5️⃣ 如果需要缩放（CENTER_CROP 模式）
	if (cropWidth > 0 && cropHeight > 0) {
		val scale = max(cropWidth / mappedRect.width(), cropHeight / mappedRect.height())
		matrix.postScale(scale, scale, mappedRect.centerX(), mappedRect.centerY())
	}
	// 生成 Bitmap
	val scaledBitmap = Bitmap.createBitmap(
		decodeBitmap, 0, 0, decodeBitmap.width, decodeBitmap.height, matrix, true
	).also {
		decodeBitmap.recycle() // 释放旧的bitmap
	}
	// 如果没有进行缩放处理，则直接返回原图
	if (cropWidth <= 0 || cropHeight <= 0) return scaledBitmap
	// 再从中心裁剪到控件大小(因为一次矩阵变化无法做到精确裁剪，所以需要二次处理实际图)
	val startX = ((scaledBitmap.width - cropWidth) / 2f).coerceAtLeast(0f).toInt()
	val startY = ((scaledBitmap.height - cropHeight) / 2f).coerceAtLeast(0f).toInt()
	// 4️⃣ 应用变换生成新的 Bitmap
	return Bitmap.createBitmap(scaledBitmap, startX, startY, cropWidth, cropHeight).also {
		scaledBitmap.recycle()
	}
}


@RequiresOptIn(
	level = RequiresOptIn.Level.WARNING // 👈 关键：只是告警，不阻止使用
)
@Retention(AnnotationRetention.BINARY)
@Target(
	AnnotationTarget.CLASS,
	AnnotationTarget.FUNCTION,
	AnnotationTarget.PROPERTY
)
annotation class ExperimentalApi
