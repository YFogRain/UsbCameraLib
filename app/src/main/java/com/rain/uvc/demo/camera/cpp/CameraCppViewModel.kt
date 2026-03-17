package com.rain.uvc.demo.camera.cpp

import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.TextureView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.camera.NCameraDevice
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.provider.OverallContext
import com.rain.uvc.demo.record.MediaMuxerThread
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.demo.utils.PictureUtils
import com.rain.uvc.parameters.CameraDataFormat
import com.rain.uvc.parameters.CameraPreviewFormat
import com.rain.uvc.parameters.Parameters
import com.rain.uvc.parameters.SupportParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2025/6/12
 * @des
 */
class CameraCppViewModel : BaseViewModel() {
	//相机实例
	private val mCameraDevice = AtomicReference<NCameraDevice>()
	
	//录制线程
	private var mMediaMuxer: MediaMuxerThread? = null
	
	//打开结果,成功返回null，失败返回错误原因
	val openResultFlow = MutableSharedFlow<String?>()
	
	val recordState = MutableLiveData(false)
	fun openCamera(usbDevice: UsbDevice) {
		open {
			CameraUvcManager.openCamera(OverallContext.baseContext, usbDevice)
		}
	}
	
	fun openCamera(videoPath: String) {
		open {
			CameraUvcManager.openCamera(OverallContext.baseContext, videoPath)
		}
	}
	
	private fun open(openInvoke: suspend () -> Result<NCameraDevice>) {
		viewModelScope.launch(Dispatchers.IO) {
			val result = openInvoke.invoke()
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
			
			val previewSizes = cameraDevice.getSupportedParameter(SupportParameters.PREVIEW_SIZE)?.find { it.format == CameraPreviewFormat.MJPEG || it.format == CameraPreviewFormat.JPEG }?.sizes
			Log.d("CameraCppViewModel", "分辨率列表:${GsonHelper.getHelper().modeToJson(previewSizes)}")
			if (previewSizes.isNullOrEmpty()) {
				cameraDevice.close()
				openResultFlow.emit("未获取到分辨率信息")
				return@launch
			}
			val previewSize = previewSizes.maxBy { it.width * it.height } ?: previewSizes.find {
				((it.width == 1920 && it.height == 1080) || (it.width == 1080 && it.height == 1920))
			} ?: previewSizes.find {
				((it.width == 640 && it.height == 480) || (it.width == 480 && it.height == 640))
			} ?: previewSizes[0]
			Log.d("CameraCppViewModel", "分辨率:${previewSize.width}*${previewSize.height}")
			cameraDevice.setPreviewSize(previewSize.width, previewSize.height, CameraPreviewFormat.MJPEG)
			mCameraDevice.set(cameraDevice)
			openResultFlow.emit(null)
		}
	}
	
	fun initPreview(surfaceView: TextureView) {
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
			val picture = mCameraDevice.get()?.takePicture()
			if (picture != null) PictureUtils.saveJpegBytes(OverallContext.baseContext, picture)
			Log.d("CameraCppViewModel", "拍照结果:$picture")
		}
	}
	
	private fun initCameraParameters(cameraDevice: NCameraDevice) {
		val supportPreviewSize = cameraDevice.getSupportedParameter(SupportParameters.PREVIEW_SIZE)
		
	}
	
	fun recorder() {
		val cameraDevice = mCameraDevice.get() ?: return
		if (recordState.value == true) {
			val path = mMediaMuxer?.end()
			mMediaMuxer = null
			recordState.value = false
			Toast.makeText(OverallContext.baseContext, "视频保存地址 = $path", Toast.LENGTH_SHORT).show()
			return
		}
		val previewSize = cameraDevice.getParameter(Parameters.PREVIEW_SIZE)
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
