package com.rain.uvc.factory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.rain.uvc.bridge.UvcNativeBridge
import com.rain.uvc.listener.IButtonListener
import com.rain.uvc.listener.IFrameListener
import com.rain.uvc.parameters.UvcCameraParameter
import com.rain.uvc.parameters.UvcDataFormat
import com.rain.uvc.parameters.UvcPreviewFormat
import com.rain.uvc.parameters.UvcSupportParameter
import com.rain.uvc.recorder.RecorderEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2026/5/15
 * @des usb相机的配置
 */
class NativeUsbDevice internal constructor(context: Context, nativeId: Long, val deviceName: String, connection: UsbDeviceConnection?) {
	private companion object { // 支持的surface类型
		const val TARGET_PREVIEW = "preview_surface"
		const val TARGET_RECORD = "record_surface"
	}
	
	//设备名称，如果为video时为设备路径，usb时通过name检查当前是否是同一个值的回调
	private val mNativeAtomic: AtomicLong = AtomicLong(nativeId)
	private var iDetachedCloseListener: (() -> Unit)? = null
	
	// context对象实例
	private val mContext = AtomicReference<Context>()
	
	//打开结果回调
	private var iUsbDeviceConnect: UsbDeviceConnection?
	
	//当前是否注册广播成功
	@Volatile
	private var isReceiverSuccess = false
	
	private var ownsSurface: Boolean = false
	private var mSurface: Surface? = null
	
	// 当前是否正在预览
	private val mPreviewState = AtomicBoolean(false)
	private var mPreviewWidth = 640
	private var mPreviewHeight = 480
	
	// =========================================================
	// 录制相关状态（一体化封装 RecorderEngine）
	// =========================================================
	private val mRecorderEngine = RecorderEngine()
	private var recordFps = 30
	private val mRecordStartedState = AtomicBoolean(false)
	
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
	
	/**
	 * 初始化
	 */
	init {
		mContext.set(context.applicationContext)
		iUsbDeviceConnect = connection
		if (connection != null) {
			initReceiver()
		}
	}
	
	/**
	 * 执行关闭
	 */
	fun close() {
		this.iDetachedCloseListener = null
		closeCamera() // 关闭摄像头
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
		mRecorderEngine.releaseVideo()
		mSurface = null
		ownsSurface = false
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
		val result = UvcNativeBridge.nativeSetPreviewSize(nativeId, width, height, format.format)
		if (result) {
			mPreviewWidth = width
			mPreviewHeight = height
		}
		return result
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
	 * 获取支持的参数列表
	 */
	fun <T> getSupportParameters(key: UvcSupportParameter.Key<T>): T? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return UvcNativeBridge.getSupportedParameter(nativeId, key)
		
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
	 * 拍照
	 */
	fun takePicture(): ByteArray? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return UvcNativeBridge.nativeTakePicture(nativeId)
	}
	
