package com.rain.uvc.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.TextureView
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * @author yuan
 * @createTime: 2025/10/27
 * @des camera图像处理类
 */

/**
 * bytes转为bitmap图像
 */
fun ByteArray.toBitmap(format: Int, width: Int, height: Int): Bitmap? {
	Log.d("format", "转为bitmap = $format,${width}*${height}")
	return when (format) {
		ImageFormat.JPEG -> BitmapFactory.decodeByteArray(this, 0, this.size)
		ImageFormat.YUV_420_888, ImageFormat.NV21 -> runCatching {
			val yuvImage = YuvImage(this@toBitmap, ImageFormat.NV21, width, height, null)
			ByteArrayOutputStream().apply {
				yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, this)
			}.use {
				val jpegBytes = it.toByteArray()
				BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
			}
		}.getOrNull()
		else -> null
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
	val normOrient = ((orientation % 360) + 360) % 360
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

/**
 * TextureView 设置图像变换矩阵，仅camera2适用
 */
fun TextureView.updateSurfaceTransformWithScale(previewWidth: Int, previewHeight: Int, orientation: Int) {
	val matrix = Matrix()
	val viewWidth = this.width.toFloat()
	val viewHeight = this.height.toFloat()
	val cx = viewWidth / 2f
	val cy = viewHeight / 2f
	// 2️⃣ 旋转
	if (orientation != 0) matrix.postRotate(orientation.toFloat(), cx, cy)
	
	// 4️⃣ 缩放，保证填充 TextureView
	val rotatedWidth = if (orientation == 90 || orientation == 270) previewHeight.toFloat() else previewWidth.toFloat()
	val rotatedHeight = if (orientation == 90 || orientation == 270) previewWidth.toFloat() else previewHeight.toFloat()
	
	// 计算缩放比例相对于 Bitmap 尺寸
	val scale = (viewWidth / rotatedWidth).coerceAtLeast(viewHeight / rotatedHeight)
	val scaleX = scale * rotatedWidth / viewWidth
	val scaleY = scale * rotatedHeight / viewHeight
	matrix.postScale(scaleX, scaleY, cx, cy)
	this.setTransform(matrix)
}

fun TextureView.applyCenterCrop(bufferWidth: Int, bufferHeight: Int, rotation: Int) {
	val viewWidth = width.toFloat()
	val viewHeight = height.toFloat()
	val matrix = Matrix()
	val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
	val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
	val centerX = viewRect.centerX()
	val centerY = viewRect.centerY()
	bufferRect.offset(
		centerX - bufferRect.centerX(), centerY - bufferRect.centerY()
	)
	
	matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
	
	val scale = maxOf(
		viewWidth / bufferWidth, viewHeight / bufferHeight
	)
	
	matrix.postScale(scale, scale, centerX, centerY)
	matrix.postRotate(rotation.toFloat(), centerX, centerY)
	
	setTransform(matrix)
}

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