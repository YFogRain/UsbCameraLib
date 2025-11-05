@file:Suppress("DEPRECATION")

package com.once.camera.factory.protogenesis.camera1

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.Parameters.FLASH_MODE_OFF
import android.hardware.Camera.Parameters.FLASH_MODE_ON
import android.hardware.Camera.Parameters.FLASH_MODE_TORCH
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.once.camera.factory.ICameraDevice
import com.once.camera.mode.CameraSize
import com.once.camera.mode.FaceDetectMode
import com.once.camera.parameters.CameraPreviewFormat
import com.once.camera.parameters.Parameters
import com.once.camera.parameters.SupportParameters
import com.once.camera.type.CameraParameterType
import com.once.camera.utils.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des camera1操作实例
 */
class Camera1Device(camera: Camera, cameraId: Int, acRotation: Int) : ICameraDevice() {
	private val mCameraAtomic = AtomicReference(camera)
	
	// 缓存控件
	private val mTextureCache = AtomicReference<TextureView>()
	
	init {
		initNormalParameters(camera)
		initCameraFacing(cameraId, acRotation)
	}
	
	/**
	 * 初始化当前预览方向,是否镜像等
	 */
	private fun initCameraFacing(cameraId: Int, acRotation: Int) {
		val cameraInfo = CameraInfo()
		val loadInfoState = runCatching { Camera.getCameraInfo(cameraId, cameraInfo) }.isSuccess
		if (!loadInfoState) return
		// 判断是否前置，是的话默认镜像处理
		mIsJpegMirror = cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT //初始化镜像，判断是否是前置，前置默认镜像处理
		mDisplayOrientation = loadOrientation(cameraInfo.orientation, mIsJpegMirror, acRotation)
	}
	
	override fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean {
		val mCamera = mCameraAtomic.get() ?: return false
		val parameters = mCamera.parameters
		parameters.setPreviewSize(width, height)
		parameters.previewFormat = format.toFormat()
		return runCatching { mCamera.parameters = parameters }.isSuccess
	}
	
	override suspend fun startPreview(): Boolean {
		if (isPreviewIng.get()) return false
		val camera = mCameraAtomic.get() ?: return false
		val previewSize = camera.parameters.previewSize
		camera.setPreviewCallback { data, _ ->
			if (mCameraAtomic.get() == null) return@setPreviewCallback
			pullPreview(data, previewSize.width, previewSize.height)
		}
		val previewResult = runCatching { camera.startPreview() }.onFailure { it.printStackTrace() }.isSuccess
		isPreviewIng.set(true)
		return previewResult
	}
	
	override fun stopPreview(): Boolean {
		if (isPreviewIng.get()) {
			val camera = mCameraAtomic.get()
			camera?.stopPreview()
			camera?.setPreviewCallback(null)
		}
		isPreviewIng.set(false)
		return true
	}
	
	override fun setDisplaySurface(view: SurfaceView): Boolean {
		val camera = mCameraAtomic.get() ?: return false
		return runCatching { camera.setPreviewDisplay(view.holder) }.isSuccess
	}
	
	override fun setDisplaySurface(view: TextureView): Boolean {
		val camera = mCameraAtomic.get() ?: return false
		mTextureCache.set(view)
		return runCatching { camera.setPreviewTexture(view.surfaceTexture) }.isSuccess
	}
	
