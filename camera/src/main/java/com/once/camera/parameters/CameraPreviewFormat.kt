package com.once.camera.parameters

/**
 * 预览可使用的类型
 */
enum class CameraPreviewFormat(val value: Int) {
	
	BGR(0), YUY2(1), NV21(2), NV12(3), RGB(5), MJPEG(6), JPEG(7), YUV_420_888(8);
	
	companion object {
		@JvmStatic
		fun valueToFormatMode(format: Int): CameraPreviewFormat {
			return when (format) {
				0 -> BGR
				2 -> NV21
				3 -> NV12
				5 -> RGB
				6 -> MJPEG
				7 -> JPEG
				8 -> YUV_420_888
				else -> YUY2
			}
		}
	}
}