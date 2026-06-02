package com.rain.camera.uvc.factory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.rain.camera.uvc.bridge.UvcNativeBridge
import com.rain.camera.uvc.listener.IButtonListener
import com.rain.camera.uvc.parameter.UvcCameraParameter
import com.rain.camera.uvc.parameter.UvcPreviewFormat
import com.rain.camera.uvc.parameter.UvcSupportParameter
import com.rain.camera.uvc.recorder.RecorderEngine
import com.rain.camera.uvc.utils.clearImageReaderQueue
import com.rain.camera.uvc.utils.imageDataToBytes
import com.rain.camera.uvc.utils.rgba8888ToNv21
import com.rain.camera.uvc.utils.toBitmap
import com.rain.camera.uvc.utils.toNv21Bytes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * @author yuan
 * @createTime: 2026/5/15
 * @des usb相机的配置
 */
class NativeUsbDevice internal constructor(context: Context, nativeId: Long, val deviceName: String, connection: UsbDeviceConnection?) {
	private companion object { // 支持的surface类型
		const val TARGET_PREVIEW = "preview_surface"
		const val TARGET_CALLBACK = "callback_surface"
		const val TARGET_CAPTURE = "capture_surface"
		const val TARGET_RECORD = "record_surface"
	}
	
	private var iDetachedCloseListener: (() -> Unit)? = null
	
	/**
	 * 当前录制状态
	 */
	private val currentRecordState = AtomicBoolean(false)
	
	/**
	 * 预览监听
	 */
	private var mPreviewListener: ((data: ByteArray, width: Int, height: Int) -> Unit)? = null
	
	//打开结果回调
	private var iUsbDeviceConnect: UsbDeviceConnection? = connection
	
	// native访问的指针对象
	private val mNativeAtomic = AtomicLong(nativeId)
	
	// context对象实例
	private val mContext = AtomicReference<Context>()
	
	//当前是否注册广播成功
	@Volatile
	private var isReceiverSuccess = false
	
	// 当前是否正在预览
	private val mPreviewState = AtomicBoolean(false)
	
	// 预览的surface
	private var ownsSurface: Boolean = false
	private var mSurface: Surface? = null
	
	// 当前使用的分辨率信息
	private var mPreviewWidth: Int = 640
	private var mPreviewHeight: Int = 480
	
	/**
	 * 数据回调的imageReader
	 */
	private var mCaptureImageReader: ImageReader? = null
	private var mImageReader: ImageReader? = null
	private var mImageReaderHandler: Handler? = null
	private var mImageReaderThread: HandlerThread? = null
	
	/**
	 * 录制引擎
	 */
	private val recordEngine = RecorderEngine()
	
	/**
	 * 拍照的回调
	 */
	private val pictureContinuation = AtomicReference<((ByteArray, Int, Int, Int) -> Unit)?>(null)
	
