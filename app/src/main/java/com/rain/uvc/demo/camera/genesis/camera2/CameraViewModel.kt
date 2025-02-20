package com.rain.uvc.demo.camera.genesis.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.provider.OverallContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des
 */
class CameraViewModel : BaseViewModel() {
	
	private val mCameraAtomic = AtomicReference<CameraDevice>()
	private var mCameraHandler: Handler? = null
	private var mHandlerThread: HandlerThread? = null
	private var captureRequestBuilder: CaptureRequest.Builder? = null
	
	//预览操作实例
	private var mCameraSession: CameraCaptureSession? = null
	
	//预览流回调的实例
	private var mImageReader: ImageReader? = null
	
	@Volatile
	private var isPreviewIng: Boolean = false
	
	private var resultBlock: (() -> Unit)? = null
	//回调
	/**
	 * 打开结果回调
	 */
	private val openStateCallBack = object : CameraDevice.StateCallback() {
		override fun onOpened(camera: CameraDevice) {
			Log.d("Camera2ViewModel", "onOpened:$camera")
			initPreviewCapture(camera)
			this@CameraViewModel.mCameraAtomic.set(camera)
			resultBlock?.invoke()
		}
		
		override fun onDisconnected(camera: CameraDevice) {
			Log.d("Camera2ViewModel", "onDisconnected:$camera")
		}
		
		override fun onError(camera: CameraDevice, error: Int) {
			Log.d("Camera2ViewModel", "onError:$error")
			this@CameraViewModel.mCameraAtomic.set(null)
		}
	}
	
	@SuppressLint("MissingPermission")
	fun openCamera(cameraId: String, block: () -> Unit) {
		//初始化可使用的handler
		this.resultBlock = block
		initHandler(cameraId)
		Log.d("Camera2ViewModel", "cameraId:$cameraId")
		val cameraManager = OverallContext.baseContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
		//执行打开摄像头
		initImageReader()
		try {
			cameraManager.openCamera(cameraId, openStateCallBack, mCameraHandler)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	private suspend fun createSession(device: CameraDevice, surface: Surface?, imageReader: ImageReader?, handler: Handler?): Result<CameraCaptureSession> {
		return runCatching<Result<CameraCaptureSession>> {
			withTimeout(3000) {
				suspendCancellableCoroutine { continuation ->
				
				}
			}
		}.getOrNull() ?: Result.failure(IllegalStateException("创建session错误"))
	}
	
	fun startPreview(view: TextureView) {
		val cameraDevice = mCameraAtomic.get()
		Log.d("Camera2ViewModel", "cameraDevice:$cameraDevice,isPreviewIng:$isPreviewIng")
		if (cameraDevice == null || isPreviewIng) return
		val captureBuilder = captureRequestBuilder ?: return
		val surface = Surface(view.surfaceTexture)
		captureBuilder.addTarget(surface)
		Log.d("Camera2ViewModel", "mCameraSession:$mCameraSession")
		mImageReader?.setOnImageAvailableListener({ }, mCameraHandler)
		if (mCameraSession == null) {
			cameraDevice.createCaptureSession(ArrayList<Surface>().also {
				it.add(surface)
				mImageReader?.surface?.apply { it.add(this) }
			}, object : CameraCaptureSession.StateCallback() {
				override fun onConfigured(session: CameraCaptureSession) {
					Log.d("Camera2ViewModel", "onConfigured:$session")
					val repeatingRequest = session.setRepeatingRequest(
						captureBuilder.build(), null, mCameraHandler
					)
					Log.d("Camera2ViewModel", "repeatingRequest:$repeatingRequest")
					mCameraSession = session
					isPreviewIng = true
					
				}
				
				override fun onConfigureFailed(session: CameraCaptureSession) {
					Log.d("Camera2ViewModel", "onConfigureFailed:$session")
					mCameraSession = null
				}
			}, mCameraHandler)
			return
		}
		val repeatingRequest = mCameraSession?.setRepeatingRequest(
			captureBuilder.build(), null, mCameraHandler
		)
		Log.d("Camera2ViewModel", "repeatingRequest:$repeatingRequest")
		isPreviewIng = true
	}
	
	private fun stopPreview() {
		isPreviewIng = false
		mImageReader?.setOnImageAvailableListener(null, null)
		mCameraSession?.stopRepeating()
	}
	
	private fun initImageReader() {
		//重置session
		mCameraSession?.stopRepeating()
		mCameraSession?.close()
		mCameraSession = null
		
		//关闭旧的imageReader
		mImageReader?.also {
			captureRequestBuilder?.removeTarget(it.surface)
			it.close()
		}
		mImageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1).also {
			captureRequestBuilder?.addTarget(it.surface)
		}
	}
	
	private fun closeCamera() {
		mCameraSession?.close()
		mCameraSession = null
		
		mImageReader?.close()
		mImageReader = null
		
		mCameraAtomic.get()?.close()
		mCameraAtomic.set(null)
		
		captureRequestBuilder = null
		stopHandlerThread()
	}
	
	override fun onCleared() {
		super.onCleared()
		Log.d("CameraViewModel", "onCleared")
		stopPreview()
		closeCamera()
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
	 * 初始化预览参数
	 */
	private fun initPreviewCapture(cameraDevice: CameraDevice) {
		//创建预览的数据
		val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
		//设置为自动模式，单个控制生效
		captureRequest.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
		captureRequest.set(CaptureRequest.CONTROL_AE_LOCK, false)
		//设置自动测光
		//自动对焦
		captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
		//设置闪光灯模式为持续照亮
		captureRequest.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
		//设置人脸检测模式
		this.captureRequestBuilder = captureRequest
	}
}