	/**
	 * 开启预览
	 */
	fun startPreview(): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return false
		if (mPreviewState.get()) return true
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
		val state = UvcNativeBridge.nativeStopPreview(nativeId)
		mPreviewState.set(false)
		return state
	}
	
	/**
	 * 设置对应的预览控件
	 *
	 * @param surface 当前使用的预览控件
	 * @return 是否设置成功
	 */
	fun setDisplaySurface(surface: Surface) {
		replaceDisplaySurface(surface, false)
	}
	
	/**
	 * 设置对应的预览控件
	 *
	 * @param view 当前使用的预览控件
	 * @return 是否设置成功
	 */
	fun setDisplaySurface(view: TextureView) {
		val surfaceTexture = view.surfaceTexture ?: return
		replaceDisplaySurface(Surface(surfaceTexture), true)
	}
	
	/**
	 * 设置对应的预览控件
	 *
	 * @param view 当前使用的预览控件
	 * @return 是否设置成功
	 */
	fun setDisplaySurface(view: SurfaceView) {
		val surface = view.holder?.surface ?: return
		replaceDisplaySurface(surface, false)
	}
	
	/**
	 * 设置预览监听
	 */
	fun setPreviewListener(listener: IFrameListener?, format: UvcDataFormat = UvcDataFormat.NV21): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		return UvcNativeBridge.setPreviewListener(nativeId, listener, format.format)
	}
	
	/**
	 * 设置usb摄像头断开关闭回调
	 */
	fun setDetachedCloseListener(listener: (() -> Unit)?): Boolean {
		this.iDetachedCloseListener = listener
		return true
	}
	
	/**
	 * 设置预览监听
	 */
	fun setButtonListener(listener: IButtonListener?) {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return
		UvcNativeBridge.setButtonListener(nativeId, listener)
	}
	
	/**
	 * 初始化录制用的 PersistentInputSurface，并将其绑定到 native 预览管线。
	 * 为了保证 EGL 路径一次性确定，必须在 [startPreview] 之前调用。
	 *
	 * @param fps    录制帧率（默认 30）
	 */
	fun prepareRecord(fps: Int): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		// 正在预览中时无法初始化，需要在预览之前调用
		if (mRecordStartedState.get() || mPreviewState.get()) return false
		
		// 初始化video的surface，使用记录的fps
		if (!mRecorderEngine.prepareVideo(this.mPreviewWidth, this.mPreviewHeight, fps)) {
			return false
		}
		val videoSurface = mRecorderEngine.getVideoSurface()
		// 如果未初始化成功，则直接return
		if (videoSurface == null || !UvcNativeBridge.nativeAddSurfaceTarget(
				nativeId, TARGET_RECORD, true, videoSurface
			)) {
			mRecorderEngine.releaseVideo()
			return false
		}
		recordFps = fps
		return true
	}
	
	/**
	 * 释放录制的surface
	 */
	fun releaseRecord() {
		val nativeId = mNativeAtomic.get()
		if (nativeId != 0L) {
			UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_RECORD)
		}
		mRecorderEngine.releaseVideo()
	}
	
	/**
	 * 启动录制。会依次启动 Muxer / Video MediaCodec / （可选）Audio MediaCodec，
	 * 最后通知 native captureThread 在下一帧开始 swapToRecord。
	 */
	fun startRecord(context: Context, outputPath: String? = null, useAudio: Boolean = false, fps: Int = 30): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		if (!mPreviewState.get()) return false
		if (mRecordStartedState.get()) return true
		
		val outFile = File(
			if (outputPath.isNullOrEmpty()) {
				"${context.filesDir}${File.separatorChar}Video${File.separatorChar}Video_${System.currentTimeMillis()}.mp4"
			} else outputPath
		)
		
		if (!mRecorderEngine.prepare(context, 0, outFile, useAudio)) {
			if (outFile.exists()) outFile.delete()
			return false
		}
		if (!mRecorderEngine.start(this.mPreviewWidth, this.mPreviewHeight, fps)) {
			if (outFile.exists()) outFile.delete()
			return false
		}
		val nativeStarted = UvcNativeBridge.nativeStartRecord(nativeId)
		if (!nativeStarted) {
			runBlocking { mRecorderEngine.stop() }
			if (outFile.exists()) outFile.delete()
			mRecordStartedState.set(false)
			return false
		}
		mRecordStartedState.set(true)
		return true
	}
	
	/**
	 * 停止录制
	 */
	suspend fun stopRecord(): String? {
		val nativeId = mNativeAtomic.get()
		if (!mRecordStartedState.getAndSet(false)) {
			return null
		}
		if (nativeId != 0L) {
			runCatching { UvcNativeBridge.nativeStopRecord(nativeId) }
		}
		return mRecorderEngine.stop()
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
		val shouldRestartPreview = mPreviewState.get()
		if (shouldRestartPreview) {
			if (!UvcNativeBridge.nativeStopPreview(nativeId)) {
				if (ownsSurface) {
					runCatching { surface.release() }
				}
				return
			}
			mPreviewState.set(false)
		}
		runCatching { UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_PREVIEW) }
		if (this.ownsSurface) {
			runCatching { mSurface?.release() }
		}
		this.mSurface = null
		this.ownsSurface = false
		if (!UvcNativeBridge.nativeAddSurfaceTarget(nativeId, TARGET_PREVIEW, false, surface)) {
			if (ownsSurface) {
				runCatching { surface.release() }
			}
			return
		}
		this.mSurface = surface
		this.ownsSurface = ownsSurface
		if (shouldRestartPreview && !startPreview()) {
			runCatching { UvcNativeBridge.nativeRemoveSurfacesTarget(nativeId, TARGET_PREVIEW) }
			if (this.ownsSurface) {
				runCatching { this.mSurface?.release() }
			}
			this.mSurface = null
			this.ownsSurface = false
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
}
