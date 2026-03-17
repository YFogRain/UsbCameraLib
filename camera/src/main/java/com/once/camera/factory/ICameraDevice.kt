package com.once.camera.factory

import android.graphics.Bitmap
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import com.once.camera.mode.FaceDetectMode
import com.once.camera.parameters.CameraPreviewFormat
import com.once.camera.parameters.Parameters
import com.once.camera.parameters.SupportParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des
 */
abstract class ICameraDevice {
	// 预览监听
	protected var iPreviewListener: ((bytes: ByteArray, width: Int, height: Int) -> Unit)? = null
	
	// 屏幕方向,默认为0
	protected var mDisplayOrientation: Int = 0
	
	// 拍照方向
	protected var mPicOrientation: Int = 0
	
	// 是否需要镜像处理（仅处理照片时使用）
	protected var mIsJpegMirror: Boolean = false
	
	//摄像头异常关闭监听
	protected var iDetachedCloseListener: (() -> Unit)? = null
	
	//人脸检测数据回调
	protected var mFaceDetectListener: ((Array<FaceDetectMode>) -> Unit)? = null
	
	protected val isPreviewIng = AtomicBoolean(false)
	
	/**
	 * 关闭摄像头
	 */
	fun close(): Boolean {
		this.mFaceDetectListener = null
		this.iPreviewListener = null
		this.iDetachedCloseListener = null
		stopFaceDetection()
		return closeCamera()
	}
	
	/**
	 * 设置预览分辨率
	 */
	abstract fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean
	
	/**
	 * 打开预览
	 */
	abstract fun startPreview()
	
	/**
	 * 关闭预览
	 */
	abstract fun stopPreview()
	
	/**
	 * 设置预览控件
	 */
	abstract fun setDisplaySurface(view: SurfaceView): Boolean
	abstract fun setDisplaySurface(view: TextureView): Boolean
	abstract fun setDisplaySurface(surface: Surface): Boolean
	
	/**
	 * 设置预览监听
	 */
	fun setPreviewListener(listener: ((bytes: ByteArray, width: Int, height: Int) -> Unit)?) {
		this.iPreviewListener = listener
	}
	
	/**
	 * 设置usb摄像头断开关闭回调
	 */
	fun setDetachedCloseListener(listener: (() -> Unit)? = null) {
		this.iDetachedCloseListener = listener
	}
	
	/**
	 * 数据回调
	 */
	protected fun pullPreview(data: ByteArray, width: Int, height: Int) {
		runCatching { iPreviewListener?.invoke(data, width, height) }
	}
	
	/**
	 * 设置参数
	 */
	abstract fun <V> setParameter(key: Parameters.Key<V>, value: V): Boolean
	
	/**
	 * 获取参数
	 */
	abstract fun <V> getParameter(key: Parameters.Key<V>): V?
	
	/**
	 * 获取支持的类型列表
	 */
	abstract fun <T> getSupportParameters(supportKey: SupportParameters.Key<T>): T?
	
	/**
	 * 关闭摄像头
	 */
	protected abstract fun closeCamera(): Boolean
	
	/**
	 * 开启人脸检测-需要在开启预览之后
	 */
	abstract fun startFaceDetection(): Boolean
	
	/**
	 * 结束人脸检测
	 */
	abstract fun stopFaceDetection(): Boolean
	
	/**
	 * 拍照
	 * @param cropWidth 裁剪的宽度（-1表示不裁剪）
	 * @param cropHeight 裁剪的高度（-1表示不裁剪）
	 */
	protected abstract suspend fun takePicture(cropWidth: Int = -1, cropHeight: Int = -1): Bitmap?
	
	/**
	 * 获取当前的设备预览方向
	 */
	protected fun loadOrientation(sensorOrientation: Int, isFont: Boolean, rotation: Int): Int {
		// 根据当前的前后置方向，以及感应器方向，window的方向来配置预览旋转角度
		val degrees = when (rotation) {
			Surface.ROTATION_0 -> 0
			Surface.ROTATION_90 -> 90
			Surface.ROTATION_180 -> 180
			Surface.ROTATION_270 -> 270
			else -> 0
		}
		return if (isFont) {
			(360 - ((sensorOrientation + degrees) % 360)) % 360
		} else (sensorOrientation - degrees + 360) % 360
	}
	
	/**
	 * 获取当前镜像状态
	 */
	fun loadJpegMirrorState() = mIsJpegMirror
	
	/**
	 * 同步请求拍照结果
	 */
	suspend fun takeSyncPicture(cropWidth: Int = -1, cropHeight: Int = -1): Bitmap? {
		return withContext(Dispatchers.IO) {
			return@withContext takePicture(cropWidth, cropHeight)
		}
	}
	
	/**
	 * 裁剪拍照
	 */
	suspend fun takeCropPicture(view: View? = null): Bitmap? {
		return withContext(Dispatchers.IO) {
			return@withContext takePicture(view?.measuredWidth ?: -1, view?.measuredHeight ?: -1)
		}
	}
}