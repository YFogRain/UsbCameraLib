package com.once.camera.factory.usb.uvc

import android.graphics.Bitmap
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.once.camera.factory.ICameraDevice
import com.once.camera.parameters.CameraPreviewFormat
import com.once.camera.parameters.Parameters
import com.once.camera.parameters.SupportParameters

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des uvc相机的操作
 */
class CameraUvcDevice : ICameraDevice() {
	override fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean {
		TODO("Not yet implemented")
	}
	
	override  fun startPreview(): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun stopPreview(): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun setDisplaySurface(view: SurfaceView): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun setDisplaySurface(view: TextureView): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun setDisplaySurface(surface: Surface): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun <V> setParameter(key: Parameters.Key<V>, value: V): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun <V> getParameter(key: Parameters.Key<V>): V? {
		TODO("Not yet implemented")
	}
	
	override fun <T> getSupportParameters(supportKey: SupportParameters.Key<T>): T? {
		TODO("Not yet implemented")
	}
	
	override fun closeCamera(): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun startFaceDetection(): Boolean {
		TODO("Not yet implemented")
	}
	
	override fun stopFaceDetection(): Boolean {
		TODO("Not yet implemented")
	}
	
	override suspend fun takePicture(cropWidth:Int,cropHeight: Int): Bitmap? {
		TODO("Not yet implemented")
	}
}