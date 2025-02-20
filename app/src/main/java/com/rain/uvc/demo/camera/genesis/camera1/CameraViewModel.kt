@file:Suppress("DEPRECATION")

package com.rain.uvc.demo.camera.genesis.camera1

import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.viewModelScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des
 */
class CameraViewModel : BaseViewModel() {
	
	private val mCameraAtomic = AtomicReference<Camera>()
	
	@Volatile
	private var isRunningOpen: Boolean = false
	
	@Volatile
	private var isPreviewIng: Boolean = false
	
	fun openCamera(cameraId: Int, block: () -> Unit) {
		isRunningOpen = true
		viewModelScope.launch(Dispatchers.IO) {
			val native1Cameras = runCatching { Camera.getNumberOfCameras() }.getOrNull() ?: 0
			if (native1Cameras <= cameraId) {
				isRunningOpen = false
				return@launch
			}
			//打开摄像头
			val cameraResult = runCatching { Camera.open(cameraId) }.onFailure { it.printStackTrace() }
			val camera = cameraResult.getOrNull()
			if (camera == null) {
				isRunningOpen = false
				return@launch
			}
			//配置基础参数
			initParameter(camera)
			mCameraAtomic.set(camera)
			isRunningOpen = false
			block.invoke()
		}
	}
	
	private fun initParameter(camera: Camera) {
		val parameters = camera.parameters ?: return
		parameters.previewFormat = ImageFormat.NV21
		parameters.setPreviewSize(640, 480)
		//设置支持视频防抖
		if ("true" == parameters.get("video-stabilization-supported")) {
			parameters.set("video-stabilization", "true")
		}
		//闪光灯模式
		val supportedFlashModes = parameters.supportedFlashModes
		if (!supportedFlashModes.isNullOrEmpty() && supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
			parameters.flashMode = Camera.Parameters.FLASH_MODE_AUTO
		}
		//预览帧率
		runCatching { camera.enableShutterSound(false) }
		runCatching { camera.parameters = parameters }.onFailure { it.printStackTrace() }
		
	}
	
	fun startPreview(view: SurfaceView) {
		if (isPreviewIng) return
		val camera = mCameraAtomic.get() ?: return
		camera.setPreviewDisplay(view.holder)
		camera.setPreviewCallback { _, _ ->
			if (mCameraAtomic.get() == null) return@setPreviewCallback
		}
		val previewResult = runCatching { camera.startPreview() }.onFailure { it.printStackTrace() }.isSuccess
		if (previewResult) {
			isPreviewIng = true
		}
	}
	
	private fun stopPreview() {
		if (isPreviewIng) {
			val camera = mCameraAtomic.get()
			camera?.stopPreview()
			camera?.setPreviewCallback(null)
		}
		isPreviewIng = false
	}
	
	private fun closeCamera() {
		val mCamera = mCameraAtomic.get()
		Log.d("camera1Tag", "mCamera:$mCamera")
		runCatching { mCamera?.release() }
		mCameraAtomic.set(null)
	}
	
	override fun onCleared() {
		super.onCleared()
		Log.d("CameraViewModel", "onCleared")
		stopPreview()
		closeCamera()
	}
}