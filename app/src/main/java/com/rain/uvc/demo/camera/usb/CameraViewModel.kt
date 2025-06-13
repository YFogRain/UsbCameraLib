package com.rain.uvc.demo.camera.usb

import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.state.CameraDataFormat
import com.rain.uvc.state.CameraPreviewFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des
 */
class CameraViewModel : BaseViewModel() {
	
	val openResultFlow = MutableSharedFlow<String?>()
	
	private var mRgbControl: CameraControlHelper? = null
	private var mIrControl: CameraControlHelper? = null
	
	/**
	 * 打开摄像头
	 */
	fun openCamera() {
		viewModelScope.launch(Dispatchers.IO) {
			val cameraDevices = CameraUvcManager.getCameraDevices()
			if (cameraDevices.isNullOrEmpty()) {
				openResultFlow.emit("未获取到摄像头设备")
				return@launch
			}
			val irDevice = cameraDevices.find { it.vendorId == 9595 && it.productId == 4117 }
			val rgbDevice = cameraDevices.find { it.vendorId == 9595 && it.productId == 4116 }
			if (irDevice == null || rgbDevice == null) {
				openResultFlow.emit("未获取到摄像头设备")
				return@launch
			}
			val rgbResult = CameraControlHelper.open("RGB摄像头", rgbDevice)
			val rgbControl = rgbResult.getOrNull()
			if (rgbResult.isFailure || rgbControl == null) {
				val message = rgbResult.exceptionOrNull()?.message.let {
					if (it.isNullOrEmpty()) "打开失败" else it
				}
				openResultFlow.emit(message)
				return@launch
			}
			
			val irResult = CameraControlHelper.open("IR摄像头", irDevice)
			val irControl = irResult.getOrNull()
			if (irResult.isFailure || irControl == null) {
				val message = irResult.exceptionOrNull()?.message.let {
					if (it.isNullOrEmpty()) "打开失败" else it
				}
				openResultFlow.emit(message)
				return@launch
			}
			mRgbControl = rgbControl
			mIrControl = irControl
			openResultFlow.emit(null)
		}
	}
	
	fun startPreview() {
		mRgbControl?.startPreview()
		mIrControl?.startPreview()
	}
	
	fun setDisplaySurface(rgbSurface: SurfaceView, irSurface: SurfaceView) {
		mRgbControl?.setDisplaySurface(rgbSurface)
		mIrControl?.setDisplaySurface(irSurface)
	}
	
	fun stopPreview() {
		mRgbControl?.stopPreview()
		mIrControl?.stopPreview()
	}
	
	fun close() {
		mRgbControl?.close()
		mIrControl?.close()
	}
}

class CameraControlHelper private constructor(val cameraType: String, val cameraDevice: ICameraDevice) {
	companion object {
		@JvmStatic
		fun open(cameraType: String, usbDevice: UsbDevice): Result<CameraControlHelper> {
			val result = runCatching { CameraUvcManager.openCamera(usbDevice) }
			Log.d("CameraControlHelper", "当前打开的usb设备为:${usbDevice.productName}")
			val cameraDevice = result.getOrNull()
			if (result.isFailure || cameraDevice == null) {
				val message = result.exceptionOrNull()?.message.let {
					if (it.isNullOrEmpty()) "打开失败" else it
				}
				Log.d("CameraControlHelper", "打开失败,原因:$message")
				return Result.failure(IllegalStateException(message))
			}
			cameraDevice.setPreviewSize(640, 480, CameraPreviewFormat.MJPEG)
			return Result.success(CameraControlHelper(cameraType, cameraDevice))
		}
	}
	
	init {
		cameraDevice.setPreviewListener({ width, height, frame ->
//			Log.d("CameraControlHelper", "使用设备:$cameraType,回调数据:${width}*${height}")
		}, CameraDataFormat.NV21)
	}
	
	fun startPreview() {
		val startPreview = cameraDevice.startPreview()
		Log.d("CameraControlHelper", "使用设备:$cameraType,开启预览结果:$startPreview")
	}
	
	fun setDisplaySurface(surfaceView: SurfaceView) {
		val displaySurface = cameraDevice.setDisplaySurface(surfaceView)
		Log.d("CameraControlHelper", "使用设备:$cameraType,设置预览控件结果:$displaySurface")
	}
	
	fun stopPreview() {
		val stopPreview = cameraDevice.stopPreview()
		Log.d("CameraControlHelper", "使用设备:$cameraType,停止预览结果:$stopPreview")
	}
	
	fun close() {
		val close = cameraDevice.close()
		Log.d("CameraControlHelper", "使用设备:$cameraType,关闭设备结果:$close")
	}
}