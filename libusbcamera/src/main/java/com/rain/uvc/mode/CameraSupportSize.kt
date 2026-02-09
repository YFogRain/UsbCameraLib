package com.rain.uvc.mode

import com.rain.uvc.parameters.CameraPreviewFormat

/**
 * @author yuan
 * @createTime: 2025/12/7
 * @des 相机
 * @param format 支持的最大fps是否时mjpeg格式
 * @param sizes 对应的分辨率列表
 */
data class CameraSupportSize(val format: CameraPreviewFormat, val sizes: List<CameraSize>)