package com.rain.uvc.demo.camera.cpp

import android.hardware.usb.UsbDevice
import android.os.Environment
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.record.MediaMuxerThread
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.demo.utils.PictureUtils
import com.rain.uvc.listener.IFrameListener
import com.rain.uvc.provider.OverallContext
import com.rain.uvc.state.CameraDataFormat
import com.rain.uvc.state.CameraParameter
import com.rain.uvc.state.CameraPreviewFormat
import com.rain.uvc.state.CameraSupportParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2025/6/12
 * @des
 */
class CameraCppViewModel : BaseViewModel() {
	//相机实例
	private val mCameraDevice = AtomicReference<ICameraDevice>()
	
	//录制线程
	private var mMediaMuxer: MediaMuxerThread? = null
	
	//打开结果,成功返回null，失败返回错误原因
	val openResultFlow = MutableSharedFlow<String?>()
	
	val recordState = MutableLiveData(false)
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
				Log.d("CameraCppUtils", "message = $message")
				openResultFlow.emit(message)
				return@launch
			}
			initCameraParameters(cameraDevice)
			
			val previewSizes = cameraDevice.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE)
			val previewSize = previewSizes?.find {
				((it.width == 1280 && it.height == 720) || (it.width == 720 && it.height == 1280)) && it.format == CameraPreviewFormat.MJPEG
			} ?: previewSizes?.find {
				((it.width == 1920 && it.height == 1080) || (it.width == 1080 && it.height == 1920))
			} ?: previewSizes?.find {
				((it.width == 640 && it.height == 480) || (it.width == 480 && it.height == 640))
			} ?: previewSizes?.getOrNull(0)
			
			if (previewSize != null) {
				cameraDevice.setPreviewSize(previewSize.width, previewSize.height, previewSize.format)
			}
			cameraDevice.setParentPath("${Environment.getExternalStorageDirectory().absolutePath}${File.separator}camera_video")
			mCameraDevice.set(cameraDevice)
			openResultFlow.emit(null)
		}
	}
	
	fun initPreview(surfaceView: SurfaceView) {
		mCameraDevice.get()?.setDisplaySurface(surfaceView)
	}
	
	fun startPreview() {
		val device = mCameraDevice.get() ?: return
		device.setPreviewListener({ width, height, frame ->
			val data = ByteArray(frame.capacity())
			frame.get(data)
			frame.clear()
			mMediaMuxer?.frame(data)
		}, CameraDataFormat.NV21)
		mCameraDevice.get()?.startPreview().also {
			Log.d("CameraCppViewModel", "打开预览结果:$it")
		}
	}

	fun stopPreview() {
		mMediaMuxer?.end()
		mMediaMuxer = null
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
//		Log.d("CameraCppViewModel", "自动曝光支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_EXPOSURE)}")
//		Log.d("CameraCppViewModel", "曝光度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.EXPOSURE).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "人脸检测支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.FACE_DETECT)}")
//		Log.d("CameraCppViewModel", "亮度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.BRIGHTNESS).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "对比度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.CONTRAST).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "增益值范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.GAIN).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "饱和度范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.SATURATION).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "缩放范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.ZOOM).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "焦距范围:${cameraDevice.getSupportedParameter(CameraSupportParameters.FOCUS).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "白平衡支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.WHITE_BALANCE).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "场景模式支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.SCENE_MODE).let { "${it?.min}-${it?.max}" }}")
//		Log.d("CameraCppViewModel", "隐私模式支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.PRIVACY)}")
//		Log.d("CameraCppViewModel", "自动白平衡支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_WHITE_BALANCE)}")
//		Log.d("CameraCppViewModel", "hue支持:${cameraDevice.getSupportedParameter(CameraSupportParameters.HUE).let { "${it?.min}-${it?.max}" }}")
//
//		Log.d("CameraCppViewModel", "当前分辨率:${cameraDevice.getParameter(CameraParameter.PREVIEW_SIZE).let { "${it?.width}*${it?.height}" }}")
//		Log.d("CameraCppViewModel", "自动曝光模式:${cameraDevice.getParameter(CameraParameter.AUTO_EXPOSURE)}")
//		Log.d("CameraCppViewModel", "曝光度:${cameraDevice.getParameter(CameraParameter.EXPOSURE)}")
//		Log.d("CameraCppViewModel", "亮度:${cameraDevice.getParameter(CameraParameter.BRIGHTNESS)}")
//		Log.d("CameraCppViewModel", "对比度:${cameraDevice.getParameter(CameraParameter.CONTRAST)}")
//		Log.d("CameraCppViewModel", "增益值:${cameraDevice.getParameter(CameraParameter.GAIN)}")
//		Log.d("CameraCppViewModel", "饱和度:${cameraDevice.getParameter(CameraParameter.SATURATION)}")
//		Log.d("CameraCppViewModel", "缩放:${cameraDevice.getParameter(CameraParameter.ZOOM)}")
	}
	
	fun recorder() {
		val cameraDevice = mCameraDevice.get() ?: return
		if (recordState.value == true) {
			val path = mMediaMuxer?.end()
			mMediaMuxer = null
			recordState.value = false
			val saveUri = PictureUtils.saveSandboxVideoToGallery(File(path))
			Toast.makeText(OverallContext.baseContext, "视频保存地址 = $saveUri", Toast.LENGTH_SHORT).show()
			return
		}
		val previewSize = cameraDevice.getParameter(CameraParameter.PREVIEW_SIZE)
		if (previewSize == null) {
			Toast.makeText(OverallContext.baseContext, "获取视频分辨率失败", Toast.LENGTH_SHORT).show()
			return
		}
		mMediaMuxer = MediaMuxerThread(loadRecordPath(), false)
		val begin = mMediaMuxer?.begin(previewSize.width, previewSize.height) ?: false
		if (!begin) {
			mMediaMuxer?.end()
			mMediaMuxer = null
		}
		recordState.value = begin
	}
	
	private fun loadRecordPath(): String {
		
		val recordFile = File("${OverallContext.baseContext.getExternalFilesDir(null)}${File.separator}camera_video${File.separator}capture_${System.currentTimeMillis()}.mp4")
		recordFile.parentFile?.also {
			if (!it.exists()) it.mkdirs()
		}
		return recordFile.absolutePath
	}
	

}
