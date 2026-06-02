@file:Suppress("UNCHECKED_CAST")

package com.rain.uvc.bridge

import android.view.Surface
import com.rain.uvc.listener.IButtonListener
import com.rain.uvc.listener.IFrameListener
import com.rain.uvc.mode.UvcCameraSize
import com.rain.uvc.mode.UvcCameraSupportSize
import com.rain.uvc.parameters.UvcCameraParameter
import com.rain.uvc.parameters.UvcParameterType
import com.rain.uvc.parameters.UvcSupportParameter
import com.rain.uvc.parameters.valueToFormat
import org.json.JSONArray

/**
 * @author yuan
 * @createTime: 2026/5/15
 * @des cpp调用native层接口
 */
internal object UvcNativeBridge {
	init {
		System.loadLibrary("usb100")
		System.loadLibrary("uvc06")
		System.loadLibrary("uvcCamera")
	}
	
	/**
	 * 初始化debug模式
	 */
	@JvmStatic
	external fun debuggable(status: Boolean): Boolean
	
	/**
	 * 读取可使用的v4l2的列表信息
	 *
	 * @return 返回列表地址
	 */
	@JvmStatic
	external fun nativeLoadV4L2Devices(): Array<String>?
	
	/**
	 * 根据对应的FileDescriptor连接指定设备
	 *
	 * @param fd 文件描述符
	 * @return 是否成功
	 */
	@JvmStatic
	external fun nativeOpen(fd: Int, busNum: Int, devAddress: Int): Long
	
	/**
	 * 根据对应的FileDescriptor连接指定设备
	 *
	 * @param videoPath 对应打开的路径
	 * @return 是否成功
	 */
	@JvmStatic
	external fun nativeOpenVideo(videoPath: String?): Long
	
	/**
	 * 断开连接设备
	 *
	 * @param nativeId 对应的内存地址值
	 * @return 是否成功
	 */
	@JvmStatic
	external fun nativeClose(nativeId: Long): Boolean
	
	/**
	 * 开启预览
	 *
	 * @param nativeId 对应的内存地址值
	 * @return 是否成功
	 */
	@JvmStatic
	external fun nativeStartPreview(nativeId: Long): Boolean
	
	/**
	 * 关闭预览
	 *
	 * @param nativeId 对应的内存地址值
	 * @return 是否成功
	 */
	@JvmStatic
	external fun nativeStopPreview(nativeId: Long): Boolean
	
	/**
	 * 添加一个surface
	 * @param nativeId 对应的内存地址值
	 * @param targetId 对应的targetId
	 * @param isRecord 是否为录制
	 * @param surface 对应的surface
	 */
	@JvmStatic
	external fun nativeAddSurfaceTarget(nativeId: Long, targetId: String, isRecord: Boolean, surface: Surface): Boolean
	
	/**
	 * 移除一个surface
	 * @param nativeId 对应的内存地址值
	 * @param targetId 对应的targetId
	 */
	
	@JvmStatic
	external fun nativeRemoveSurfacesTarget(nativeId: Long, targetId: String): Boolean
	
	/**
	 * 设置预览的分辨率信息
	 *
	 * @param nativeId 对应的内存地址值
	 * @param width    宽
	 * @param height   高
	 * @return 是否成功
	 */
	@JvmStatic
	external fun nativeSetPreviewSize(nativeId: Long, width: Int, height: Int, format: Int): Boolean
	
	/**
	 * 设置预览监听，返回的数据格式固定为RGB格式
	 *
	 * @param nativeId 设置的对应id
	 * @param listener 监听器
	 */
	@JvmStatic
	external fun setPreviewListener(nativeId: Long, listener: IFrameListener?, mode: Int): Boolean
	
	/**
	 * 设置按钮监听
	 * @param nativeId 设置的对应id
	 * @param listener 监听器
	 */
	@JvmStatic
	external fun setButtonListener(nativeId: Long, listener: IButtonListener?): Boolean
	
	/**
	 * 获取支持的参数信息
	 */
	@JvmStatic
	external fun nativeGetSupportedParameters(nativeId: Long, type: Int): String?
	
	/**
	 * 获取当前参数信息
	 */
	@JvmStatic
	external fun nativeGetParameterValue(nativeId: Long, type: Int): String?
	
	/**
	 * 设置当前参数信息
	 */
	@JvmStatic
	external fun nativeSetParameterValue(nativeId: Long, type: Int, value: Int): Boolean
	
	/**
	 * 执行拍照
	 */
	@JvmStatic
	external fun nativeTakePicture(nativeId: Long): ByteArray?
	
	/**
	 * 启动录制（captureThread 读取该状态后启动 swapToRecord）。
	 */
	@JvmStatic
	external fun nativeStartRecord(nativeId: Long): Boolean
	
	/**
	 * 停止录制。
	 */
	@JvmStatic
	external fun nativeStopRecord(nativeId: Long): Boolean
	
