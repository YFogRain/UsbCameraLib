package com.once.camera.factory.protogenesis.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.once.camera.factory.ICameraDevice
import com.once.camera.factory.protogenesis.camera1.filterMap
import com.once.camera.factory.protogenesis.camera1.isUserFormat
import com.once.camera.factory.protogenesis.camera1.toFormat
import com.once.camera.factory.protogenesis.camera1.toPreviewFormat
import com.once.camera.mode.CameraSize
import com.once.camera.mode.FaceDetectMode
import com.once.camera.parameters.CameraPreviewFormat
import com.once.camera.parameters.Parameters
import com.once.camera.parameters.SupportParameters
import com.once.camera.provider.OverallContext
import com.once.camera.type.CameraParameterType
import com.once.camera.utils.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.resume

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des camera2操作实例
 */
class Camera2Device : ICameraDevice() {
	//摄像头驱动
	private val mCameraDeviceAtomic = AtomicReference<CameraDevice?>()
	
	//预览操作实例
	private var mCameraSession: CameraCaptureSession? = null
	
	//预览流回调的实例
	private var mImageReader: ImageReader? = null
	private var captureRequestBuilder: CaptureRequest.Builder? = null
	
	//是否正在预览中
	private val isOpenPreviewIng = AtomicBoolean(false)
	
	//预览格式
	private var mPreviewFormat: CameraPreviewFormat = CameraPreviewFormat.NV21
	
	private var mCameraHandler: Handler? = null
	private var mHandlerThread: HandlerThread? = null
	private val previewLock = ReentrantLock()
	private var mSurface: Surface? = null
	
	//打开回调
	private var iOpenListener: ((Boolean, String?) -> Unit)? = null
	
	// 缓存控件
	private val mTextureCache = AtomicReference<TextureView>()
	
	/**
	 * 打开结果回调
	 */
	private val openStateCallBack = object : CameraDevice.StateCallback() {
		override fun onOpened(camera: CameraDevice) {
			initPreviewCapture(camera)
			this@Camera2Device.mCameraDeviceAtomic.set(camera)
			initImageReader(640, 480)
			resultOpen(true, "打开成功")
		}
		
		override fun onDisconnected(camera: CameraDevice) {
			iDetachedCloseListener?.invoke()
		}
		
		override fun onError(camera: CameraDevice, error: Int) {
			this@Camera2Device.mCameraDeviceAtomic.set(null)
			resultOpen(false, "获取摄像头打开失败,失败code:$error")
		}
	}
	
