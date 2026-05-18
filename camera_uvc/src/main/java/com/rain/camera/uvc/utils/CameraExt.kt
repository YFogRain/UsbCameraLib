package com.rain.camera.uvc.utils

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.view.TextureView
import com.rain.camera.uvc.factory.NativeUsbDevice
import com.rain.camera.uvc.parameter.UvcCameraParameter

/**
 * 更新textureView的宽高
 */
fun NativeUsbDevice.updateTexture(view: TextureView) {
	val (width, height) = this.getParameter(UvcCameraParameter.PREVIEW_SIZE) ?: return
	Log.i("CameraBusUtils", "当前的分辨率 = ${width}*$height")
	view.surfaceTexture?.setDefaultBufferSize(width, height)
	view.applyCenterCrop(
		width, height, this.getParameter(UvcCameraParameter.ORIENTATION) ?: 0
	)
}

/**
 * textureView执行缩放
 */
fun TextureView.applyCenterCrop(bufferWidth: Int, bufferHeight: Int, rotation: Int) {
	Log.i("CameraBusUtils", "旋转方向？ = $rotation")
	val viewWidth = width.toFloat()
	val viewHeight = height.toFloat()
	val matrix = Matrix()
	val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
	val bufferRect = RectF(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat())
	val centerX = viewRect.centerX()
	val centerY = viewRect.centerY()
	bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
	matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
	// 根据旋转方向计算宽高缩放比
	val (scaleWidth, scaleHeight) = if (rotation == 90 || rotation == 270) {
		bufferHeight to bufferWidth
	} else bufferWidth to bufferHeight
	// 取最大值，保证正常缩放
	val scale = maxOf(viewWidth / scaleWidth, viewHeight / scaleHeight)
	matrix.postScale(scale, scale, centerX, centerY)
	matrix.postRotate(rotation.toFloat(), centerX, centerY)
	setTransform(matrix)
}
