package com.rain.uvc.mode

import android.graphics.Point
import android.graphics.Rect

/**
 * @author yuan
 * @createTime: 2024/11/5
 * @des 人脸检测的数据值
 */
data class FaceDetectMode(val faceRect: Rect, val score: Int, val leftEye: Point, val rightEye: Point, val mouth: Point)