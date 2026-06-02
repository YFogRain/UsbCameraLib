package com.rain.uvc.demo.camera

import android.util.Log
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.factory.NativeUsbDevice
import com.rain.uvc.mode.UvcCameraSize
import com.rain.uvc.mode.UvcCameraSupportSize
import com.rain.uvc.parameters.UvcCameraParameter
import com.rain.uvc.parameters.UvcPreviewFormat
import com.rain.uvc.parameters.UvcSupportParameter

/**
 * @author yuan
 * @createTime: 2026/5/12
 * @des 相机配置数据
 */
data class CameraSupportOptions(
	// 照片分辨率
	var previewSizes: MutableList<UvcCameraSupportSize>? = null,
	var isSupportAutoExposure: Boolean = false,// 是否支持自动曝光
	var isSupportAutoFocus: Boolean = false, // 是否支持自动对焦
	var isSupportAutoHue: Boolean = false, // 是否支持自动色调
	var isSupportAutoWhiteBalance: Boolean = false, // 自动白平衡
	var exposureRange: IntRange? = null, // 曝光范围
	var brightnessRange: IntRange? = null,// 亮度范围
	var contrastRange: IntRange? = null, // 对比度范围
	var gainRange: IntRange? = null,// 增益值范围
	var saturationRange: IntRange? = null, // 饱和度范围
	var zoomRange: IntRange? = null, // 缩放范围
	var focusRange: IntRange? = null, // 聚焦范围
	var irisRange: IntRange? = null, // 光圈范围
	var hueRange: IntRange? = null,// 色温范围
	var whiteBalanceRange: IntRange? = null, // 白平衡范围
) {
	
	/**
	 * 初始化参数
	 */
	fun initSupport(cameraSession: NativeUsbDevice) {
		previewSizes = cameraSession.getSupportParameters(UvcSupportParameter.PREVIEW_SIZE)?.toMutableList() // 录制列表
		// 是否支持自动曝光
		isSupportAutoExposure = cameraSession.getSupportParameters(UvcSupportParameter.AUTO_EXPOSURE) ?: false
		// 曝光度
		exposureRange = cameraSession.getSupportParameters(UvcSupportParameter.EXPOSURE)
		brightnessRange = cameraSession.getSupportParameters(UvcSupportParameter.BRIGHTNESS)
		contrastRange = cameraSession.getSupportParameters(UvcSupportParameter.CONTRAST)
		gainRange = cameraSession.getSupportParameters(UvcSupportParameter.GAIN)
		saturationRange = cameraSession.getSupportParameters(UvcSupportParameter.SATURATION)
		zoomRange = cameraSession.getSupportParameters(UvcSupportParameter.ZOOM)
		isSupportAutoFocus = cameraSession.getSupportParameters(UvcSupportParameter.AUTO_FOCUS) ?: false
		focusRange = cameraSession.getSupportParameters(UvcSupportParameter.FOCUS)
		irisRange = cameraSession.getSupportParameters(UvcSupportParameter.IRIS)
		isSupportAutoHue = cameraSession.getSupportParameters(UvcSupportParameter.AUTO_HUE) ?: false
		hueRange = cameraSession.getSupportParameters(UvcSupportParameter.HUE)
		isSupportAutoWhiteBalance = cameraSession.getSupportParameters(UvcSupportParameter.AUTO_WHITE_BALANCE) ?: false
		whiteBalanceRange = cameraSession.getSupportParameters(UvcSupportParameter.WHITE_BALANCE)
		Log.d("CameraViewModel", "预览分辨率 = ${GsonHelper.getHelper().modeToJson(previewSizes)}")
	}
	
	fun clear() {
		previewSizes = null
		isSupportAutoExposure = false // 是否支持自动曝光
		isSupportAutoFocus = false // 是否支持自动对焦
		isSupportAutoHue = false // 是否支持自动色调
		isSupportAutoWhiteBalance = false // 自动白平衡
		exposureRange = null // 曝光范围
		brightnessRange = null // 亮度范围
		contrastRange = null // 对比度范围
		gainRange = null // 增益值范围
		saturationRange = null // 饱和度范围
		zoomRange = null // 缩放范围
		focusRange = null // 聚焦范围
		irisRange = null // 光圈范围
		hueRange = null // 色温范围
		whiteBalanceRange = null // 白平衡范围
		
	}
}

fun List<UvcCameraSupportSize>.supportedPreviewSizes(): List<UvcCameraSize> {
	return firstOrNull {
		it.format == UvcPreviewFormat.MJPEG || it.format == UvcPreviewFormat.JPEG
	}?.sizes.orEmpty()
}

fun List<UvcCameraSupportSize>.resolvePreviewSize(preferred: UvcCameraSize?): UvcCameraSize? {
	val sizes = supportedPreviewSizes()
	if (sizes.isEmpty()) return null
	if (preferred != null && sizes.contains(preferred)) return preferred
	return sizes.find {
		(it.width == 1920 && it.height == 1080) || (it.width == 1080 && it.height == 1920)
	} ?: sizes.find {
		(it.width == 640 && it.height == 480) || (it.width == 480 && it.height == 640)
	} ?: sizes[0]
}

/**
 * 当前设置的参数信息
 */
