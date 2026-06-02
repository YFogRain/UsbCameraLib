package com.rain.camera.uvc.parameter

/**
 * @author yuan
 * @createTime: 2026/4/3
 * @des 当前支持的类型
 */
object UvcParameterType {
	const val AUTO_EXPOSURE: String = "com.camera.control.auto_exposure" //自动曝光开关
	const val EXPOSURE: String = "com.camera.control.exposure" //曝光度
	const val BRIGHTNESS: String = "com.camera.control.brightness" //亮度
	const val CONTRAST: String = "com.camera.control.contrast" //对比度
	const val GAIN: String = "com.camera.control.gain" //增益值
	const val SATURATION: String = "com.camera.control.saturation" //饱和度
	
	const val ZOOM: String = "com.camera.control.zoom" //缩放
	
	const val AUTO_FOCUS: String = "com.camera.control.auto_focus" //自动对焦设置
	const val FOCUS: String = "com.camera.control.focus" //焦距设置
	
	const val IRIS: String = "com.camera.control.iris" //光圈
	
	const val AUTO_HUE: String = "com.camera.control.auto_hue" //自动设置色调
	const val HUE: String = "com.camera.control.hue" //色调
	
	const val AUTO_WHITE_BALANCE: String = "com.camera.control.auto_white_balance" //自动模式白平衡
	const val WHITE_BALANCE: String = "com.camera.control.white_balance" //白平衡
	
	const val PRIVACY: String = "com.camera.control.privacy" //隐私模式
	
	const val PREVIEW_SIZE: String = "com.camera.control.preview_size" //预览分辨率
	
	const val MIRROR : String = "com.camera.control.mirror" // 是否镜像
	const val ORIENTATION: String = "com.camera.control.orientation" // 方向
}