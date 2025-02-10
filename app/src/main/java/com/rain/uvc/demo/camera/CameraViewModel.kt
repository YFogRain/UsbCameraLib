package com.rain.uvc.demo.camera

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Surface
import androidx.core.content.getSystemService
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraManager
import com.rain.uvc.camera.CameraDevice
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.demo.utils.UsbCameraUtils
import com.rain.uvc.state.CameraPreviewFormat
import com.rain.uvc.provider.OverallContext
import com.rain.uvc.state.CameraParameter
import com.rain.uvc.state.CameraSupportParameters
import com.rain.uvc.state.DisplayTransformState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 */
class CameraViewModel : BaseViewModel() {
	
	private var currentRotation = 0 //旋转角度
	
	private var mUvcCamera: CameraDevice? = null
	
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
		CameraManager.cancel()
		mUvcCamera?.close()
		mUvcCamera = null
	}
	
	private fun openCamera(device: UsbDevice, block: ((Boolean) -> Unit)) {
		Log.d("cameraPreviewUpdateTag", "openCamera-device:${device}")
		viewModelScope.launch(Dispatchers.IO) {
			//打开摄像头
			val cameraDevice = CameraManager.openCameraSync(device)
			Log.d("cameraPreviewUpdateTag", "打开结果～～：$cameraDevice")
			if (cameraDevice == null) {
				mUvcCamera?.close()
				mUvcCamera = null
				block.invoke(false)
				return@launch
			}
			this@CameraViewModel.mUvcCamera = cameraDevice
			val supportedParameter = mUvcCamera?.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE)
			Log.d("cameraPreviewUpdateTag", "分辨率集合:${GsonHelper.getHelper().modeToJson(supportedParameter)}")
			//设置预览分辨率
			mUvcCamera?.setPreviewSize(640, 480, CameraPreviewFormat.YUY2)
			block.invoke(true)
		}
	}
	
	fun startPreview(surface: Surface) {
		mUvcCamera?.setPreviewListener { _, _, frame ->
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