	override fun setDisplaySurface(surface: Surface): Boolean {
		val camera = mCameraAtomic.get() ?: return false
		try {
			val mClass = Class.forName("android.hardware.Camera")
			
			val method = mClass.getMethod("setPreviewSurface", Surface::class.java)
			method.isAccessible = true
			method.invoke(camera, surface)
			return true
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return false
	}
	
	override fun <V> setParameter(key: Parameters.Key<V>, value: V): Boolean {
		val parameters = mCameraAtomic.get()?.parameters ?: return false
		when (key.type) {
			CameraParameterType.AUTO_EXPOSURE -> {
				if (value !is Boolean) return false
				//曝光度设置
				if (!parameters.isAutoExposureLockSupported) {
					return false
				}
				//当前值为自动曝光锁定（锁定时无法修改自动曝光）
				parameters.autoExposureLock = !value
			}
			CameraParameterType.EXPOSURE -> {
				//自动曝光打开时无法设置曝光度
				if (value !is Int) return false
				val currentMode = !parameters.isAutoExposureLockSupported || !parameters.autoExposureLock
				if (currentMode) return false
				//如果最大值为0，且最小值等同于最大值，说明不支持设置曝光度
				if (parameters.maxExposureCompensation == 0 && parameters.minExposureCompensation == parameters.maxExposureCompensation) return false
				//设置曝光度
				parameters.exposureCompensation = parameters.minExposureCompensation.coerceAtLeast(
					value
				).coerceAtMost(parameters.maxExposureCompensation)
			}
			CameraParameterType.ZOOM -> {
				if (value !is Int) return false
				//如果最大值也为0，说明不支持缩放
				if (parameters.maxZoom == 0 || !parameters.isZoomSupported) return false
				parameters.zoom = value.coerceAtLeast(0).coerceAtMost(parameters.maxZoom)
			}
			CameraParameterType.FLASH -> {
				if (value !is Boolean) return false
				// 判断是否存在当前闪光灯模式
				val flashModes = parameters.supportedFlashModes ?: return false
				parameters.flashMode = if (value) {
					if (flashModes.contains(FLASH_MODE_TORCH)) FLASH_MODE_TORCH else FLASH_MODE_ON
				} else FLASH_MODE_OFF
			}
			CameraParameterType.ORIENTATION -> { // 配置预览方向
				if (value !is Int) return false //  仅支持0,90,180,270
				mCameraAtomic.get()?.setDisplayOrientation(value)
				this.mDisplayOrientation = value
			}
			CameraParameterType.JPEG_MIRROR -> {
				if (value !is Boolean) return false
				this.mIsJpegMirror = value
			}
			CameraParameterType.FOCUS -> {
				if (value !is String) return false
				parameters.focusMode = value
			}
			
			CameraParameterType.PREVIEW_SIZE -> {
				if (value !is CameraSize) return false
				parameters.setPreviewSize(value.width, value.height)
			}
			CameraParameterType.PREVIEW_FORMAT -> {
				if (value !is CameraPreviewFormat) return false
				parameters.previewFormat = value.toFormat()
			}
			CameraParameterType.SCENE_MODE -> {
				if (value !is String) return false
				parameters.sceneMode = value
			}
			CameraParameterType.WHITE_BALANCE -> {
				if (value !is String) return false
				parameters.whiteBalance = value
			}
			CameraParameterType.COLOR_EFFECTS -> {
				if (value !is String) return false
				parameters.colorEffect = value
			}
			else -> return false
		}
		return runCatching { mCameraAtomic.get()?.parameters = parameters }.onFailure {
			it.printStackTrace()
		}.isSuccess
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <V> getParameter(key: Parameters.Key<V>): V? {
		val parameters = mCameraAtomic.get()?.parameters ?: return null
		return when (key.type) {
			CameraParameterType.AUTO_EXPOSURE -> (parameters.isAutoExposureLockSupported && !parameters.autoExposureLock)
			CameraParameterType.EXPOSURE -> parameters.exposureCompensation
			CameraParameterType.ZOOM -> parameters.zoom
			CameraParameterType.FLASH -> parameters.flashMode != FLASH_MODE_OFF
			CameraParameterType.FOCUS -> parameters.focusMode
			CameraParameterType.ORIENTATION -> this.mDisplayOrientation
			CameraParameterType.PREVIEW_SIZE -> parameters.previewSize.let {
				CameraSize(it.width, it.height)
			}
			CameraParameterType.PREVIEW_FORMAT -> parameters.previewFormat.toPreviewFormat()
			CameraParameterType.SCENE_MODE -> parameters.sceneMode
			CameraParameterType.WHITE_BALANCE -> parameters.whiteBalance
			CameraParameterType.COLOR_EFFECTS -> parameters.colorEffect
			else -> null
		} as? V
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <T> getSupportParameters(supportKey: SupportParameters.Key<T>): T? {
		val camera = mCameraAtomic.get() ?: return null
		val parameters = camera.parameters
		return when (supportKey.type) {
			CameraParameterType.AUTO_EXPOSURE -> parameters.isAutoExposureLockSupported
			CameraParameterType.EXPOSURE -> if (parameters.maxExposureCompensation == 0 && parameters.minExposureCompensation == parameters.maxExposureCompensation) null else {
				IntRange(parameters.minExposureCompensation, parameters.maxExposureCompensation)
			}
			CameraParameterType.ZOOM -> if (parameters.maxZoom == 0) null else IntRange(
				0, parameters.maxZoom
			)
			CameraParameterType.FLASH -> parameters.supportedFlashModes.let {
				if (it.isNullOrEmpty()) false else it.any { mode ->
					mode == FLASH_MODE_TORCH || mode == FLASH_MODE_ON
				}
			}
			CameraParameterType.FOCUS -> parameters.supportedFocusModes?.toTypedArray()
			CameraParameterType.PREVIEW_SIZE -> parameters.supportedPreviewSizes.filterMap { size ->
				CameraSize(size.width, size.height)
			}
			CameraParameterType.PREVIEW_FORMAT -> parameters.supportedPreviewFormats.filter {
				it.isUserFormat()
			}.filterMap { it.toPreviewFormat() }
			CameraParameterType.COLOR_EFFECTS -> parameters.supportedColorEffects?.toTypedArray()
			CameraParameterType.SCENE_MODE -> parameters.supportedSceneModes?.toTypedArray()
			CameraParameterType.WHITE_BALANCE -> parameters.supportedWhiteBalance?.toTypedArray()
			CameraParameterType.FACE_DETECT -> true
			else -> null
		} as? T
	}
	
	override fun closeCamera(): Boolean {
		val mCamera = mCameraAtomic.get()
		Log.d("camera1Tag", "mCamera:$mCamera")
		runCatching { mCamera?.release() }
		mCameraAtomic.set(null)
		return true
	}
	
	override fun startFaceDetection(): Boolean {
		val camera = mCameraAtomic.get() ?: return false
		val result = runCatching { camera.startFaceDetection() }.isSuccess
		if (result) {
			//设置人间检测回调
			camera.setFaceDetectionListener { faces, _ ->
				if (!faces.isNullOrEmpty()) {
					mFaceDetectListener?.invoke(Array(faces.size) {
						val face = faces[it]
						face.rect
						face.mouth
						face.leftEye
						FaceDetectMode(
							face.rect, face.score, face.leftEye, face.rightEye, face.mouth
						)
					})
				}
			}
		}
		return result
	}
	
	override fun stopFaceDetection(): Boolean {
		val camera = mCameraAtomic.get() ?: return false
		return runCatching { camera.stopFaceDetection() }.isSuccess.also {
			camera.setFaceDetectionListener(null)
		}
	}
	
	override suspend fun takePicture(cropWidth:Int,cropHeight: Int): Bitmap? {
		val camera = mCameraAtomic.get() ?: return null
		val data = withTimeoutOrNull(3000) {
			suspendCancellableCoroutine<ByteArray?> { continuation ->
				continuation.invokeOnCancellation {
					// 取消
				}
				camera.takePicture(null, null) { data, _ ->
					continuation.resume(data)
				}
			}
		} ?: return null
		return withContext(Dispatchers.IO) {
			// jpeg转为bitmap
			return@withContext data.toBitmap(this@Camera1Device.mDisplayOrientation,mIsJpegMirror,cropWidth,cropHeight)
		}
	}
	
	/**
	 * 基础参数配置类
	 */
	private fun initNormalParameters(camera: Camera) {
		val parameters = camera.parameters ?: return
		//获取支持的分辨率集合
		val supportedPreviewSize = parameters.supportedPreviewSizes
		if (!supportedPreviewSize.isNullOrEmpty()) {
			val previewDetail = supportedPreviewSize.checkSize(640, 480)
			parameters.setPreviewSize(previewDetail.width, previewDetail.height)
		}
		//配置预览格式
		val previewFormat = parameters.loadPreviewFormat()
		if (previewFormat != null) parameters.previewFormat = previewFormat
		//设置支持视频防抖
		if ("true" == parameters.get("video-stabilization-supported")) {
			parameters.set("video-stabilization", "true")
		}
		val banding = parameters.loadBanding()
		if (!banding.isNullOrEmpty()) parameters.antibanding = banding
//		//白平衡
		//预览帧率
		val differenceData = parameters.loadDifferenceData()
		if (differenceData != null) {
			parameters.setPreviewFpsRange(differenceData[0], differenceData[1])
		}
		// 设置接收帧率
		val minFrameRate = parameters.loadMaxFrameRate()
		if (minFrameRate != null) {
			parameters.previewFrameRate = minFrameRate
		}
		runCatching { camera.enableShutterSound(false) }
		runCatching { camera.parameters = parameters }.onFailure { it.printStackTrace() }
	}
}