package com.rain.uvc.camera

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
import com.rain.uvc.listener.IButtonListener
import com.rain.uvc.listener.IFrameListener
import com.rain.uvc.parameters.CameraDataFormat
import com.rain.uvc.parameters.CameraPreviewFormat
import com.rain.uvc.parameters.Parameters
import com.rain.uvc.parameters.SupportParameters
import com.rain.uvc.utils.CameraNativeUtils
import com.rain.uvc.utils.CameraNativeUtils.getParameter
import com.rain.uvc.utils.CameraNativeUtils.getSupportedParameter
import com.rain.uvc.utils.CameraNativeUtils.nativeSetDisplaySurface
import com.rain.uvc.utils.CameraNativeUtils.nativeSetPreviewSize
import com.rain.uvc.utils.CameraNativeUtils.nativeTakePicture
import com.rain.uvc.utils.CameraNativeUtils.setParameter
import com.rain.uvc.utils.CameraNativeUtils.setPreviewListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yuan
 * @createTime: 2026/2/2
 * @des
 */
class NCameraDevice(context: Context, nativeId: Long, val deviceName: String,val deviceId: String, connection: UsbDeviceConnection?) {
	// 请求c层资源的对应的内存id
	private val mNativeAtomic = AtomicLong(0L)
	private var iDetachedCloseListener: (() -> Unit)? = null
	private var textureSurface: Surface? = null
	
	// context对象实例
	private val mContext = AtomicReference<Context>()
	
	// 当前是否正在预览
	private val mPreviewState = AtomicBoolean(false)
	private var iUsbDeviceConnect: UsbDeviceConnection? = null
	
	// 当前注册usb监听状态
	private val mReceiverUsbState = AtomicBoolean(false)
	
	/**
	 * usb移除监听
	 */
	private val mUsbDetachReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			val action = intent?.action
			if (action.isNullOrBlank() || action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
			val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
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
		mNativeAtomic.set(nativeId)
		mContext.set(context.applicationContext)
		iUsbDeviceConnect = connection
		if (connection != null) {
			initReceiver()
		}
	}
	
	fun close() {
		this.iDetachedCloseListener = null
		closeCamera() // 关闭摄像头
	}
	
	/**
	 * 关闭摄像头
	 */
	private fun closeCamera() {
		unReceiver() // 解绑
		val nativeId = mNativeAtomic.get()
		if (nativeId != 0L) {
			CameraNativeUtils.nativeClose(nativeId);
		}
		mNativeAtomic.set(0L)
		runCatching { iUsbDeviceConnect?.close() }
		iUsbDeviceConnect = null
		mContext.set(null)
		textureSurface?.release()
		textureSurface = null
	}
	
	/**
	 * 开启预览
	 */
	fun startPreview(): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return false
		if (mPreviewState.get()) return true
		if (!CameraNativeUtils.nativeStartPreview(nativeId)) {//如果开启失败，则返回false
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
		val state = CameraNativeUtils.nativeStopPreview(nativeId)
		mPreviewState.set(false)
		return state
	}
	
	/**
	 * 是否打开
	 */
	fun cameraIsOpen() = mNativeAtomic.get() != 0L
	
	/**
	 * 设置预览分辨率
	 *
	 * @param width  宽
	 * @param height 高
	 * @param format 使用的解码类型，当前只支持yuy2/mjpeg
	 */
	fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		return nativeSetPreviewSize(nativeId, width, height, format.value)
	}
	
	/**
	 * 设置对应的预览控件
	 *
	 * @param surface 当前使用的预览控件
	 * @return 是否设置成功
	 */
	fun setDisplaySurface(surface: Surface): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		return nativeSetDisplaySurface(nativeId, surface)
	}
	
	/**
	 * 设置对应的预览控件
	 *
	 * @param view 当前使用的预览控件
	 * @return 是否设置成功
	 */
	fun setDisplaySurface(view: SurfaceView): Boolean {
		return setDisplaySurface(view.holder.surface)
	}
	
	/**
	 * 设置对应的预览控件
	 *
	 * @param view 当前使用的预览控件
	 * @return 是否设置成功
	 */
	fun setDisplaySurface(view: TextureView): Boolean {
		textureSurface?.release()
		return setDisplaySurface(Surface(view.surfaceTexture).apply {
			textureSurface = this
		})
	}
	
	/**
	 * 设置预览监听
	 */
	fun setPreviewListener(listener: IFrameListener?, format: CameraDataFormat): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		return setPreviewListener(nativeId, listener, format.value)
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
		CameraNativeUtils.setButtonListener(nativeId, listener)
	}
	
	/**
	 * 设置参数
	 */
	fun <T> setParameter(key: Parameters.Key<T>, value: T): Boolean {
		return setParameter(mNativeAtomic.get(), key, value)
	}
	
	/**
	 * 获取参数
	 */
	fun <T> getParameter(key: Parameters.Key<T>): T? {
		return getParameter(mNativeAtomic.get(), key)
	}
	
	/**
	 * 获取支持的类型列表
	 */
	fun <T> getSupportedParameter(key: SupportParameters.Key<T>): T? {
		return getSupportedParameter(mNativeAtomic.get(), key)
	}
	
	/**
	 * 注册usb广播
	 */
	private fun initReceiver() {
		// 已经注册过就不在进行注册
		if (mReceiverUsbState.get()) return
		val context = mContext.get() ?: return
		try {
			val intentFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
			//注册广播，接受对应的结果
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				context.registerReceiver(mUsbDetachReceiver, intentFilter, Context.RECEIVER_EXPORTED)
			} else {
				context.registerReceiver(mUsbDetachReceiver, intentFilter)
			}
			mReceiverUsbState.set(true)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	/**
	 * 解除注册
	 */
	private fun unReceiver() {
		val context = mContext.get() ?: return
		if (mReceiverUsbState.get()) runCatching { context.unregisterReceiver(mUsbDetachReceiver) }
		mReceiverUsbState.set(false)
	}
	
	/**
	 * 拍照
	 */
	fun takePicture(): ByteArray? {
		if (!mPreviewState.get()) return null
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return runCatching { nativeTakePicture(nativeId) }.getOrNull()
	}
}