data class CameraOptions(
	var previewSize: UvcCameraSize? = null,// 照片分辨率
	var mPicOrientation: Int = 0,// 相机方向
	var mPicMirrorState: Boolean = false,// 是否拍照镜像
	
	var isAutoExposure: Boolean? = null,// 自动曝光
	var isAutoFocus: Boolean? = null, // 是否支持自动对焦
	var isAutoHue: Boolean? = null, // 是否支持自动色调
	var isAutoWhiteBalance: Boolean? = null, // 自动白平衡
	var exposure: Int? = null, // 曝光范围
	var brightness: Int? = null,// 亮度范围
	var contrast: Int? = null, // 对比度范围
	var gain: Int? = null,// 增益值范围
	var saturation: Int? = null,// 饱和度范围
	var zoom: Int? = null, // 缩放范围
	var focus: Int? = null,// 聚焦范围
	var iris: Int? = null, // 光圈范围
	var hue: Int? = null, // 色温范围
	var whiteBalance: Int? = null, // 白平衡范围
) {
	/**
	 * 初始化参数
	 */
	fun initParameter(cameraSession: NativeUsbDevice) {
		mPicOrientation = cameraSession.getParameter(UvcCameraParameter.ORIENTATION) ?: 0
		mPicMirrorState = cameraSession.getParameter(UvcCameraParameter.MIRROR) ?: false
		applyParameters(cameraSession)
		if (previewSize == null) previewSize = cameraSession.getParameter(UvcCameraParameter.PREVIEW_SIZE)
		isAutoExposure = cameraSession.getParameter(UvcCameraParameter.AUTO_EXPOSURE) ?: false
		isAutoFocus = cameraSession.getParameter(UvcCameraParameter.AUTO_FOCUS) ?: false// 是否支持自动对焦
		isAutoHue = cameraSession.getParameter(UvcCameraParameter.AUTO_HUE) ?: false // 是否支持自动色调
		isAutoWhiteBalance = cameraSession.getParameter(UvcCameraParameter.AUTO_WHITE_BALANCE) ?: false // 自动白平衡
		exposure = cameraSession.getParameter(UvcCameraParameter.EXPOSURE)
		brightness = cameraSession.getParameter(UvcCameraParameter.BRIGHTNESS)
		contrast = cameraSession.getParameter(UvcCameraParameter.CONTRAST)
		gain = cameraSession.getParameter(UvcCameraParameter.GAIN)
		saturation = cameraSession.getParameter(UvcCameraParameter.SATURATION)
		zoom = cameraSession.getParameter(UvcCameraParameter.ZOOM)
		focus = cameraSession.getParameter(UvcCameraParameter.FOCUS)
		iris = cameraSession.getParameter(UvcCameraParameter.IRIS)
		hue = cameraSession.getParameter(UvcCameraParameter.HUE)
		whiteBalance = cameraSession.getParameter(UvcCameraParameter.WHITE_BALANCE)
	}
	
	fun applyParameters(cameraSession: NativeUsbDevice) {
		if (isAutoExposure != null) cameraSession.setParameter(
			UvcCameraParameter.AUTO_EXPOSURE, isAutoExposure!!
		)
		if (isAutoFocus != null) cameraSession.setParameter(
			UvcCameraParameter.AUTO_FOCUS, isAutoFocus!!
		)
		if (isAutoHue != null) cameraSession.setParameter(UvcCameraParameter.AUTO_HUE, isAutoHue!!)
		if (isAutoWhiteBalance != null) cameraSession.setParameter(
			UvcCameraParameter.AUTO_WHITE_BALANCE, isAutoWhiteBalance!!
		)
		if (exposure != null) cameraSession.setParameter(
			UvcCameraParameter.EXPOSURE, exposure!!
		) // 曝光范围
		if (brightness != null) cameraSession.setParameter(
			UvcCameraParameter.BRIGHTNESS, brightness!!
		) // 亮度范围
		if (contrast != null) cameraSession.setParameter(
			UvcCameraParameter.CONTRAST, contrast!!
		) // 对比度范围
		if (gain != null) cameraSession.setParameter(UvcCameraParameter.GAIN, gain!!) // 增益值范围
		if (saturation != null) cameraSession.setParameter(
			UvcCameraParameter.SATURATION, saturation!!
		) // 饱和度范围
		if (zoom != null) cameraSession.setParameter(UvcCameraParameter.ZOOM, zoom!!) // 缩放范围
		if (focus != null) cameraSession.setParameter(UvcCameraParameter.FOCUS, focus!!)
		if (iris != null) cameraSession.setParameter(UvcCameraParameter.IRIS, iris!!)
		if (hue != null) cameraSession.setParameter(UvcCameraParameter.HUE, hue!!)
		if (whiteBalance != null) cameraSession.setParameter(
			UvcCameraParameter.WHITE_BALANCE, whiteBalance!!
		) // 白平衡范围
	}
	
	fun clear() {
		mPicOrientation = 0
		mPicMirrorState = false
		previewSize = null
		isAutoExposure = null
		isAutoFocus = null// 是否支持自动对焦
		isAutoHue = null // 是否支持自动色调
		isAutoWhiteBalance = null // 自动白平衡
		exposure = null
		brightness = null
		contrast = null
		gain = null
		saturation = null
		zoom = null
		focus = null
		iris = null
		hue = null
		whiteBalance = null
	}
}
