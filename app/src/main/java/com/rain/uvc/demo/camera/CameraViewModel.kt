package com.rain.uvc.demo.camera

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Surface
import androidx.core.content.getSystemService
import androidx.lifecycle.viewModelScope
import com.rain.uvc.UvcCamera
import com.rain.uvc.UvcCameraHelper
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.UsbCameraUtils
import com.rain.uvc.listener.ICameraOpenListener
import com.rain.uvc.provider.OverallContext
import com.rain.uvc.state.CameraNativeState
import com.rain.uvc.state.OrientationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 */
class CameraViewModel : BaseViewModel() {
	
	private var currentRotation = -1 //旋转角度
	
	private var mUvcCamera: UvcCamera? = null
	
	fun openCamera(block: ((Boolean) -> Unit)) {
		val usbManager = OverallContext.baseContext.getSystemService<UsbManager>()
		if (usbManager == null) {
			block.invoke(false)
			return
		}
		val uvcDevice = UsbCameraUtils.loadUsbCameraDevice()
		if (uvcDevice == null) {
			block.invoke(false)
			return
		}
		openCamera(uvcDevice, block)
	}
	
	fun destroyCamera() {
		mUvcCamera?.close()
		mUvcCamera = null
	}
	
	private fun openCamera(device: UsbDevice, block: ((Boolean) -> Unit)) {
		Log.d("cameraPreviewUpdateTag", "openCamera-device:${device}")
		viewModelScope.launch(Dispatchers.IO) {
			//获取设备列表
			val uvcCamera = UvcCameraHelper.create(device)
			if (uvcCamera == null) {
				block.invoke(false)
				return@launch
			}
			mUvcCamera = uvcCamera
			//打开摄像头
			val openResult = open()
			if (!openResult) {
				mUvcCamera?.close()
				mUvcCamera = null
				block.invoke(false)
				return@launch
			}
			//设置预览分辨率
			mUvcCamera?.setPreviewSize(640, 480, true)
			block.invoke(true)
		}
	}
	
	private suspend fun open(): Boolean {
		try {
			return withTimeout(3000) {
				suspendCancellableCoroutine { con ->
					con.invokeOnCancellation {
						mUvcCamera?.close()
					}
					mUvcCamera?.open(object : ICameraOpenListener {
						override fun success() {
							con.resume(true)
						}
						
						override fun failed(message: String?) {
							con.resume(false)
						}
					})
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			return false
		}
	}
	
	fun startPreview(surface: Surface) {
		mUvcCamera?.setPreviewListener { width, height, frame ->
			val data = ByteArray(frame.capacity())
			frame.get(data)
			frame.clear()
			Log.d("cameraPreviewUpdateTag", "startPreview-data:${data.size}")
		}
		mUvcCamera?.setDisplaySurface(surface)
		mUvcCamera?.startPreview()
	}
	
	fun setDisplay() {
		val currentRotation = (this.currentRotation.run {
			if (this == -1) 0 else this
		} + 90) % 360
		this.currentRotation = currentRotation
		mUvcCamera?.setParameter(CameraNativeState.ORIENTATION, OrientationState.orientationToState(currentRotation))
	}
	
	fun stopPreview() {
		mUvcCamera?.stopPreview()
	}
	
	fun updatePreviewSize() {
	}
}