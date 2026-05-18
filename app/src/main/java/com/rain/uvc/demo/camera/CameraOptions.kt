package com.rain.uvc.demo.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import com.rain.camera.uvc.factory.NativeUsbDevice
import com.rain.camera.uvc.mode.UvcCameraSize
import com.rain.camera.uvc.mode.UvcCameraSupportSize
import com.rain.camera.uvc.parameter.UvcCameraParameter
import com.rain.camera.uvc.parameter.UvcSupportParameter
import com.rain.uvc.demo.utils.GsonHelper

/**
 * @author yuan
 * @createTime: 2026/5/12
 * @des 相机配置数据
 */
class CameraSupportOptions {
	
	// 照片分辨率
	var previewSizes: MutableList<UvcCameraSupportSize>? = null
	
	// 曝光度范围
	var exposureRange: IntRange? = null
	
	// 是否支持自动曝光
	var isSupportAutoExposure: Boolean = false
	
	// 亮度范围
	var brightnessRange: IntRange? = null
	
	// 缩放范围
	var zoomRange: IntRange? = null
	
	// 支持的白平衡列表
	var whiteRange: IntRange? = null
	
	/**
	 * 初始化参数
	 */
	fun initSupport(cameraSession: NativeUsbDevice) {
		previewSizes = cameraSession.getSupportParameters(UvcSupportParameter.PREVIEW_SIZE)?.toMutableList() // 录制列表
		exposureRange = cameraSession.getSupportParameters(UvcSupportParameter.EXPOSURE) // 曝光度范围
		isSupportAutoExposure = cameraSession.getSupportParameters(UvcSupportParameter.AUTO_EXPOSURE) ?: false // 是否支持自动曝光
		brightnessRange = cameraSession.getSupportParameters(UvcSupportParameter.BRIGHTNESS) // 亮度范围
		
		whiteRange = cameraSession.getSupportParameters(UvcSupportParameter.WHITE_BALANCE) // 白平衡列表
		
		zoomRange = cameraSession.getSupportParameters(UvcSupportParameter.ZOOM) // 缩放范围
		
		Log.d("CameraViewModel", "预览分辨率 = ${GsonHelper.getHelper().modeToJson(previewSizes)}")
	}
	
	fun clear() {
		previewSizes = null
		exposureRange = null
		isSupportAutoExposure = false
		whiteRange = null
		zoomRange = null
		
	}
}

/**
 * 当前设置的参数信息
 */
data class CameraOptions(
	var mPicOrientation: Int = 0,// 相机方向
	var mPicMirrorState: Boolean = false,// 是否拍照镜像
	// 当前预览分辨率
	var mCurrentPreviewSize: UvcCameraSize? = null,
	
	// 闪光灯状态
	var isAutoExposure: Boolean? = null,
	// 当前曝光度
	var mCurrentExposure: Int? = null,
	
	// 当前缩放值
	var mCurrentZoom: Int? = null,
	
	// 当前亮度
	var mCurrentBrightness: Int? = null,
	
	// 当前白平衡
	var mCurrentWhiteBalance: Int? = null,
	
	// 当前场景模式
	var mCurrentSceneMode: String? = null,
	
	// 闪光灯状态
	var mCurrentFlashState: Boolean? = null,
	
	// 当前帧率
	var mVideoFps: Int = 30,// 默认30帧
) {
	/**
	 * 初始化参数
	 */
	fun initParameter(cameraSession: NativeUsbDevice) {
		mPicOrientation = cameraSession.getParameter(UvcCameraParameter.ORIENTATION) ?: 0
		mPicMirrorState = cameraSession.getParameter(UvcCameraParameter.MIRROR) ?: false
		resetParameter(cameraSession)
		if (mCurrentPreviewSize == null) mCurrentPreviewSize = cameraSession.getParameter(
			UvcCameraParameter.PREVIEW_SIZE
		)
		isAutoExposure = cameraSession.getParameter(UvcCameraParameter.AUTO_EXPOSURE)
		mCurrentExposure = cameraSession.getParameter(UvcCameraParameter.EXPOSURE)
		mCurrentZoom = cameraSession.getParameter(UvcCameraParameter.ZOOM)
		mCurrentBrightness = cameraSession.getParameter(UvcCameraParameter.BRIGHTNESS)
		mCurrentWhiteBalance = cameraSession.getParameter(UvcCameraParameter.WHITE_BALANCE)
	}
	
	private fun resetParameter(cameraSession: NativeUsbDevice) {
		if (isAutoExposure != null) {
			cameraSession.setParameter(UvcCameraParameter.AUTO_EXPOSURE, isAutoExposure!!)
		}
		if (mCurrentExposure != null) {
			cameraSession.setParameter(UvcCameraParameter.EXPOSURE, mCurrentExposure!!)
		}
		if (mCurrentZoom != null) {
			cameraSession.setParameter(UvcCameraParameter.ZOOM, mCurrentZoom!!)
		}
		if (mCurrentBrightness != null) {
			cameraSession.setParameter(UvcCameraParameter.BRIGHTNESS, mCurrentBrightness!!)
		}
		if (mCurrentWhiteBalance != null) {
			cameraSession.setParameter(UvcCameraParameter.WHITE_BALANCE, mCurrentWhiteBalance!!)
		}
	}
	
	fun clear() {
		isAutoExposure = null
		mCurrentExposure = null
		mCurrentZoom = null
		mCurrentBrightness = null
		mCurrentWhiteBalance = null
		mCurrentSceneMode = null
		mCurrentFlashState = null
		mCurrentPreviewSize = null
		mPicMirrorState = false
		mPicOrientation = 0
	}
}

