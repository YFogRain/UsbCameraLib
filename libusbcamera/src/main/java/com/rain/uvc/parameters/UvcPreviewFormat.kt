package com.rain.uvc.parameters

/**
 * @author yuan
 * @createTime: 2026/4/3
 * @des 预览支持的格式
 */
sealed class UvcPreviewFormat(val format: Int) {
	data object BGR : UvcPreviewFormat(0)
	data object YUY2 : UvcPreviewFormat(1)
	data object NV21 : UvcPreviewFormat(2)
	data object NV12 : UvcPreviewFormat(3)
	data object RGB : UvcPreviewFormat(5)
	data object MJPEG : UvcPreviewFormat(6)
	data object JPEG : UvcPreviewFormat(7)
	data object YUV420SP : UvcPreviewFormat(8)
}

/**
 * uvc预览回调
 */
sealed class UvcDataFormat(val format: Int) {
	data object BGR : UvcDataFormat(0)
	data object YUY2 : UvcDataFormat(1)
	data object NV21 : UvcDataFormat(2)
	data object RGBA : UvcDataFormat(4)
	data object RGB : UvcDataFormat(5)
	data object MJPEG : UvcDataFormat(6)
}

/**
 * 转换为输入的格式
 */
fun Int.valueToFormat(): UvcPreviewFormat {
	return when (this) {
		0 -> UvcPreviewFormat.BGR
		2 -> UvcPreviewFormat.NV21
		3 -> UvcPreviewFormat.NV12
		5 -> UvcPreviewFormat.RGB
		6 -> UvcPreviewFormat.MJPEG
		7 -> UvcPreviewFormat.JPEG
		8 -> UvcPreviewFormat.YUV420SP
		else -> UvcPreviewFormat.YUY2
	}
}