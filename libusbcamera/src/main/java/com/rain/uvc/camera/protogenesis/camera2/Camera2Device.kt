package com.rain.uvc.camera.protogenesis.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
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
import android.os.Process
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.camera.protogenesis.camera1.isUserFormat
import com.rain.uvc.camera.protogenesis.camera1.toFormat
import com.rain.uvc.camera.protogenesis.camera1.toPreviewFormat
import com.rain.uvc.listener.IButtonListener
import com.rain.uvc.mode.CameraSize
import com.rain.uvc.mode.CameraSupportSize
import com.rain.uvc.mode.FaceDetectMode
import com.rain.uvc.parameters.CameraParameterType
import com.rain.uvc.parameters.CameraPreviewFormat
import com.rain.uvc.parameters.Parameters
import com.rain.uvc.parameters.SupportParameters
import com.rain.uvc.utils.cropBitmap
import com.rain.uvc.utils.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.set
import kotlin.coroutines.resume

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des camera2操作实例
 */
class Camera2Device(context: Context) : ICameraDevice() {
	//摄像头驱动
	private val mCameraDeviceAtomic = AtomicReference<CameraDevice?>()
	
	// 配置缓存参数
	private val parameterCache = ConcurrentHashMap<String, Any>()
	
	// context对象实例
	private val mContext = AtomicReference<Context>()
	
	// 设置当前的打开状态,如果为false时，则不执行打开直接关闭
	private val isOpenCancelled = AtomicBoolean(false)
	
	// 是否正在打开
	private val isOpening = AtomicBoolean(false)
	
	/**
	 * 拍照的回调
	 */
	private val pictureContinuation = AtomicReference<((ByteArray, Int, Int, Int) -> Unit)?>(null)
	
	//预览操作实例
	private var mCameraSession: CameraCaptureSession? = null
	
	//预览流回调的实例
	private var mImageReader: ImageReader? = null
	
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
	
	// 当前传感器方向
	private var mSensorOrientation: Int = 0
	
	// 缓存控件
	private val mTextureCache = AtomicReference<TextureView>()
	
	init {
		mContext.set(context.applicationContext)
	}
	override fun getDeviceId(): String? {
		return mCameraDeviceAtomic.get()?.id
	}
	/**
	 * 打开结果回调
	 */
	private val openStateCallBack = object : CameraDevice.StateCallback() {
		override fun onOpened(camera: CameraDevice) {
			Log.d("Camera2Device", "打开成功 - isOpenCancelled :${isOpenCancelled.get()}")
			if (isOpenCancelled.get()) {
				camera.close()
				releaseHandler()
				return
			}
			
			if (!mCameraDeviceAtomic.compareAndSet(null, camera)) {
				camera.close()
				releaseHandler()
				return
			}
			isOpening.set(false)
			initImageReader(640, 480)
			resultOpen(true, "打开成功")
		}
		
		override fun onDisconnected(camera: CameraDevice) {
			Log.d("Camera2Device", "断开连接")
			closeCamera()
			iDetachedCloseListener?.invoke()
		}
		
		override fun onError(camera: CameraDevice, error: Int) {
			Log.d("Camera2Device", "打开失败,失败code:$error")
			isOpening.set(false)
			closeCamera()
			resultOpen(false, "获取摄像头打开失败,失败code:$error")
		}
	}
	
