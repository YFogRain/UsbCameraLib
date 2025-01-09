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
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.demo.utils.UsbCameraUtils
import com.rain.uvc.listener.ICameraOpenListener
import com.rain.uvc.mode.FormatModeState
import com.rain.uvc.provider.OverallContext
import com.rain.uvc.state.CameraParameter
import com.rain.uvc.state.CameraSupportParameters
import com.rain.uvc.state.DisplayTransformState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 */
class CameraViewModel : BaseViewModel() {
	
	private var currentRotation = 0 //旋转角度
	
	private var mUvcCamera: UvcCamera? = null
	
	fun openCamera(block: ((Boolean) -> Unit)) {
		val usbManager = OverallContext.baseContext.getSystemService<UsbManager>()
		if (usbManager == null) {
			block.invoke(false)
			return
		}
		val uvcDevice = UsbCameraUtils.loadUsbCameraDevice()
		Log.d("cameraPreviewUpdateTag", "获取到的摄像头信息:${uvcDevice?.manufacturerName}")
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
				Log.d("cameraPreviewUpdateTag", "创建usb驱动对象失败～～～")
				block.invoke(false)
				return@launch
			}
			mUvcCamera = uvcCamera
			//打开摄像头
			val openResult = open(uvcCamera)
			Log.d("cameraPreviewUpdateTag", "打开结果～～：$openResult")
			if (!openResult) {
				mUvcCamera?.close()
				mUvcCamera = null
				block.invoke(false)
				return@launch
			}
			val supportedParameter = mUvcCamera?.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE)
			Log.d("cameraPreviewUpdateTag", "分辨率集合:${GsonHelper.getHelper().modeToJson(supportedParameter)}")
			//设置预览分辨率
			mUvcCamera?.setPreviewSize(1920, 1080, FormatModeState.MJPEG)
			block.invoke(true)
		}
	}
	
	private suspend fun open(uvcCamera: UvcCamera): Boolean {
		try {
			return withTimeout(3000) {
				suspendCancellableCoroutine { con ->
					con.invokeOnCancellation {
						uvcCamera.close()
					}
					uvcCamera.open(object : ICameraOpenListener {
						override fun success() {
							Log.d("cameraPreviewUpdateTag", "camera 打开成功")
							con.resume(true)
							
						}
						
						override fun failed(message: String?) {
							Log.d("cameraPreviewUpdateTag", "打开失败啦 ：$message")
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
//			Log.d("cameraPreviewUpdateTag", "startPreview-data:${data.size},,,:${width}*${height}")
		}
		mUvcCamera?.setDisplaySurface(surface)
		mUvcCamera?.startPreview()
	}
	
	fun setDisplay() {
		currentRotation++
		if (currentRotation > 11) {
			currentRotation = 0
		}
		mUvcCamera?.setParameter(CameraParameter.DISPLAY_TRANSFORM, DisplayTransformState.orientationToState(currentRotation))
	}
	
	fun stopPreview() {
		mUvcCamera?.stopPreview()
	}
	
	fun updatePreviewSize() {
	}
}