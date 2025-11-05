package com.once.camera.type

/**
 * 支持的预览参数集合
 */
object CameraParameterType {
	const val PREVIEW_SIZE: String = "com.camera.control.preview_size" //预览分辨率
	const val PREVIEW_FORMAT: String = "com.camera.control.preview_format" // 预览格式
	const val AUTO_EXPOSURE: String = "com.camera.control.auto_exposure" //自动曝光开关
	const val EXPOSURE: String = "com.camera.control.exposure" //曝光度
	const val BRIGHTNESS: String = "com.camera.control.brightness" //亮度
	const val CONTRAST: String = "com.camera.control.contrast" //对比度
	const val GAIN: String = "com.camera.control.gain" //增益值
	const val SATURATION: String = "com.camera.control.saturation" //饱和度
	const val ZOOM: String = "com.camera.control.zoom" //缩放
	const val FLASH: String = "com.camera.control.flash" //闪光灯
	const val ORIENTATION: String = "com.camera.control.orientation" // 方向
	const val JPEG_MIRROR: String = "com.camera.control.jpeg_mirror" // 是否拍照镜像
	const val FOCUS: String = "com.camera.control.focus" //焦距设置
	const val IRIS: String = "com.camera.control.iris" //光圈
	const val WHITE_BALANCE: String = "com.camera.control.white_balance" //白平衡
	const val SCENE_MODE: String = "com.camera.control.scene_mode" //场景模式
	const val PRIVACY: String = "com.camera.control.privacy" //隐私模式
	const val COLOR_EFFECTS: String = "com.camera.control.color_effects" //滤镜效果
	const val ISO: String = "com.camera.control.iso" //iso值
	const val FACE_DETECT: String = "com.camera.control.face_detect" //人脸检测
}