/**
 * 场景模式转换中文显示
 */
private fun String.toSceneMode(): String {
	return when (this.toIntOrNull()) {
		CameraCharacteristics.CONTROL_MODE_OFF -> "关闭"
		CameraCharacteristics.CONTROL_MODE_AUTO -> "自动"
		CameraCharacteristics.CONTROL_SCENE_MODE_ACTION -> "运动(高速快门)"
		CameraCharacteristics.CONTROL_SCENE_MODE_PORTRAIT -> "人像"
		CameraCharacteristics.CONTROL_SCENE_MODE_LANDSCAPE -> "风景"
		CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT -> "夜景"
		CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT_PORTRAIT -> "夜景人像"
		CameraCharacteristics.CONTROL_SCENE_MODE_THEATRE -> "剧院"
		CameraCharacteristics.CONTROL_SCENE_MODE_BEACH -> "海滩"
		CameraCharacteristics.CONTROL_SCENE_MODE_SNOW -> "雪景"
		CameraCharacteristics.CONTROL_SCENE_MODE_SUNSET -> "日落"
		CameraCharacteristics.CONTROL_SCENE_MODE_STEADYPHOTO -> "防抖"
		CameraCharacteristics.CONTROL_SCENE_MODE_FIREWORKS -> "烟火"
		CameraCharacteristics.CONTROL_SCENE_MODE_SPORTS -> "运动"
		CameraCharacteristics.CONTROL_SCENE_MODE_PARTY -> "派对"
		CameraCharacteristics.CONTROL_SCENE_MODE_CANDLELIGHT -> "烛光"
		CameraCharacteristics.CONTROL_SCENE_MODE_BARCODE -> "条形码"
		CameraCharacteristics.CONTROL_SCENE_MODE_HDR -> "HDR"
		else -> this
	}
}

/**
 * 场景模式转换中文显示
 */
private fun String.toWhiteBalanceMode(): String {
	return when (this.toIntOrNull()) {
		CaptureRequest.CONTROL_AWB_MODE_OFF -> "关闭"
		CaptureRequest.CONTROL_AWB_MODE_AUTO -> "自动"
		CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "白炽灯"
		CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "荧光灯"
		CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "暖荧光灯"
		CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "日光"
		CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "阴天"
		CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "黄昏"
		CaptureRequest.CONTROL_AWB_MODE_SHADE -> "阴影"
		else -> "未知" // 更友好
	}
}

data class CameraOptionSelect(val value: String, val name: String)