	/**
	 * 获取对应的参数信息
	 *
	 * @param nativeId 对应的内存地址值
	 * @param key      对应支持的参数key
	 * @return 返回值，如果不支持或者不存在，则返回null
	 */
	@JvmStatic
	fun <T> getParameter(nativeId: Long, key: UvcCameraParameter.Key<T>): T? {
		if (nativeId == 0L) return null
		// 获取到native层返回的数据
		val returnValue = nativeGetParameterValue(nativeId, key.type.strToKeyId())
		if (returnValue.isNullOrBlank()) return null
		return when (key.mClass) {
			UvcCameraSize::class.java -> {
				val sizeArray = returnValue.split(":")
				if (sizeArray.size < 2) return null
				UvcCameraSize(
					sizeArray[0].toInt(), sizeArray[1].toInt()
				)
			}
			String::class.java -> returnValue
			Int::class.java, Int::class.javaPrimitiveType -> returnValue.toIntOrNull()
			Boolean::class.java, Boolean::class.javaPrimitiveType -> returnValue.toIntOrNull() == 1
			Double::class.java, Double::class.javaPrimitiveType -> returnValue.toDoubleOrNull()
			Float::class.java, Float::class.javaPrimitiveType -> returnValue.toIntOrNull()?.toFloat()
			else -> null
		} as? T
	}
	
	/**
	 * 设置对应的参数信息
	 *
	 * @param nativeId 对应的内存地址值
	 * @param key      对应支持的参数key
	 * @param value    对应的值
	 * @return 是否支持
	 */
	fun <T> setParameter(nativeId: Long, key: UvcCameraParameter.Key<T>, value: T): Boolean {
		if (nativeId == 0L) return false
		val keyId = key.type.strToKeyId()
		if (keyId == -1) return false
		return when (key.mClass) {
			Int::class.java, Int::class.javaPrimitiveType -> nativeSetParameterValue(
				nativeId, keyId, value as Int
			)
			Float::class.java -> nativeSetParameterValue(
				nativeId, keyId, (value as Float).toInt()
			)
			Boolean::class.java, Boolean::class.javaPrimitiveType -> nativeSetParameterValue(
				nativeId, keyId, if (value as Boolean) 1 else 0
			)
			else -> false
		}
	}
	
	/**
	 * 获取对应支持的参数列表信息
	 * @param nativeId 对应的内存地址值
	 * @param key      对应支持的参数key
	 * @return 对应支持的列表
	 */
	fun <T> getSupportedParameter(nativeId: Long, key: UvcSupportParameter.Key<T>): T? {
		if (nativeId == 0L) return null
		val keyId = key.type.strToKeyId()
		if (keyId == -1) return null
		val returnValue: String? = nativeGetSupportedParameters(nativeId, keyId)
		if (returnValue.isNullOrBlank()) return null
		return when (key.mClass) {
			Array<UvcCameraSupportSize>::class.java -> runCatching {
				val groupArray = JSONArray(returnValue)
				if (groupArray.length() <= 0) return@runCatching null
				return@runCatching Array(groupArray.length()) {
					val jsonObject = groupArray.optJSONObject(it)
					val format = jsonObject.optInt("format")
					val sizes = jsonObject.optJSONArray("sizes")
					val sizeList = if (sizes != null && sizes.length() > 0) {
						List(sizes.length()) { childIndex ->
							val childJsonObj = sizes.getJSONObject(childIndex)
							val width = childJsonObj.getInt("width")
							val height = childJsonObj.getInt("height")
							UvcCameraSize(width, height)
						}
					} else listOf()
					return@Array UvcCameraSupportSize(
						format.valueToFormat(), sizeList
					)
				}
			}.getOrNull()
			Boolean::class.java, Boolean::class.javaPrimitiveType -> returnValue.toInt() == 1
			IntRange::class.java -> {
				if (returnValue.contains(":")) {
					val split = returnValue.split(":")
					if (split.size == 2) IntRange(split[0].toInt(), split[1].toInt()) else null
				} else null
			}
			else -> null
		} as? T
	}
}

/**
 * 将当前的key转为对应native层需要的id
 */
private fun String.strToKeyId(): Int {
	return when (this) {
		UvcParameterType.PREVIEW_SIZE -> 0        // 预览分辨率
		UvcParameterType.ORIENTATION -> 1         // 预览方向
		UvcParameterType.AUTO_EXPOSURE -> 2       // 自动曝光
		UvcParameterType.EXPOSURE -> 3            // 曝光度
		UvcParameterType.BRIGHTNESS -> 4          // 亮度
		UvcParameterType.CONTRAST -> 5            // 对比度
		UvcParameterType.GAIN -> 6                // 增益值
		UvcParameterType.SATURATION -> 7          // 饱和度
		UvcParameterType.ZOOM -> 8                // 缩放
		UvcParameterType.AUTO_FOCUS -> 9          // 自动对焦
		UvcParameterType.FOCUS -> 10              // 焦距
		UvcParameterType.IRIS -> 11               // 光圈
		UvcParameterType.AUTO_HUE -> 12           // 自动变化色调
		UvcParameterType.HUE -> 13                // 色调
		UvcParameterType.AUTO_WHITE_BALANCE -> 14 // 自动白平衡
		UvcParameterType.WHITE_BALANCE -> 15      // 白平衡
		UvcParameterType.PRIVACY -> 16            // 隐私模式
		UvcParameterType.MIRROR -> 17             // 镜像处理
		else -> -1
	}
}
