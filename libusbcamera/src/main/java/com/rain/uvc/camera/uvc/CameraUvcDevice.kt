package com.rain.uvc.camera.uvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.rain.uvc.parameters.Parameters
import com.rain.uvc.parameters.SupportParameters
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.parameters.CameraDataFormat
import com.rain.uvc.parameters.CameraParameterType
import com.rain.uvc.parameters.CameraPreviewFormat
import com.rain.uvc.utils.CameraNativeUtils
import com.rain.uvc.utils.toBitmap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile

/**
 * @author yuan
 * @createTime: 2025/9/18
 * @des uvc相机的操作
 */
class CameraUvcDevice(context: Context, nativeId: Long, val deviceName: String, connection: UsbDeviceConnection) : ICameraDevice() {
	//设备名称，如果为video时为设备路径，usb时通过name检查当前是否是同一个值的回调
	private val mNativeAtomic: AtomicLong = AtomicLong(nativeId)
	
	// context对象实例
	private val mContext = AtomicReference<Context>()
	
	//打开结果回调
	private var iUsbDeviceConnect: UsbDeviceConnection? = connection
	
	//当前是否注册广播成功
	@Volatile
	private var isReceiverSuccess = false
	
	// 缓存控件
	private val mTextureCache = AtomicReference<TextureView>()
	
	//usb移除回调监听
	private val usbDetachedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent?) {
			val action = intent?.action
			if (action.isNullOrEmpty() || action != UsbManager.ACTION_USB_DEVICE_DETACHED) {
				return
			}
			//获取传递进来的usb设备
			val usbDevice = intent.getParcelableExtra<UsbDevice?>(UsbManager.EXTRA_DEVICE)
			if (usbDevice == null || deviceName != usbDevice.deviceName) {
				return
			}
			//说明当前是需要的类型，直接回调结果
			iDetachedCloseListener?.invoke()
			close()
		}
	}
	
	init {
		mContext.set(context.applicationContext)
		initReceiver()
	}
	
	override fun setPreviewSize(width: Int, height: Int, format: CameraPreviewFormat): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		return CameraNativeUtils.nativeSetPreviewSize(nativeId, width, height, format.value)
	}
	
	override fun startPreview() {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return
		if (this.isPreviewIng.get()) return
		if (!CameraNativeUtils.nativeStartPreview(nativeId)) {//如果开启失败，则返回false
			return
		}
		CameraNativeUtils.setPreviewListener(nativeId, { width, height, frame ->
			val listener = iPreviewListener ?: return@setPreviewListener
			listener.invoke(ByteArray(frame.remaining()).also {
				frame.get(it)
			}, width, height)
		}, CameraDataFormat.MJPEG.value)
		this.isPreviewIng.set(true)
	}
	
	override fun stopPreview() {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return
		}
		CameraNativeUtils.nativeStopPreview(nativeId)
		this.isPreviewIng.set(false)
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
	
	override fun <V> setParameter(key: Parameters.Key<V>, value: V): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return false
		return when(key.type){
			CameraParameterType.PIC_ORIENTATION -> {
				if (value !is Int) return false
				this.mPicOrientation = value
				true
			}
			else -> CameraNativeUtils.setParameter(nativeId, key, value)
		}
	}
	
	override fun <V> getParameter(key: Parameters.Key<V>): V? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return when (key.type) {
			CameraParameterType.PIC_ORIENTATION -> this.mPicOrientation // 拍照方向
			else -> CameraNativeUtils.getParameter(nativeId, key)
		} as? V
	}
	
	override fun <T> getSupportParameters(supportKey: SupportParameters.Key<T>): T? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		return CameraNativeUtils.getSupportedParameter(nativeId, supportKey)
	}
	
	override fun closeCamera(): Boolean {
		unReceiver()
		this.iDetachedCloseListener = null
		val nativeId = mNativeAtomic.get()
		if (nativeId != 0L) { //释放native层的资源
			CameraNativeUtils.nativeClose(nativeId)
		}
		// 清理控件
		mTextureCache.set(null)
		
		mNativeAtomic.set(0L)
		if (iUsbDeviceConnect != null) {
			iUsbDeviceConnect!!.close()
			iUsbDeviceConnect = null
		}
		mContext.set(null)
		return true
	}
	
	override fun startFaceDetection(): Boolean {
		return false
	}
	
	override fun stopFaceDetection(): Boolean {
		return false
	}
	
	override suspend fun takePicture(cropWidth: Int, cropHeight: Int): Bitmap? {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) return null
		val nativeTakePicture = CameraNativeUtils.nativeTakePicture(nativeId)
		if (nativeTakePicture == null || nativeTakePicture.isEmpty()) return null
		return nativeTakePicture.toBitmap(mPicOrientation, false, cropWidth, cropHeight)
	}
	
	private fun setDisplaySurfaceData(surface: Surface): Boolean {
		val nativeId = mNativeAtomic.get()
		if (nativeId == 0L) {
			return false
		}
		CameraNativeUtils.nativeSetDisplaySurface(nativeId, surface)
		return true
	}
	
	/**
	 * 注册解绑监听
	 */
	@Synchronized
	private fun initReceiver() {
		if (isReceiverSuccess) return
		val context = mContext.get() ?: return
		try {
			val intentFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
			//注册广播，接受对应的结果
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				context.registerReceiver(usbDetachedReceiver, intentFilter, Context.RECEIVER_EXPORTED)
			} else {
				context.registerReceiver(usbDetachedReceiver, intentFilter)
			}
			isReceiverSuccess = true
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	/**
	 * 解除注册
	 */
	@Synchronized
	private fun unReceiver() {
		val context = mContext.get() ?: return
		if (isReceiverSuccess) runCatching { context.unregisterReceiver(usbDetachedReceiver) }
		isReceiverSuccess = false
	}
}