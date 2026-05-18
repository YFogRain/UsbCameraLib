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