	/**
	 * usb移除监听
	 */
	private val mUsbDetachReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			val action = intent?.action
			if (action.isNullOrBlank() || action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
			val usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
			} else {
				@Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
			} ?: return
			if (usbDevice.deviceName != deviceName) return
			// 这里解除了，需要回调移除
			closeCamera()
			iDetachedCloseListener?.invoke()
			iDetachedCloseListener = null
		}
	}
	
	init {
		mContext.set(context.applicationContext)
		if (connection != null) {
			initReceiver()
		}
		initImageReaderThread()
	}
	
	/**
	 * 关闭
	 */
	fun close() {
		iDetachedCloseListener = null
		closeCamera()
	}
	
	/**
	 * 关闭摄像头
	 */
	private fun closeCamera() {
		unReceiver() // 解绑
		val nativeId = mNativeAtomic.getAndSet(0L)
		if (nativeId != 0L) {
			UvcNativeBridge.nativeClose(nativeId)
		}
		mPreviewState.set(false)
		runCatching { iUsbDeviceConnect?.close() }
		iUsbDeviceConnect = null
		mContext.set(null)
		
		if (this.ownsSurface) {
			runCatching { mSurface?.release() }
		}
		mSurface = null
		ownsSurface = false
		destroyImageReaderThread()
	}
	
	/**
	 * 设置预览分辨率
	 *
	 * @param width  宽
	 * @param height 高
	 * @param format 使用的解码类型，当前只支持yuy2/mjpeg
	 */
	fun setPreviewSize(width: Int, height: Int, format: UvcPreviewFormat): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		this.mPreviewWidth = width
		this.mPreviewHeight = height
		return UvcNativeBridge.nativeSetPreviewSize(nativeId, width, height, format.format)
	}
	
	/**
	 * 获取支持的参数列表
	 */
	fun <T> getSupportParameters(key: UvcSupportParameter.Key<T>): T? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return UvcNativeBridge.getSupportedParameter(nativeId, key)
		
	}
	
	/**
	 * 获取当前的参数
	 */
	fun <T> getParameter(key: UvcCameraParameter.Key<T>): T? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return UvcNativeBridge.getParameter(nativeId, key)
	}
	
	/**
	 * 设置参数
	 */
	fun <T> setParameter(key: UvcCameraParameter.Key<T>, value: T): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return false
		return UvcNativeBridge.setParameter(nativeId, key, value)
	}
	
	/**
	 * 设置按钮的监听
	 */
	fun setButtonListener(listener: IButtonListener?) {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return
		UvcNativeBridge.setButtonListener(nativeId, listener)
	}
	
	/**
	 * 开启预览
	 */
	fun startPreview(): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return false
		if (mPreviewState.get()) return true
		if (mImageReader == null && mCaptureImageReader == null) {
			initAllImageReader()
		}
		if (mPreviewListener != null) {
			mImageReader?.setOnImageAvailableListener({ // 设置预览监听
				readReaderBytes(it)
			}, mImageReaderHandler)
		}
		if (!UvcNativeBridge.nativeStartPreview(nativeId)) {//如果开启失败，则返回false
			return false
		}
		mPreviewState.set(true)
		return true
	}
	
	/**
	 * 停止预览
	 */
	fun stopPreview(): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return false
		// 1. 先断监听
		mCaptureImageReader?.setOnImageAvailableListener(null, null)
		mImageReader?.setOnImageAvailableListener(null, null)
		mPreviewState.set(false)
		val state = UvcNativeBridge.nativeStopPreview(nativeId)
		
		mImageReader.clearImageReaderQueue()
		mCaptureImageReader.clearImageReaderQueue()
		
		return state
	}
	
	fun setDisplaySurface(surface: Surface) {
		replaceDisplaySurface(surface, false)
	}
	
	fun setDisplaySurface(view: TextureView) {
		replaceDisplaySurface(Surface(view.surfaceTexture), true)
	}
	
	fun setDisplaySurface(view: SurfaceView) {
		val surface = view.holder?.surface ?: return
		replaceDisplaySurface(surface, false)
	}
	
	/**
	 * 替换surface
	 */
	private fun replaceDisplaySurface(surface: Surface, ownsSurface: Boolean) {
		if (!surface.isValid) {
			if (ownsSurface) {
				runCatching { surface.release() }
			}
			return
		}
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			if (ownsSurface) {
				runCatching { surface.release() }
			}
			return
		}
		val oldSurface = mSurface
		val oldOwnsSurface = this.ownsSurface
		if (oldSurface === surface && oldOwnsSurface == ownsSurface) {
			return
		}
		if (oldSurface != null) {
			UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_PREVIEW)
		}
		mSurface = null
		this.ownsSurface = false
		if (oldOwnsSurface && oldSurface !== surface) {
			oldSurface?.let {
				runCatching { it.release() }
			}
		}
		// 同id的surface只允许添加一个
		if (!UvcNativeBridge.nativeAddSurfaceTarget(
				nativeId, TARGET_PREVIEW, 0, 4, surface
			)) {
			if (ownsSurface) {
				runCatching { surface.release() }
			}
			return
		}
		this.mSurface = surface
		this.ownsSurface = ownsSurface
	}
	
	/**
	 * 预初始化录制的surface
	 * 切换分辨率需要重新初始化
	 */
	fun prepareRecord(): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_RECORD)
		// 初始化video的surface，使用记录的fps
		recordEngine.prepareVideo(this.mPreviewWidth, this.mPreviewHeight, 30)
		val videoSurface = recordEngine.getVideoSurface()
		if (videoSurface == null || !UvcNativeBridge.nativeAddSurfaceTarget(
				nativeId, TARGET_RECORD, 3, 4, videoSurface
			)) {
			recordEngine.releaseVideo()
			return false
		}
		return true
	}
	
	/**
	 * 释放录制的surface
	 */
	fun releaseRecord() {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			recordEngine.releaseVideo()
			return
		}
		UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_RECORD)
		recordEngine.releaseVideo()
	}
	
	/**
	 * 设置录制帧率
	 */
	fun setRecordFps(fps: Int) {
		recordEngine.setFps(fps)
	}
	
	/**
	 * 开始录制
	 */
	fun startRecord(context: Context, isUseAudio: Boolean = false, filePath: String? = null): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		if (currentRecordState.get()) return false
		if (!mPreviewState.get()) return false
		if (!prepareRecord()) {
			return false
		}
		val outPath = if (filePath.isNullOrEmpty()) {
			"${context.filesDir}${File.separatorChar}Video${File.separatorChar}Video_${System.currentTimeMillis()}.mp4"
		} else filePath
		if (!recordEngine.prepare(context, 0, outPath, isUseAudio)) {
			return false
		}
		if (!recordEngine.start(this.mPreviewWidth, this.mPreviewHeight)) {
			return false
		}
		return UvcNativeBridge.startRecord(nativeId).apply {
			if (!this) {
				runBlocking { recordEngine.stop() }
			}
			currentRecordState.set(this)
		}
	}
	
	/**
	 * 停止录制
	 */
	suspend fun stopRecord(): String? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return null
		}
		currentRecordState.set(false)
		UvcNativeBridge.stopRecord(nativeId)
		SystemClock.sleep(30)
		return recordEngine.stop()
	}
	
	/**
	 * 拍照获取
	 */
	suspend fun takeCaptureBytes(): ByteArray? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		val captureImageReader = mCaptureImageReader
		return if (captureImageReader == null) {
			withTimeoutOrNull(2000) {
				suspendCancellableCoroutine { continuation ->
					// 如果已经有拍照请求，直接失败（避免并发）
					val callback: (ByteArray, Int, Int, Int) -> Unit = { bytes, format, width, height ->
						continuation.resume(normalizeCaptureBytes(bytes, format, width, height))
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
		} else {
			withTimeoutOrNull(6000) {
				suspendCancellableCoroutine { continuation ->
					continuation.invokeOnCancellation {
						Log.d("NativeUsbStream", "拍照出错 = $it")
						captureImageReader.setOnImageAvailableListener(null, null)
					}
					// ------------------------------
					// 2. 监听拍照图片
					// ------------------------------
					val listener = ImageReader.OnImageAvailableListener { reader ->
						Log.d("NativeUsbStream", "拍照结果 = ${reader.imageFormat}")
						captureImageReader.setOnImageAvailableListener(null, null) // 立即取消
						val data = runCatching {
							reader.acquireNextImage()?.use { image ->
								image.planes.imageDataToBytes(
									image.format, image.width, image.height
								)?.also {
									Log.d("NativeUsbStream", "拍照的数据值 = ${it.size}")
								}?.let { bytes ->
									normalizeCaptureBytes(
										bytes, image.format, image.width, image.height
									)
								}
							}
						}.getOrNull()
						if (continuation.isActive) {
							continuation.resume(data)
						}
					}
					captureImageReader.setOnImageAvailableListener(listener, mImageReaderHandler)
					if (!UvcNativeBridge.nativeTakePicture(nativeId)) {
						Log.d("NativeUsbStream", "拍照失败")
						captureImageReader.setOnImageAvailableListener(null, null) // 立即取消
						if (continuation.isActive) continuation.resume(null)
					}
				}
			}
		}
	}
	
	/**
	 * 初始化imageReader,当imageReader改变时，重新初始化
	 */
	fun initAllImageReader() {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return
		if (mPreviewListener != null) {
			UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_CALLBACK)
			mImageReader?.close()
			mImageReader = ImageReader.newInstance(
				this.mPreviewWidth, this.mPreviewHeight, PixelFormat.RGBA_8888, 2
			)
			mImageReader?.surface?.let {
				UvcNativeBridge.nativeAddSurfaceTarget(
					nativeId, TARGET_CALLBACK, 1, 4, it
				)
			}
		} else {
			UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_CAPTURE)
			mCaptureImageReader?.close()
			mCaptureImageReader = ImageReader.newInstance(
				this.mPreviewWidth, this.mPreviewHeight, PixelFormat.RGBA_8888, 2
			)
			mCaptureImageReader?.surface?.let {
				UvcNativeBridge.nativeAddSurfaceTarget(nativeId, TARGET_CAPTURE, 2, 4, it)
			}
		}
		
	}
	
	/**
	 * 注册usb广播
	 */
	private fun initReceiver() {
		// 已经注册过就不在进行注册
		if (isReceiverSuccess) return
		val context = mContext.get() ?: return
		try {
			val intentFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
			//注册广播，接受对应的结果
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				context.registerReceiver(
					mUsbDetachReceiver, intentFilter, Context.RECEIVER_EXPORTED
				)
			} else {
				context.registerReceiver(mUsbDetachReceiver, intentFilter)
			}
			isReceiverSuccess = true
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	/**
	 * 解除注册
	 */
	private fun unReceiver() {
		val context = mContext.get() ?: return
		if (isReceiverSuccess) runCatching { context.unregisterReceiver(mUsbDetachReceiver) }
		isReceiverSuccess = false
	}
	
	/**
	 * 创建imageReader使用的thread
	 */
	private fun initImageReaderThread() {
		if (mImageReaderHandler != null) return
		val threadName = "Native-Image"
		val handlerThread = HandlerThread(threadName).apply {
			start()
		}
		mImageReaderThread = handlerThread
		mImageReaderHandler = Handler(handlerThread.looper)
	}
	
	/**
	 * 销毁thread
	 */
	private fun destroyImageReaderThread() {
		mImageReaderThread?.quitSafely()
		mImageReaderThread = null
		mImageReaderHandler = null
	}
	
	/**
	 * 图像统一转成 NV21
	 */
	private fun normalizeCaptureBytes(bytes: ByteArray, format: Int, width: Int, height: Int): ByteArray? {
		return when (format) {
			ImageFormat.NV21, ImageFormat.YUV_420_888 -> bytes
			PixelFormat.RGBA_8888 -> bytes.rgba8888ToNv21(width, height)
			else -> bytes.toBitmap(format, width, height)?.let { bitmap ->
				try {
					bitmap.toNv21Bytes()
				} finally {
					if (!bitmap.isRecycled) {
						bitmap.recycle()
					}
				}
			}
		}
	}
	
	/**
	 * 读取当前imageReader返回的帧数据
	 */
	private fun readReaderBytes(imageReader: ImageReader?) {
		//监听回调
		val acquireNextImage = runCatching { imageReader?.acquireLatestImage() }.getOrNull() ?: return
		//获取每一帧数据
		if (mImageReader == null || !mPreviewState.get() || (pictureContinuation.get() == null && mPreviewListener == null)) {
			acquireNextImage.close()
			return
		}
		try {
			val width = acquireNextImage.width
			val height = acquireNextImage.height
			val format = acquireNextImage.format
			val bytes = acquireNextImage.planes.imageDataToBytes(format, width, height) ?: return
			// 拍照优先
			pictureContinuation.getAndSet(null)?.let {
				it.invoke(bytes, format, width, height)
				return
			}
			// 预览回调
			normalizeCaptureBytes(bytes, format, width, height)?.let {
				mPreviewListener?.invoke(it, width, height)
			}
			
		} catch (e: Exception) {
			e.printStackTrace()
		} finally {
			acquireNextImage.close()
		}
	}
}
