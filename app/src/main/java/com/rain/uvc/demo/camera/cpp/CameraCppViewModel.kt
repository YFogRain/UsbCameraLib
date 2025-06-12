package com.rain.uvc.demo.camera.cpp

import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.mode.CameraSize
import com.rain.uvc.provider.OverallContext
import com.rain.uvc.state.CameraDataFormat
import com.rain.uvc.state.CameraParameter
import com.rain.uvc.state.CameraPreviewFormat
import com.rain.uvc.state.CameraSupportParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2025/6/12
 * @des
 */
class CameraCppViewModel : BaseViewModel() {
	//相机实例
	private val mCameraDevice = AtomicReference<ICameraDevice>()
	
	//打开结果,成功返回null，失败返回错误原因
	val openResultFlow = MutableSharedFlow<String?>()
	
	val recordState = MutableLiveData<Boolean>(false)
	fun openCamera(usbDevice: UsbDevice) {
		open {
			CameraUvcManager.openCamera(usbDevice)
		}
	}
	
	fun openCamera(videoPath: String) {
		open {
			CameraUvcManager.openCamera(videoPath)
		}
	}
	
	private fun open(openInvoke: () -> ICameraDevice) {
		viewModelScope.launch(Dispatchers.IO) {
			val result = runCatching { openInvoke.invoke() }
			val cameraDevice = result.getOrNull()
			if (result.isFailure || cameraDevice == null) {
				val message = result.exceptionOrNull()?.message.let {
					if (it.isNullOrEmpty()) "打开失败" else it
				}
				openResultFlow.emit(message)
				return@launch
			}
			initCameraParameters(cameraDevice)
			cameraDevice.setPreviewSize(640, 480, CameraPreviewFormat.MJPEG)
			cameraDevice.setParentPath(OverallContext.baseContext.filesDir.path)
			mCameraDevice.set(cameraDevice)
			openResultFlow.emit(null)
		}
	}
	
	fun initPreview(surfaceView: SurfaceView) {
		mCameraDevice.get()?.setDisplaySurface(surfaceView)
	}
	
	fun startPreview() {
		mCameraDevice.get()?.startPreview().also {
			Log.d("CameraCppViewModel", "打开预览结果:$it")
		}
	}
	
	fun stopPreview() {
		mCameraDevice.get()?.stopPreview()
	}
	
	fun closeCamera() {
		mCameraDevice.get()?.close()
		mCameraDevice.set(null)
	}
	
	fun takePicture() {
		viewModelScope.launch(Dispatchers.IO) {
			val picture = mCameraDevice.get()?.takePicture(OverallContext.baseContext.filesDir.path)
			Log.d("CameraCppViewModel", "拍照结果:$picture")
		}
	}
	
	private fun initCameraParameters(cameraDevice: ICameraDevice) {
		val supportPreviewSize = cameraDevice.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE)
		Log.d("CameraCppViewModel", "分辨率列表:${GsonHelper.getHelper().modeToJson(supportPreviewSize)}")
		Log.d("CameraCppViewModel", "自动曝光支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_EXPOSURE)}")
		Log.d("CameraCppViewModel", "曝光度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.EXPOSURE)}")
		Log.d("CameraCppViewModel", "人脸检测支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.FACE_DETECT)}")
		Log.d("CameraCppViewModel", "亮度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.BRIGHTNESS)}")
		Log.d("CameraCppViewModel", "对比度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.CONTRAST)}")
		Log.d("CameraCppViewModel", "增益值范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.GAIN)}")
		Log.d("CameraCppViewModel", "饱和度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.SATURATION)}")
		Log.d("CameraCppViewModel", "缩放范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.ZOOM)}")
		Log.d("CameraCppViewModel", "白平衡支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.WHITE_BALANCE)}")
		Log.d("CameraCppViewModel", "场景模式支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.SCENE_MODE)}")
		Log.d("CameraCppViewModel", "隐私模式支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.PRIVACY)}")
		Log.d("CameraCppViewModel", "自动白平衡支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_WHITE_BALANCE)}")
		Log.d("CameraCppViewModel", "hue支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.HUE)}")
		
		Log.d("CameraCppViewModel", "当前分辨率:${cameraDevice.getParameter(CameraParameter.PREVIEW_SIZE)}")
		Log.d("CameraCppViewModel", "自动曝光模式:${cameraDevice.getParameter(CameraParameter.AUTO_EXPOSURE)}")
		Log.d("CameraCppViewModel", "曝光度:${cameraDevice.getParameter(CameraParameter.EXPOSURE)}")
		Log.d("CameraCppViewModel", "亮度:${cameraDevice.getParameter(CameraParameter.BRIGHTNESS)}")
		Log.d("CameraCppViewModel", "对比度:${cameraDevice.getParameter(CameraParameter.CONTRAST)}")
		Log.d("CameraCppViewModel", "增益值:${cameraDevice.getParameter(CameraParameter.GAIN)}")
		Log.d("CameraCppViewModel", "饱和度:${cameraDevice.getParameter(CameraParameter.SATURATION)}")
		Log.d("CameraCppViewModel", "缩放:${cameraDevice.getParameter(CameraParameter.ZOOM)}")
	}
	
	fun recorder() {
		val cameraDevice = mCameraDevice.get() ?: return
		if (recordState.value == true) {
			val stopRecord = cameraDevice.stopRecord()
			if (stopRecord) {
				Log.d("CameraCppViewModel", "保存路径:${cameraDevice.recordPath}")
			}
			recordState.value = false
		} else cameraDevice.startRecord().also {
			Log.d("CameraCppViewModel", "开启录制结果:${it}")
			recordState.value = it
		}
	}
}