	//回调
	private val mRepeatingCallback = object : CameraCaptureSession.CaptureCallback() {
		override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
			super.onCaptureCompleted(session, request, result)
			// 获取人脸检测数据
			val faces = result.get(CaptureResult.STATISTICS_FACES)
			if (!faces.isNullOrEmpty()) {
				mFaceDetectListener?.invoke(Array(faces.size) {
					val face = faces[it]
					FaceDetectMode(
						face.bounds, face.score, face.leftEyePosition, face.rightEyePosition, face.mouthPosition
					)
				})
			}
		}
	}
	
	/**
	 * 取消相机打开操作（核心方法）
	 */
	fun cancelOpen() {
		Log.d("Camera2Device", "取消打开~ :${isOpening.get()}")
		if (!isOpening.get()) return
		isOpenCancelled.set(true)
		val device = mCameraDeviceAtomic.get()
		if (device != null) {
			mCameraHandler?.post {
				device.close()
			}
		} else {
			releaseHandler()
		}
		isOpening.set(false)
	}
	
	private fun releaseHandler() {
		mContext.set(null)
		mHandlerThread?.quitSafely()
		mHandlerThread = null
		mCameraHandler = null
	}
	
	/**
	 * 打开结果回调
	 */
	private fun resultOpen(isSuccess: Boolean, message: String) {
		iOpenListener?.invoke(isSuccess, message)
		iOpenListener = null
	}
	
	@SuppressLint("MissingPermission")
	fun open(context: Context, cameraId: String, acRotation: Int, listener: ((Boolean, String?) -> Unit)) {
		if (!isOpening.compareAndSet(false, true)) {
			listener(false, "camera is opening")
			return
		}
		this.iOpenListener = listener
		isOpenCancelled.set(false)
		val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
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
		// 需要获取旧的camera对象，并置为null，保证其他调用无法使用
		val oldDevice = mCameraDeviceAtomic.getAndSet(null).also {
			Log.d("Camera2Device", "获取摄像头设备对象:$it")
		} ?: return true
		val handler = mCameraHandler ?: run {
			Log.d("Camera2Device", "未初始化cameraHandler")
			oldDevice.close()
			return true
		}
		handler.post {
			try {
				stopPreviewInternal() // 线程内停止预览
				runCatching { mCameraSession?.close() }
				mCameraSession = null
				runCatching {
					previewLock.lock()
					try {
						val reader = mImageReader
						if (reader != null) {
							while (true) {
								val image = try {
									reader.acquireLatestImage()
								} catch (_: Exception) {
									null
								} ?: break
								image.close()
							}
							reader.close()
						}
					} finally {
						previewLock.unlock()
					}
					
					mImageReader = null
				}
				runCatching { mSurface?.release() }
				mSurface = null
				runCatching { oldDevice.close() }
			} catch (e: Exception) {
				e.printStackTrace()
			} finally {
				Log.d("Camera2Device", "执行关闭完毕，开始执行退出~~~~~~")
				releaseHandler()
				Log.d("Camera2Device", "执行退出完成")
			}
		}
		return true
	}
	
	private fun stopPreviewInternal() {
		if (!isPreviewIng.get()) return
		isPreviewIng.set(false)
		mImageReader?.setOnImageAvailableListener(null, null)
		runCatching {
			if (previewLock.tryLock(500, TimeUnit.MILLISECONDS)) {
				try {
					while (true) {
						val image = mImageReader?.acquireNextImage() ?: break
						image.close()
					}
				} finally {
					previewLock.unlock()
				}
			}
		}
		runCatching {
			mCameraSession?.stopRepeating()
		}
		isOpenPreviewIng.set(false)
	}
	
	override fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean {
		if (mCameraDeviceAtomic.get() == null) return false
		mPreviewFormat = format
		initImageReader(width, height)
		return true
	}
	
	override fun startPreview() {
		val cameraDevice = mCameraDeviceAtomic.get()
		if (cameraDevice == null || isPreviewIng.get() || isOpenPreviewIng.get()) return
		isOpenPreviewIng.compareAndSet(false, true)
		if (mCameraSession == null) {
			try {
				cameraDevice.createCaptureSession(ArrayList<Surface>().also {
					this@Camera2Device.mSurface?.let { surface -> it.add(surface) }
					mImageReader?.surface?.let { surface -> it.add(surface) }
				}, object : CameraCaptureSession.StateCallback() {
					override fun onConfigured(session: CameraCaptureSession) {
						mCameraSession = session
						startPreviewReader(createCaptureRequest(cameraDevice))
					}
					
					override fun onConfigureFailed(session: CameraCaptureSession) {
						isOpenPreviewIng.set(false)
						session.close()
					}
				}, mCameraHandler)
			} catch (e: CameraAccessException) {
				e.printStackTrace()
				isOpenPreviewIng.set(false)
				
			}
		} else startPreviewReader(createCaptureRequest(cameraDevice))
	}
	
	private fun startPreviewReader(captureRequest: CaptureRequest) {
		mImageReader?.setOnImageAvailableListener({
			readReaderBytes(it)
		}, mCameraHandler)
		try {
			mCameraSession?.setRepeatingRequest(captureRequest, mRepeatingCallback, mCameraHandler)
			isPreviewIng.set(true)
		} catch (e: CameraAccessException) {
			e.printStackTrace()
			isPreviewIng.set(false)
			mImageReader?.setOnImageAvailableListener(null, null)
		}
		isOpenPreviewIng.set(false)
	}
	
	override fun stopPreview() {
		isPreviewIng.set(false)
		mImageReader?.setOnImageAvailableListener(null, null)
		runCatching {
			while (true) {
				val image = mImageReader?.acquireNextImage() ?: break
				image.close()
			}
		}
		runCatching {
			mCameraSession?.stopRepeating()
		}
		isOpenPreviewIng.set(false)
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
	
	override fun setButtonListener(listener: IButtonListener?) {
	}
	
	private fun setDisplaySurfaceData(surface: Surface): Boolean {
		mSurface?.also { it.release() }
		this.mSurface = surface
		return true
	}
	
	override fun <V> setParameter(key: Parameters.Key<V>, value: V): Boolean {
		val context = mContext.get() ?: return false
		when (key.type) {
			CameraParameterType.AUTO_EXPOSURE -> {
				if (value !is Boolean) return false
				val aeMode = if (value) mCameraDeviceAtomic.get()?.loadAEEnable(context) else CaptureRequest.CONTROL_AE_MODE_OFF
				if (aeMode == null) return false
				parameterCache[key.type] = aeMode
			}
			CameraParameterType.EXPOSURE -> {
				if (value !is Int) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.PREVIEW_ORIENTATION -> {
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
			CameraParameterType.FLASH -> {
				if (value !is Boolean) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.FOCUS -> {
				if (value !is String) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.SCENE_MODE -> {
				if (value !is String) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.IRIS -> {
				if (value !is Float) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.WHITE_BALANCE -> {
				if (value !is String) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.ISO -> {
				if (value !is String) return false
				parameterCache[key.type] = value
			}
			CameraParameterType.PIC_ORIENTATION -> {
				if (value !is Int) return false
				this.mPicOrientation = value
			}
			else -> return false
		}
		return true
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <V> getParameter(key: Parameters.Key<V>): V? {
		return when (key.type) {
			CameraParameterType.AUTO_EXPOSURE -> parameterCache[key.type].let {
				it != null && it != CaptureRequest.CONTROL_AE_MODE_OFF
			}
			CameraParameterType.EXPOSURE -> parameterCache[key.type]
			CameraParameterType.FLASH -> parameterCache[key.type]
			CameraParameterType.FOCUS -> parameterCache[key.type]?.toString()
			CameraParameterType.PREVIEW_ORIENTATION -> this.mDisplayOrientation // 屏幕方向，即拍照方向
			CameraParameterType.PIC_ORIENTATION -> this.mPicOrientation
			CameraParameterType.JPEG_MIRROR -> this.mIsJpegMirror
			CameraParameterType.PREVIEW_SIZE -> mImageReader?.let {
				CameraSize(it.width, it.height)
			}
			CameraParameterType.IRIS -> parameterCache[key.type]
			CameraParameterType.SCENE_MODE -> parameterCache[key.type]?.toString()
			CameraParameterType.WHITE_BALANCE -> parameterCache[key.type]?.toString()
			CameraParameterType.ISO -> parameterCache[key.type]?.toString()
			else -> null
		} as? V
	}
	
	@Suppress("UNCHECKED_CAST")
	override fun <T> getSupportParameters(supportKey: SupportParameters.Key<T>): T? {
		val context = mContext.get() ?: return null
		val cameraId = mCameraDeviceAtomic.get()?.id
		if (cameraId.isNullOrEmpty()) return null
		val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
		val cameraCharacteristics = manager.getCameraCharacteristics(cameraId) //获取对应的参数信息
		return when (supportKey.type) {
			CameraParameterType.AUTO_EXPOSURE -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES).let {
				if (it == null || it.isEmpty()) return@let false
				it.size > 1
			}
			CameraParameterType.EXPOSURE -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.let {
				IntRange(it.lower, it.upper)
			}
			CameraParameterType.PREVIEW_SIZE -> getSupportPreviewSizes(cameraCharacteristics)
			CameraParameterType.FLASH -> cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
			CameraParameterType.FOCUS -> cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES).filterMap {
				it.toString()
			}
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
			CameraParameterType.SENSOR_ORIENTATION -> cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
			else -> null
		} as? T
	}
	
	override fun startFaceDetection(): Boolean {
		val context = mContext.get() ?: return false
		val cameraId = mCameraDeviceAtomic.get()?.id
		if (cameraId.isNullOrEmpty()) return false
		val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
		val cameraCharacteristics = manager.getCameraCharacteristics(cameraId) //获取对应的参数信息
		val supportFaceDetect = cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
		if (supportFaceDetect == null || supportFaceDetect.isEmpty()) return false
		val faceTypeId = if (supportFaceDetect.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)) {
			CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
		} else if (supportFaceDetect.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL)) {
			CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
		} else return false
		parameterCache["android.statistics.faceDetectMode"] = faceTypeId
		return true
	}
	
	override fun stopFaceDetection(): Boolean {
		parameterCache["android.statistics.faceDetectMode"] = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
		return true
	}
	
	/**
	 * 初始化imageReader捕获
	 */
	private fun initImageReader(width: Int, height: Int) {
		runCatching { previewLock.lock() }
		runCatching { // 关闭旧的session
			mCameraSession?.close()
			mCameraSession = null
		}
		//关闭旧的imageReader
		mImageReader?.also {
			it.close()
		}
		mImageReader = ImageReader.newInstance(width, height, mPreviewFormat.toFormat(), 2)
		runCatching { previewLock.unlock() }
	}
	
	/**
	 * 初始化摄像头handler
	 */
	private fun initHandler(cameraId: String) {
		if (mCameraHandler != null) return
		
		val threadName = "camera2Factory_${cameraId}_${System.currentTimeMillis()}"
		val handlerThread = HandlerThread(threadName).apply {
			start()
			// 可选：设置线程优先级（相机线程建议高优先级）
			Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
		}
		mHandlerThread = handlerThread
		mCameraHandler = Handler(handlerThread.looper)
	}
	
	/**
	 * 读取当前imageReader返回的帧数据
	 */
	private fun readReaderBytes(imageReader: ImageReader?) {
		//监听回调
		val acquireNextImage = runCatching { imageReader?.acquireLatestImage() }.getOrNull() ?: return
		//获取每一帧数据
		if (mImageReader == null || !isPreviewIng.get() || (pictureContinuation.get() == null && iPreviewListener == null)) {
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
		val format: Int
		val imageBytes: ByteArray?
		try {
			width = acquireNextImage.width
			height = acquireNextImage.height
			format = acquireNextImage.format
			imageBytes = Camera2CommonExt.imageDataToBytes(format, acquireNextImage.planes, width, height)
		} catch (e: Exception) {
			e.printStackTrace()
			return
		} finally {
			acquireNextImage.close()
			runCatching { previewLock.unlock() }
		}
		if (imageBytes == null || !isPreviewIng.get()) return
		// 拍照请求触发中
		pictureContinuation.getAndSet(null)?.let {
			it.invoke(imageBytes, format, width, height)
			return
		}
		if (iPreviewListener == null) return
		// 正常预览回调
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
		//获取当前是前置还是后置
		this.mIsJpegMirror = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
		// 前置默认需要镜像
		mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
		mDisplayOrientation = loadOrientation(mSensorOrientation, this.mIsJpegMirror, acRotation)
	}
	
	override suspend fun takePicture(cropWidth: Int, cropHeight: Int): Bitmap? {
		return withTimeoutOrNull(2000) {
			suspendCancellableCoroutine { continuation ->
				// 如果已经有拍照请求，直接失败（避免并发）
				val callback: (ByteArray, Int, Int, Int) -> Unit = { bytes, format, width, height ->
					val bmp = runCatching {
						bytes.toBitmap(format, width, height)?.cropBitmap(// 旋转方向需要根据角度来计算,否则会出现方向不对,或者拉伸的问题
							mPicOrientation % 360, mIsJpegMirror, cropWidth, cropHeight
						)
					}.onFailure { it.printStackTrace() }.getOrNull()
					continuation.resume(bmp)
				}
				
				if (!pictureContinuation.compareAndSet(null, callback)) {
					continuation.resume(null)
					return@suspendCancellableCoroutine
				}
				continuation.invokeOnCancellation {
					pictureContinuation.compareAndSet(callback, null)
				}
			}
		}
	}
	
	override suspend fun takeCapturePicture(width: Int, height: Int, cropWidth: Int, cropHeight: Int): Bitmap? {
		val cameraDevice = mCameraDeviceAtomic.get() ?: return null
		stopPreview()
		mCameraSession?.close()
		mCameraSession = null
		val bytes = withTimeoutOrNull(3000) {
			return@withTimeoutOrNull suspendCancellableCoroutine<ByteArray?> { continuation ->
				val useWidth: Int
				val useHeight: Int
				if (width <= 0 || height <= 0) {
					useWidth = mImageReader?.width ?: 640
					useHeight = mImageReader?.height ?: 480
				} else {
					useWidth = width
					useHeight = height
				}
				val mCaptureReader = ImageReader.newInstance(useWidth, useHeight, ImageFormat.JPEG, 1)
				var mCaptureSession: CameraCaptureSession? = null
				continuation.invokeOnCancellation {
					mCaptureReader.close()
					mCaptureSession?.close()
				}
				mCaptureReader.setOnImageAvailableListener({
					val image = runCatching { it.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
					image.use { image ->
						val buffer = image.planes[0].buffer
						val data = ByteArray(buffer.remaining())
						buffer.get(data)
						if (continuation.isActive) continuation.resume(data)
					}
				}, mCameraHandler)
				try {
					cameraDevice.createCaptureSession(
						listOf(mCaptureReader.surface), object : CameraCaptureSession.StateCallback() {
							override fun onConfigured(session: CameraCaptureSession) {
								//这里处理拍照
								mCaptureSession = session
								session.capture(
									cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
										addTarget(mCaptureReader.surface)
										set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
									}.build(), object : CameraCaptureSession.CaptureCallback() {
										override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
											super.onCaptureFailed(session, request, failure)
											//拍照失败
											Log.d("camera2Tag", "拍照失败")
											session.close()
											mCaptureReader.close()
											mCaptureSession = null
											if (continuation.isActive) continuation.resume(null)
										}
										
										override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
											super.onCaptureCompleted(session, request, result)
											//拍照完成
											Log.d("camera2Tag", "拍照完成")
											mCaptureReader.close()
											session.close()
											mCaptureSession = null
										}
									}, mCameraHandler
								)
							}
							
							override fun onConfigureFailed(session: CameraCaptureSession) {
								session.close()
								mCaptureReader.close()
								mCaptureSession = null
								if (continuation.isActive) continuation.resume(null)
							}
						}, mCameraHandler
					)
				} catch (e: Exception) {
					e.printStackTrace()
					mCaptureReader.close()
					if (continuation.isActive) continuation.resume(null)
				}
			}
		} ?: return null
		return withContext(Dispatchers.IO) {
			return@withContext bytes.toBitmap(ImageFormat.JPEG, width, height)?.cropBitmap(
				mPicOrientation, mIsJpegMirror, cropWidth, cropHeight
			)
		}
	}
	
	/**
	 * 获取支持的分辨率列表
	 */
	private fun getSupportPreviewSizes(characteristics: CameraCharacteristics): Array<CameraSupportSize>? {
		//获取当前输出帧的信息
		val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
		val outputFormats = streamConfigurationMap.outputFormats?.filter { it.isUserFormat() }
		if (outputFormats == null || outputFormats.isEmpty()) return null
		val outputSupportSizes = mutableListOf<CameraSupportSize>()
		outputFormats.forEach {
			val outputSizes = streamConfigurationMap.getOutputSizes(it)
			if (outputSizes.isNullOrEmpty()) return@forEach
			outputSupportSizes.add(
				CameraSupportSize(
					it.toPreviewFormat(), outputSizes.map { size -> CameraSize(size.width, size.height) })
			)
		}
		return outputSupportSizes.toTypedArray()
	}
	
	/**
	 * 创建预览的captureBuilder
	 */
	private fun createCaptureRequest(cameraDevice: CameraDevice): CaptureRequest {
		val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
		//设置为自动模式，单个控制生效
		captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
		captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false)
		//自动对焦
		captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
		parameterCache.forEach {
			val value = it.value
			when (it.key) {
				CameraParameterType.AUTO_EXPOSURE -> {
					captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, (value as Int))
				}
				CameraParameterType.EXPOSURE -> {
					captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value as Int)
				}
				CameraParameterType.FLASH -> {
					captureRequestBuilder.set(CaptureRequest.FLASH_MODE, if (value == true) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
				}
				CameraParameterType.FOCUS -> {
					captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, (value as String).toInt())
				}
				CameraParameterType.SCENE_MODE -> {
					val scene = (value as? String)?.toIntOrNull() ?: return@forEach
					captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, scene)
					if (scene == CaptureRequest.CONTROL_SCENE_MODE_DISABLED) {
						//设置为使用场景模式
						captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
					} else {
						//设置为使用场景模式
						captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
					}
				}
				CameraParameterType.IRIS -> {
					captureRequestBuilder.set(CaptureRequest.LENS_APERTURE, value as Float)
				}
				CameraParameterType.WHITE_BALANCE -> {
					val awbMode = (value as String).toIntOrNull() ?: return@forEach
					captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode)
				}
				CameraParameterType.ISO -> {
					if (value !is String) return@forEach
					if (value == "auto") {
						captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
						captureRequestBuilder.set(
							CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
						)
					} else {
						val isoValue = value.toIntOrNull() ?: return@forEach
						captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
					}
				}
				"android.statistics.faceDetectMode" -> {
					captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, value as Int)
				}
				else -> return@forEach
			}
		}
		mSurface?.let {
			captureRequestBuilder.addTarget(it)
		}
		mImageReader?.surface?.let {
			captureRequestBuilder.addTarget(it)
		}
		return captureRequestBuilder.build()
	}
}