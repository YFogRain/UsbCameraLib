package com.once.camera.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.max

/**
 * @author yuan
 * @createTime: 2025/10/27
 * @des camera图像处理类
 */
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
	val scaledBitmap = Bitmap.createBitmap(decodeBitmap, 0, 0, decodeBitmap.width, decodeBitmap.height, matrix, true).also {
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