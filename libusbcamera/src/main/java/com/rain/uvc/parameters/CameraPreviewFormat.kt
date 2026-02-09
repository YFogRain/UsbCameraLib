package com.rain.uvc.parameters

/**
 * @author yuan
 * @createTime: 2026/2/2
 * @des 当前支持的预览格式
 */
sealed class CameraPreviewFormat(val value: Int) {
	companion object {
		@JvmStatic
		fun valueToFormatMode(format: Int): CameraPreviewFormat {
			return when (format) {
				0 -> BGR;
				2 -> return NV21
				3 -> return NV12
				5 -> return  RGB
				6 -> return  MJPEG
				7 -> return  JPEG
				else -> return  YUY2
			}
		}
	}
	
	data object BGR : CameraPreviewFormat(0)
	data object YUY2 : CameraPreviewFormat(1)
	data object NV21 : CameraPreviewFormat(2)
	data object NV12 : CameraPreviewFormat(3)
	data object RGB : CameraPreviewFormat(5)
	data object MJPEG : CameraPreviewFormat(6)
	data object JPEG : CameraPreviewFormat(7)
}