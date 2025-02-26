package com.rain.uvc.demo.camera.usb

import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.listener.IFrameListener
import com.rain.uvc.state.CameraDataFormat
import com.rain.uvc.state.CameraPreviewFormat
import com.rain.uvc.state.CameraSupportParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des
 */
class CameraViewModel : BaseViewModel() {
	
	private val mCameraDevice = AtomicReference<ICameraDevice>()
	
	fun openCamera(usbDevice: UsbDevice, block: () -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			//打开摄像头
			val cameraDevice = runCatching { CameraUvcManager.openCamera(usbDevice) }.onFailure { it.printStackTrace() }.getOrNull()
			Log.d("cameraPreviewUpdateTag", "打开结果～～：$cameraDevice")
			if (cameraDevice == null) {
				return@launch
			}
			mCameraDevice.set(cameraDevice)
			val supportedParameter = cameraDevice.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE)
			Log.d("cameraPreviewUpdateTag", "分辨率集合:${GsonHelper.getHelper().modeToJson(supportedParameter)}")
			//设置预览分辨率
			cameraDevice.setPreviewSize(640, 480, CameraPreviewFormat.YUY2)
			cameraDevice.setPreviewListener(IFrameListener { width, height, frame -> }, CameraDataFormat.BGR)
			block.invoke()
		}
	}
	
	fun startPreview(surfaceView: SurfaceView) {
		val iCameraDevice = mCameraDevice.get() ?: return
		iCameraDevice.setDisplaySurface(surfaceView)
		iCameraDevice.startPreview()
	}
	
	override fun onCleared() {
		super.onCleared()
		Log.d("CameraViewModel", "onCleared")
		val iCameraDevice = mCameraDevice.get()
		if (iCameraDevice != null) {
			iCameraDevice.stopPreview()
			iCameraDevice.close()
		}
	}
	
}