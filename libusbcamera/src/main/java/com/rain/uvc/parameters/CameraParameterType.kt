package com.rain.uvc.parameters

/**
 * 支持的预览参数集合
 */
object CameraParameterType {
	const val PREVIEW_SIZE = "com.camera.control.preview_size" //预览分辨率
	const val AUTO_EXPOSURE = "com.camera.control.auto_exposure" //自动曝光开关
	const val EXPOSURE = "com.camera.control.exposure" //曝光度
	const val BRIGHTNESS = "com.camera.control.brightness" //亮度
	const val CONTRAST = "com.camera.control.contrast" //对比度
	const val GAIN = "com.camera.control.gain" //增益值
	const val SATURATION = "com.camera.control.saturation" //饱和度
	const val ZOOM = "com.camera.control.zoom" //缩放
	const val FLASH = "com.camera.control.flash" //闪光灯
	const val FOCUS = "com.camera.control.focus" //焦距设置
	const val IRIS = "com.camera.control.iris" //光圈
	const val WHITE_BALANCE = "com.camera.control.white_balance" //白平衡
	const val SCENE_MODE = "com.camera.control.scene_mode" //场景模式
	const val PRIVACY = "com.camera.control.privacy" //隐私模式
	const val COLOR_EFFECTS = "com.camera.control.color_effects" //滤镜效果
	const val ISO = "com.camera.control.iso" //iso值
	const val FACE_DETECT = "com.camera.control.face_detect" //人脸检测
	const val JPEG_MIRROR = "com.camera.control.jpeg_mirror" // 是否拍照镜像
	const val AUTO_WHITE_BALANCE = "com.camera.control.auto_white_balance" //自动模式白平衡
	const val AUTO_FOCUS = "com.camera.control.auto_focus" //自动对焦设置
	const val AUTO_HUE = "com.camera.control.auto_hue" //自动设置色调
	const val HUE = "com.camera.control.hue"//色调
	
	const val PREVIEW_ORIENTATION = "com.camera.control.preview_orientation" // 方向
	const val SENSOR_ORIENTATION = "com.camera.control.sensor_orientation" // 获取传感器方向
	const val PIC_ORIENTATION = "com.camera.control.pic_orientation" // 拍照方向。默认跟预览方向相同
	
}