	//回调
	private val mRepeatingCallback = object : CameraCaptureSession.CaptureCallback() {
		override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
			super.onCaptureCompleted(session, request, result)
			//获取人脸检测数据
			val faces = result.get(CaptureResult.STATISTICS_FACES)
			if (!faces.isNullOrEmpty()) {
				mFaceDetectListener?.invoke(Array(faces.size) {
					val face = faces[it]
					FaceDetectMode(
						face.bounds,
						face.score,
						face.leftEyePosition,
						face.rightEyePosition,
						face.mouthPosition
					)
				})
			}
		}
	}
	
	/**
	 * 打开结果回调
	 */
	private fun resultOpen(isSuccess: Boolean, message: String) {
		iOpenListener?.invoke(isSuccess, message)
		iOpenListener = null
	}
	
	@SuppressLint("MissingPermission")
	fun open(cameraId: String, acRotation: Int, listener: ((Boolean, String?) -> Unit)) {
		this.iOpenListener = listener
		val manager = OverallContext.baseContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
		val cameraList = runCatching {
			manager?.cameraIdList
		}.getOrNull()
		if (cameraList.isNullOrEmpty() || cameraId.isEmpty() || cameraList.none { it == cameraId }) {
			resultOpen(false, "没有匹配到对应的摄像头id")
			return
		}
		//初始化可使用的handler
		initHandler(cameraId)
		if (manager == null) {
			resultOpen(false, "获取camera2管理类失败")
			return
		}
		//执行打开摄像头
		initCharacteristics(manager, cameraId, acRotation)
		try {
			manager.openCamera(cameraId, openStateCallBack, mCameraHandler)
		} catch (e: Exception) {
			e.printStackTrace()
			resultOpen(false, e.message ?: "摄像头权限异常")
		}
	}
	
	override fun closeCamera(): Boolean {
		mCameraSession?.close()
		mCameraSession = null
		
		runCatching { previewLock.lock() }
		mImageReader?.close()
		mImageReader = null
		
		runCatching { previewLock.unlock() }
		// 清理控件
		mTextureCache.set(null)
		
		mCameraDeviceAtomic.get()?.close()
		mCameraDeviceAtomic.set(null)
		captureRequestBuilder = null
		mSurface = null
		stopHandlerThread()
		return true
	}
	
	override fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean {
		if (mCameraDeviceAtomic.get() == null) return false
		mPreviewFormat = format
		initImageReader(width, height)
		return true
	}
	
	override suspend fun startPreview(): Boolean {
		val cameraDevice = mCameraDeviceAtomic.get()
		if (cameraDevice == null || isPreviewIng.get() || isOpenPreviewIng.get()) return false
		val captureBuilder = captureRequestBuilder ?: return false
		
		mTextureCache.get()?.also { view -> // 设置
			mImageReader?.also { reader ->// 设置控件的大小
				mTextureCache.get()?.surfaceTexture?.setDefaultBufferSize(
					reader.width,
					reader.height
				)
			}// 旋转
			setSurfaceTransformWithScale(view)
		}
		isOpenPreviewIng.set(true)
		if (mCameraSession == null) {
			val resultSession = Camera2CommonExt.createSession(
				cameraDevice, this@Camera2Device.mSurface, mImageReader, mCameraHandler
			)
			val session = resultSession.getOrNull()
			if (resultSession.isFailure || session == null) {
				return false
			}
			mCameraSession = session
		}
		startPreviewReader(captureBuilder.build())
		return true
	}
	
	private fun startPreviewReader(captureRequest: CaptureRequest) {
		mImageReader?.setOnImageAvailableListener({
			readReaderBytes(it)
		}, mCameraHandler)
		mCameraSession?.setRepeatingRequest(captureRequest, mRepeatingCallback, mCameraHandler)
		isOpenPreviewIng.set(false)
		isPreviewIng.set(true)
	}
	
	override fun stopPreview(): Boolean {
		isPreviewIng.set(false)
		mImageReader?.setOnImageAvailableListener(null, null)
		runCatching { mCameraSession?.stopRepeating() }
		isOpenPreviewIng.set(false)
		return true
	}
	
	override fun setDisplaySurface(view: SurfaceView): Boolean {
		mTextureCache.set(null)
		val surface = view.holder?.surface ?: return false
		return setDisplaySurfaceData(surface)
	}
	
	override fun setDisplaySurface(view: TextureView): Boolean {
		mTextureCache.set(view)
		return setDisplaySurfaceData(Surface(view.surfaceTexture))
	}
	
	override fun setDisplaySurface(surface: Surface): Boolean {
		mTextureCache.set(null)
		return setDisplaySurfaceData(surface)
	}
	
	private fun setDisplaySurfaceData(surface: Surface): Boolean {
		captureRequestBuilder ?: return false
		mSurface?.also { captureRequestBuilder?.removeTarget(it) }
		captureRequestBuilder?.addTarget(surface)
		this.mSurface = surface
		return true
	}
	
	override fun <V> setParameter(key: Parameters.Key<V>, value: V): Boolean {
		when (key.type) {
			CameraParameterType.AUTO_EXPOSURE -> {
				Log.d("Camera2Factory", "曝光模式:${value}")
				if (value !is Boolean) return false
				val aeMode = if (value) mCameraDeviceAtomic.get()?.loadAEEnable() else CaptureRequest.CONTROL_AE_MODE_OFF
				if (aeMode == null) return false
				captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
			}
			CameraParameterType.EXPOSURE -> {
				Log.d("Camera2Factory", "曝光度:${value}")
				if (value !is Int) return false
				captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value)
			}
			CameraParameterType.ORIENTATION -> {
				if (value !is Int) return false
				this.mDisplayOrientation = value
			}
			CameraParameterType.JPEG_MIRROR -> {
				if (value !is Boolean) return false
				this.mIsJpegMirror = value
			}
			CameraParameterType.PREVIEW_SIZE -> {
				if (value !is CameraSize) return false
				initImageReader(value.width, value.height)
			}
			CameraParameterType.PREVIEW_FORMAT -> {
				if (value !is CameraPreviewFormat) return false
				mPreviewFormat = value
				initImageReader(mImageReader?.width ?: 640, mImageReader?.height ?: 480)
			}
			CameraParameterType.FLASH -> {
				if (value !is Boolean) return false
				captureRequestBuilder?.set(
					CaptureRequest.FLASH_MODE,
					if (value) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
				)
			}
			CameraParameterType.FOCUS -> {
				if (value !is String) return false
				captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, value.toInt())
			}
			CameraParameterType.SCENE_MODE -> {
				if (value !is String) return false
				val scene = value.toIntOrNull() ?: return false
				captureRequestBuilder?.set(CaptureRequest.CONTROL_SCENE_MODE, scene)
				if (scene == CaptureRequest.CONTROL_SCENE_MODE_DISABLED) {
					//设置为使用场景模式
					captureRequestBuilder?.set(
						CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO
					)
				} else {
					//设置为使用场景模式
					captureRequestBuilder?.set(
						CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE
					)
				}
			}
			CameraParameterType.IRIS -> {
				if (value !is Float) return false
				captureRequestBuilder?.set(CaptureRequest.LENS_APERTURE, value)
			}
			CameraParameterType.WHITE_BALANCE -> {
				if (value !is String) return false
				val awbMode = value.toIntOrNull() ?: return false
				captureRequestBuilder?.set(CaptureRequest.CONTROL_AWB_MODE, awbMode)
			}
			CameraParameterType.ISO -> {
				if (value !is String) return false
				if (value == "auto") {
					captureRequestBuilder?.set(
						CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO
					)
					captureRequestBuilder?.set(
						CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
					)
				} else {
					val isoValue = value.toIntOrNull() ?: return false
					captureRequestBuilder?.set(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
				}
			}
			else -> return false
		}
		return true
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <V> getParameter(key: Parameters.Key<V>): V? {
		val builder = captureRequestBuilder ?: return null
		return when (key.type) {
			CameraParameterType.AUTO_EXPOSURE -> {
				(builder.get(CaptureRequest.CONTROL_AE_MODE) != CaptureRequest.CONTROL_AE_MODE_OFF)
			}
			CameraParameterType.EXPOSURE -> builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION)
			CameraParameterType.FLASH -> builder.get(CaptureRequest.FLASH_MODE) != CaptureRequest.FLASH_MODE_OFF
			CameraParameterType.FOCUS -> builder.get(CaptureRequest.CONTROL_AF_MODE)?.toString()
			CameraParameterType.ORIENTATION -> this.mDisplayOrientation // 屏幕方向，即拍照方向
			CameraParameterType.JPEG_MIRROR -> this.mIsJpegMirror
			CameraParameterType.PREVIEW_SIZE -> mImageReader?.let {
				CameraSize(it.width, it.height)
			}
			CameraParameterType.PREVIEW_FORMAT -> mImageReader?.imageFormat?.toPreviewFormat()
			CameraParameterType.IRIS -> builder.get(CaptureRequest.LENS_APERTURE)
			CameraParameterType.SCENE_MODE -> builder.get(CaptureRequest.CONTROL_SCENE_MODE)?.toString()
			CameraParameterType.WHITE_BALANCE -> builder.get(CaptureRequest.CONTROL_AWB_MODE)?.toString()
			CameraParameterType.ISO -> builder.get(CaptureRequest.SENSOR_SENSITIVITY)?.toString()
			else -> null
		} as? V
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <T> getSupportParameters(supportKey: SupportParameters.Key<T>): T? {
		val cameraId = mCameraDeviceAtomic.get()?.id
		if (cameraId.isNullOrEmpty()) return null
		val manager = OverallContext.baseContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
		val cameraCharacteristics = manager.getCameraCharacteristics(cameraId) //获取对应的参数信息
		return when (supportKey.type) {
			CameraParameterType.AUTO_EXPOSURE -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES).let {
				if (it == null || it.isEmpty()) return@let false
				it.size > 1
			}
			CameraParameterType.EXPOSURE -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let {
				IntRange(it.lower, it.upper)
			}
			CameraParameterType.PREVIEW_SIZE -> {
				//获取当前输出帧的信息
				val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
				//获取支持的分辨率集合
				streamConfigurationMap?.getOutputSizes(mPreviewFormat.toFormat()).filterMap { size ->
					CameraSize(size.width, size.height)
				}
			}
			CameraParameterType.FLASH -> cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
			CameraParameterType.FOCUS -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES).filterMap {
				it.toString()
			}
			CameraParameterType.PREVIEW_FORMAT -> cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.outputFormats?.filter {
				it.isUserFormat()
			}?.filterMap { it.toPreviewFormat() }
			CameraParameterType.SCENE_MODE -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES).filterMap {
				it.toString()
			}
			CameraParameterType.WHITE_BALANCE -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES).filterMap {
				it.toString()
			}
			CameraParameterType.FACE_DETECT -> cameraCharacteristics.get(
				CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES
			).let {
				if (it == null || it.isEmpty()) return@let false
				it.any { mode -> mode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF }
			}
			else -> null
		} as? T
	}
	
	override fun startFaceDetection(): Boolean {
		val cameraId = mCameraDeviceAtomic.get()?.id
		if (cameraId.isNullOrEmpty()) return false
		val manager = OverallContext.baseContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
		val cameraCharacteristics = manager.getCameraCharacteristics(cameraId) //获取对应的参数信息
		val supportFaceDetect = cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
		if (supportFaceDetect == null || supportFaceDetect.isEmpty()) return false
		val faceTypeId = if (supportFaceDetect.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)) {
			CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
		} else if (supportFaceDetect.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)) {
			CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
		} else return false
		captureRequestBuilder?.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceTypeId)
		return true
	}
	
	override fun stopFaceDetection(): Boolean {
		captureRequestBuilder?.set(
			CaptureRequest.STATISTICS_FACE_DETECT_MODE,
			CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
		)
		return true
	}
	
	/**
	 * 初始化imageReader捕获
	 */
	private fun initImageReader(width: Int, height: Int) {
		//重置session
		runCatching { mCameraSession?.stopRepeating() }
		mCameraSession?.close()
		mCameraSession = null
		
		runCatching { previewLock.lock() }
		
		//关闭旧的imageReader
		mImageReader?.also {
			captureRequestBuilder?.removeTarget(it.surface)
			it.close()
		}
		runCatching { previewLock.unlock() }
		mImageReader = ImageReader.newInstance(width, height, mPreviewFormat.toFormat().also {
			Log.d("camera2Tag", "当前使用的格式为 = $it")
		}, 2).also {
			captureRequestBuilder?.addTarget(it.surface)
		}
	}
	
	/**
	 * 初始化摄像头handler
	 */
	private fun initHandler(cameraId: String) {
		//创建handler线程
		if (mCameraHandler != null) return
		mCameraHandler = Handler(loadHandlerThread(cameraId).looper)
	}
	
	private fun loadHandlerThread(cameraId: String): HandlerThread {
		return mHandlerThread ?: HandlerThread("camera2Factory_${cameraId}").apply {
			mHandlerThread = this
			this.start()
		}
	}
	
	private fun stopHandlerThread() {
		mHandlerThread?.quitSafely()
		try {
			mHandlerThread?.join()
			mCameraHandler?.removeCallbacksAndMessages(null)
		} catch (e: Exception) {
			e.printStackTrace()
		}
		mHandlerThread = null
		mCameraHandler = null
	}
	
	/**
	 * 读取当前imageReader返回的帧数据
	 */
	private fun readReaderBytes(imageReader: ImageReader?) {
		//监听回调
		val acquireNextImage = runCatching { imageReader?.acquireNextImage() }.getOrNull() ?: return
		//获取每一帧数据
		if (mImageReader == null || !isPreviewIng.get() || iPreviewListener == null) {
			acquireNextImage.close()
			return
		}
		runCatching { previewLock.lock() }
		if (!isPreviewIng.get()) {
			acquireNextImage.close()
			runCatching { previewLock.unlock() }
			return
		}
		val width: Int
		val height: Int
		val imageBytes: ByteArray?
		try {
			width = acquireNextImage.width
			height = acquireNextImage.height
			imageBytes = Camera2CommonExt.imageDataToBytes(
				acquireNextImage.format, acquireNextImage.planes, width, height
			)
		} catch (e: Exception) {
			e.printStackTrace()
			return
		} finally {
			acquireNextImage.close()
			runCatching { previewLock.unlock() }
		}
		if (imageBytes == null || !isPreviewIng.get()) return
		pullPreview(imageBytes, width, height)
	}
	
	/**
	 * 获取摄像头的范围参数信息
	 */
	private fun initCharacteristics(manager: CameraManager, cameraId: String, acRotation: Int) {
		//获取摄像头的参数类
		val cameraCharacteristics = manager.getCameraCharacteristics(cameraId)
		//获取当前输出帧的信息
		val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
		//获取当前支持的图片格式
		val outputFormats = streamConfigurationMap?.outputFormats
		if (outputFormats != null && outputFormats.isNotEmpty()) {
			val formatState = Camera2CommonExt.loadFormatState(outputFormats)
			if (formatState != null) mPreviewFormat = formatState.toPreviewFormat()
		}
		//获取支持的分辨率集合
		//获取最大缩放倍数
		val maxZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
		Log.d("Camera2Factory", "支持的最大缩放:$maxZoom")
		//获取未缩放的正常预览画面大小
		val rect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
		Log.d("Camera2Factory", "未缩放的正常预览画面大小:${rect}")
		//获取是否支持自动曝光
		//获取当前是前置还是后置
		this.mIsJpegMirror = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
		Log.d("Camera2Factory", "摄像头是否前置:${this.mIsJpegMirror}")
		// 前置默认需要镜像
		mDisplayOrientation = loadOrientation(
			cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
			this.mIsJpegMirror,
			acRotation
		)
		Log.d("cameraDevice", "旋转方向为 = $mDisplayOrientation")
	}
	
	/**
	 * 初始化预览参数
	 */
	private fun initPreviewCapture(cameraDevice: CameraDevice) {
		//创建预览的数据
		val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
		//设置为自动模式，单个控制生效
		captureRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
		captureRequest.set(CaptureRequest.CONTROL_AE_LOCK, false)
		//自动对焦
		captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
		this@Camera2Device.captureRequestBuilder = captureRequest
	}
	
	override suspend fun takePicture(cropWidth:Int,cropHeight: Int): Bitmap? {
		val cameraDevice = mCameraDeviceAtomic.get() ?: return null
		mCameraSession?.close()
		mCameraSession = null
		val data = withTimeoutOrNull(3000) {
			suspendCancellableCoroutine<ByteArray?> { continuation ->
				val imageReader = ImageReader.newInstance(
					mImageReader?.width ?: 640, mImageReader?.height ?: 480, ImageFormat.JPEG, 1
				)
				var mCaptureSession: CameraCaptureSession? = null
				continuation.invokeOnCancellation {
					imageReader.close()
					mCaptureSession?.close()
				}
				imageReader.setOnImageAvailableListener({
					val image = runCatching { it.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
					try {
						val buffer = image.planes[0].buffer
						val data = ByteArray(buffer.remaining())
						buffer.get(data)
						if (continuation.isActive) continuation.resume(data)
					} finally {
						image.close()
					}
				}, mCameraHandler)
				try {
					cameraDevice.createCaptureSession(
						listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
							override fun onConfigured(session: CameraCaptureSession) {
								//这里处理拍照
								mCaptureSession = session
								session.capture(
									createCaptureRequest(
										cameraDevice, imageReader.surface
									), object : CameraCaptureSession.CaptureCallback() {
										override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
											super.onCaptureFailed(session, request, failure)
											//拍照失败
											Log.d("camera2Tag", "拍照失败")
											session.close()
											imageReader.close()
											mCaptureSession = null
											if (continuation.isActive) continuation.resume(null)
										}
										
										override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
											super.onCaptureCompleted(session, request, result)
											//拍照完成
											Log.d("camera2Tag", "拍照完成")
											imageReader.close()
											session.close()
											mCaptureSession = null
										}
									}, mCameraHandler
								)
							}
							
							override fun onConfigureFailed(session: CameraCaptureSession) {
								session.close()
								imageReader.close()
								mCaptureSession = null
								if (continuation.isActive) continuation.resume(null)
							}
						}, mCameraHandler
					)
				} catch (e: Exception) {
					e.printStackTrace()
					imageReader.close()
					if (continuation.isActive) continuation.resume(null)
				}
			}
		} ?: return null
		return withContext(Dispatchers.IO) {
			return@withContext data.toBitmap(this@Camera2Device.mDisplayOrientation,mIsJpegMirror,cropWidth,cropHeight)
		}
	}
	
	private fun createCaptureRequest(cameraDevice: CameraDevice, surface: Surface): CaptureRequest {
		val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
		captureBuilder.addTarget(surface)
		captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
		// 保持方向正确
		return captureBuilder.build()
	}
	
	/**
	 * TextureView 设置图像变换矩阵，仅camera2适用
	 */
	private fun setSurfaceTransformWithScale(view: TextureView) {
		val reader = mImageReader ?: return
		val matrix = Matrix()
		val width = view.width.toFloat()
		val height = view.height.toFloat()
		val cx = width / 2f
		val cy = height / 2f
		// 2️⃣ 旋转
		if (this.mDisplayOrientation != 0) matrix.postRotate(this.mDisplayOrientation.toFloat(), cx, cy)

		// 4️⃣ 缩放，保证填充 TextureView
		val rotatedWidth = if (this.mDisplayOrientation == 90 || this.mDisplayOrientation == 270) reader.height.toFloat() else reader.width.toFloat()
		val rotatedHeight = if (this.mDisplayOrientation == 90 || this.mDisplayOrientation == 270) reader.width.toFloat() else reader.height.toFloat()

		// 计算缩放比例相对于 Bitmap 尺寸
		val scale = (width / rotatedWidth).coerceAtLeast(height / rotatedHeight)
		val scaleX = scale * rotatedWidth / width
		val scaleY = scale * rotatedHeight / height
		matrix.postScale(scaleX, scaleY, cx, cy)
		view.setTransform(matrix)
	}
}