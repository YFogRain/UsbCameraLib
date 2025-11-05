package com.once.camera.listener

/**
 * 预览回调监听
 */
interface IPreviewListener {
	/**
	 * 默认的回调
	 * @param bytes 预览数据
	 * @param format 数据的类型
	 * @param width 预览宽度
	 * @param height 预览高度
	 */
	fun onPreview(bytes: ByteArray, width: Int, height: Int)
}