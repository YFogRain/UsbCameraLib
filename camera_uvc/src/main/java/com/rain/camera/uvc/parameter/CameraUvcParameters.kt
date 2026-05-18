package com.rain.camera.uvc.parameter

import com.rain.camera.uvc.mode.UvcCameraSize
import com.rain.camera.uvc.mode.UvcCameraSupportSize

/**
 * @author yuan
 * @createTime: 2026/5/15
 * @des uvc支持的参数信息
 */

object UvcCameraParameter {
	data class Key<T>(val type: String, val mClass: Class<T>)
	
	// 分辨率
	@JvmField
	val PREVIEW_SIZE = Key(UvcParameterType.PREVIEW_SIZE, UvcCameraSize::class.java)
	
	//自动曝光
	@JvmField
	val AUTO_EXPOSURE = Key(UvcParameterType.AUTO_EXPOSURE, Boolean::class.java)
	
	//曝光度
	@JvmField
	val EXPOSURE = Key(UvcParameterType.EXPOSURE, Int::class.java)
	
	//亮度
	@JvmField
	val BRIGHTNESS = Key(UvcParameterType.BRIGHTNESS, Int::class.java)
	
	//对比度
	@JvmField
	val CONTRAST = Key(UvcParameterType.CONTRAST, Int::class.java)
	
	//增益值
	@JvmField
	val GAIN = Key(UvcParameterType.GAIN, Int::class.java)
	
	//饱和度
	@JvmField
	val SATURATION = Key(UvcParameterType.SATURATION, Int::class.java)
	
	//缩放
	@JvmField
	val ZOOM = Key(UvcParameterType.ZOOM, Int::class.java)
	
	//自动对焦
	@JvmField
	val AUTO_FOCUS = Key(UvcParameterType.AUTO_FOCUS, Boolean::class.java)
	
	// 聚焦设置
	@JvmField
	val FOCUS = Key(UvcParameterType.FOCUS, Int::class.java)
	
	// 光圈
	@JvmField
	val IRIS = Key(UvcParameterType.IRIS, Int::class.java)
	
	//自动设置色调
	@JvmField
	val AUTO_HUE = Key(UvcParameterType.AUTO_HUE, Boolean::class.java)
	
	// 色调
	@JvmField
	val HUE = Key(UvcParameterType.HUE, Int::class.java)
	
	//自动白平衡
	@JvmField
	val AUTO_WHITE_BALANCE = Key(UvcParameterType.AUTO_WHITE_BALANCE, Boolean::class.java)
	
	// 白平衡
	@JvmField
	val WHITE_BALANCE = Key(UvcParameterType.WHITE_BALANCE, Int::class.java)
	
	// 隐私协议
	@JvmField
	val PRIVACY = Key(UvcParameterType.PRIVACY, Boolean::class.java)
	
	// 预览方向变换
	@JvmField
	val ORIENTATION = Key(UvcParameterType.ORIENTATION, Int::class.java)
	
	// 镜像处理
	@JvmField
	val MIRROR = Key(UvcParameterType.MIRROR, Boolean::class.java)
}

/**
 * 获取支持的参数
 */
object UvcSupportParameter {
	data class Key<T>(val type: String, val mClass: Class<T>)
	
	//自动曝光
	@JvmField
	val AUTO_EXPOSURE = Key(UvcParameterType.AUTO_EXPOSURE, Boolean::class.java)
	
	//曝光度
	@JvmField
	val EXPOSURE = Key(UvcParameterType.EXPOSURE, IntRange::class.java)
	
	//亮度
	@JvmField
	val BRIGHTNESS = Key(UvcParameterType.BRIGHTNESS, IntRange::class.java)
	
	//对比度
	@JvmField
	val CONTRAST = Key(UvcParameterType.CONTRAST, IntRange::class.java)
	
	//增益值
	@JvmField
	val GAIN = Key(UvcParameterType.GAIN, IntRange::class.java)
	
	//饱和度
	@JvmField
	val SATURATION = Key(UvcParameterType.SATURATION, IntRange::class.java)
	
	//缩放
	@JvmField
	val ZOOM = Key(UvcParameterType.ZOOM, IntRange::class.java)
	
	//自动对焦
	@JvmField
	val AUTO_FOCUS = Key(UvcParameterType.AUTO_FOCUS, Boolean::class.java)
	
	// 聚焦设置
	@JvmField
	val FOCUS = Key(UvcParameterType.FOCUS, IntRange::class.java)
	
	// 光圈
	@JvmField
	val IRIS = Key(UvcParameterType.IRIS, IntRange::class.java)
	
	//自动设置色调
	@JvmField
	val AUTO_HUE = Key(UvcParameterType.AUTO_HUE, Boolean::class.java)
	
	// 色调
	@JvmField
	val HUE = Key(UvcParameterType.HUE, IntRange::class.java)
	
	//自动白平衡
	@JvmField
	val AUTO_WHITE_BALANCE = Key(UvcParameterType.AUTO_WHITE_BALANCE, Boolean::class.java)
	
	// 白平衡
	@JvmField
	val WHITE_BALANCE = Key(UvcParameterType.WHITE_BALANCE, IntRange::class.java)
	
	// 隐私协议
	@JvmField
	val PRIVACY = Key(UvcParameterType.PRIVACY, Boolean::class.java)
	
	// 分辨率
	@JvmField
	val PREVIEW_SIZE = Key(UvcParameterType.PREVIEW_SIZE, Array<UvcCameraSupportSize>::class.java)
}