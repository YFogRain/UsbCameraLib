package com.rain.uvc.parameters

import com.rain.uvc.mode.CameraSize
import com.rain.uvc.mode.CameraSupportSize

/**
 * 可使用的参数配置文件
 */
object Parameters {
	data class Key<T>(val type: String, val mClass: Class<T>)
	
	//自动曝光开关
	@JvmField
	val AUTO_EXPOSURE = Key(CameraParameterType.AUTO_EXPOSURE, Boolean::class.java)
	
	//曝光度
	@JvmField
	val EXPOSURE = Key(CameraParameterType.EXPOSURE, Int::class.java)
	
	//亮度
	@JvmField
	val BRIGHTNESS = Key(CameraParameterType.BRIGHTNESS, Int::class.java)
	
	//对比度
	@JvmField
	val CONTRAST = Key(CameraParameterType.CONTRAST, Int::class.java)
	
	//增益值
	@JvmField
	val GAIN = Key(CameraParameterType.GAIN, Int::class.java)
	
	//饱和度
	@JvmField
	val SATURATION = Key(CameraParameterType.SATURATION, Int::class.java)
	
	// 缩放
	@JvmField
	val ZOOM = Key(CameraParameterType.ZOOM, Int::class.java)
	
	// 闪光灯
	@JvmField
	val FLASH = Key(CameraParameterType.FLASH, Boolean::class.java)
	
	//分辨率
	@JvmField
	val PREVIEW_SIZE = Key(CameraParameterType.PREVIEW_SIZE, CameraSize::class.java)
	
	//iso值
	@JvmField
	val ISO = Key(CameraParameterType.ISO, String::class.java)
	
	// 焦距设置
	@JvmField
	val FOCUS = Key(CameraParameterType.FOCUS, String::class.java)
	
	// 光圈
	@JvmField
	val IRIS = Key(CameraParameterType.IRIS, Float::class.java)
	
	// 白平衡
	@JvmField
	val WHITE_BALANCE = Key(CameraParameterType.WHITE_BALANCE, String::class.java)
	
	// 场景模式
	@JvmField
	val SCENE_MODE = Key(CameraParameterType.SCENE_MODE, String::class.java)
	
	// 隐私模式
	@JvmField
	val PRIVACY = Key(CameraParameterType.PRIVACY, Boolean::class.java)
	
	// 预览方向变换
	@JvmField
	val PREVIEW_ORIENTATION = Key(CameraParameterType.PREVIEW_ORIENTATION, Int::class.java)
	
	// 镜像处理
	@JvmField
	val JPEG_MIRROR = Key(CameraParameterType.JPEG_MIRROR, Boolean::class.java)
	
	// 拍照方向
	@JvmField
	val PIC_ORIENTATION = Key(CameraParameterType.PIC_ORIENTATION, Int::class.java)
	
	//    public static final Key<String> SCENE_MODE = new Key<>(CameraParameterType.SCENE_MODE, String.class);
	//    public static final Key<String> WHITE_BALANCE = new Key<>(CameraParameterType.WHITE_BALANCE, String.class);
	//    public static final Key<String> COLOR_EFFECTS = new Key<>(CameraParameterType.COLOR_EFFECTS, String.class);
	//    public static final Key<String> ISO = new Key<>(CameraParameterType.ISO, String.class);
	// 自动对焦
	val AUTO_FOCUS = Key(CameraParameterType.AUTO_FOCUS, Boolean::class.java)
	
	// 色温自动
	val AUTO_HUE = Key(CameraParameterType.AUTO_HUE, Boolean::class.java)
	
	// 色温
	val HUE = Key(CameraParameterType.HUE, Int::class.java)
	
	// 自动白平衡
	val AUTO_WHITE_BALANCE = Key(CameraParameterType.AUTO_WHITE_BALANCE, Boolean::class.java)
}

/**
 * 获取支持的参数
 */
object SupportParameters {
	data class Key<T>(val type: String, val mClass: Class<T>)
	
	//是否支持人脸检测
	@JvmField
	val FACE_DETECT = Key(CameraParameterType.FACE_DETECT, Boolean::class.java)
	
	//自动曝光
	@JvmField
	val AUTO_EXPOSURE = Key(CameraParameterType.AUTO_EXPOSURE, Boolean::class.java)
	
	//曝光度
	@JvmField
	val EXPOSURE = Key(CameraParameterType.EXPOSURE, IntRange::class.java)
	
	//亮度
	@JvmField
	val BRIGHTNESS = Key(CameraParameterType.BRIGHTNESS, IntRange::class.java)
	
	//对比度
	@JvmField
	val CONTRAST = Key(CameraParameterType.CONTRAST, IntRange::class.java)
	
	//增益值
	@JvmField
	val GAIN = Key(CameraParameterType.GAIN, IntRange::class.java)
	
	//饱和度
	@JvmField
	val SATURATION = Key(CameraParameterType.SATURATION, IntRange::class.java)
	
	//缩放
	@JvmField
	val ZOOM = Key(CameraParameterType.ZOOM, IntRange::class.java)
	
	// 闪光灯
	@JvmField
	val FLASH = Key(CameraParameterType.ZOOM, Boolean::class.java)
	
	// 滤镜效果
	@JvmField
	val COLOR_EFFECTS = Key(CameraParameterType.COLOR_EFFECTS, Array<String>::class.java)
	
	// 分辨率
	@JvmField
	val PREVIEW_SIZE = Key(CameraParameterType.PREVIEW_SIZE, Array<CameraSupportSize>::class.java)
	
	// 聚焦设置
	@JvmField
	val FOCUS = Key(CameraParameterType.FOCUS, Array<String>::class.java)
	
	// 光圈
	@JvmField
	val IRIS = Key(CameraParameterType.IRIS, FloatArray::class.java)
	
	// 白平衡
	@JvmField
	val WHITE_BALANCE = Key(CameraParameterType.WHITE_BALANCE, Array<String>::class.java)
	
	// 场景模式
	@JvmField
	val SCENE_MODE = Key(CameraParameterType.SCENE_MODE, Array<String>::class.java)
	
	// 隐私协议
	@JvmField
	val PRIVACY = Key(CameraParameterType.PRIVACY, Boolean::class.java)
	
	// 传感器方向
	@JvmField
	val SENSOR_ORIENTATION = Key(CameraParameterType.SENSOR_ORIENTATION, Int::class.java)
	
	// 自动聚焦
	@JvmField
	val AUTO_FOCUS = Key(CameraParameterType.AUTO_FOCUS, Boolean::class.java)
	
	// 自动色温
	@JvmField
	val AUTO_HUE = Key(CameraParameterType.AUTO_HUE, Boolean::class.java)
	
	// 色温
	@JvmField
	val HUE = Key(CameraParameterType.HUE, IntRange::class.java)
	
	// 自动白平衡
	@JvmField
	val AUTO_WHITE_BALANCE = Key(CameraParameterType.AUTO_WHITE_BALANCE, Boolean::class.java)
	
}