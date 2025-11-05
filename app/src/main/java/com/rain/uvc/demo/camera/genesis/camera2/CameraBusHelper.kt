package com.rain.uvc.demo.camera.genesis.camera2

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import com.once.camera.CameraControlHelper
import com.once.camera.CameraStateException
import com.once.camera.factory.ICameraDevice
import com.once.camera.mode.CameraSize
import com.once.camera.parameters.CameraPreviewFormat
import com.once.camera.parameters.Parameters
import com.once.camera.parameters.SupportParameters
import com.rain.uvc.demo.utils.appLifeScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * @author yuan
 * @createTime: 2025/9/22
 * @des 摄像头总线管理类，处理全局打开摄像头，关闭摄像头等操作
 */
class CameraBusHelper private constructor() {
	companion object {
		val helper by lazy { CameraBusHelper() }
	}
	
	private var mCameraDevice: ICameraDevice? = null // 摄像头操作的缓存对象
	
	@Volatile
	private var currentCameraState: CameraState = CameraState.DEFAULT // 当前摄像头状态， 0 - 默认，1 - 已打开，2 - 正在打开
	private var cameraOpenJob: Job? = null //
	
	/**
	 * 打开摄像头
	 */
	fun open(context: Context, listener: ((isSuccess: Boolean, message: String) -> Unit)) {
		if (!CameraControlHelper.checkPermission()) {
			listener.invoke(false, "请检查摄像头权限")
			return
		}
		if (currentCameraState != CameraState.DEFAULT) {
			listener.invoke(false, "当前摄像头正在打开，请关闭后重新打开")
			return
		}
		currentCameraState = CameraState.OPENING
		cameraOpenJob?.cancel()
		cameraOpenJob = appLifeScope.launch(Dispatchers.IO) {
			var cameraDeviceResult = openCamera2(context)
			if (cameraDeviceResult.getOrNull() == null) cameraDeviceResult = openCamera1(context)
			if (cameraDeviceResult.getOrNull() == null) cameraDeviceResult = openCameraUsb()
			val cameraDevice = cameraDeviceResult.getOrNull()
			if (cameraDevice == null) {
				withContext(Dispatchers.Main) {
					listener.invoke(
						false, cameraDeviceResult.exceptionOrNull()?.message ?: "摄像头打开失败"
					)
				}
				return@launch
			}
			// 这里需要初始化摄像头的分辨率
			val previewSizes = cameraDevice.getSupportParameters(SupportParameters.PREVIEW_SIZE)
			if (previewSizes.isNullOrEmpty()) {
				withContext(Dispatchers.Main) {
					listener.invoke(false, "获取支持的分辨率列表失败")
				}
				cameraDevice.close()
				return@launch
			}
			//当前支持的预览类型
			val previewFormat = cameraDevice.getSupportParameters(SupportParameters.PREVIEW_FORMAT)
			if (previewFormat.isNullOrEmpty()) {
				withContext(Dispatchers.Main) {
					listener.invoke(false, "获取支持的图像格式失败")
				}
				cameraDevice.close()
				return@launch
			}
			
			val format = previewFormat.checkFormat() // 配置最合适的图像格式
			val cameraSize = previewSizes.checkSize() // 配置最合适的宽高
			cameraDevice.setPreviewSize(cameraSize.width, cameraSize.height, format)
			currentCameraState = CameraState.OPENED
			mCameraDevice = cameraDevice
			withContext(Dispatchers.Main) {
				listener.invoke(true, "成功")
			}
		}
	}
	fun setPreviewListener(listener: ((bytes: ByteArray, width: Int, height: Int) -> Unit)?){
		mCameraDevice?.setPreviewListener(listener)
	}
	
	/**
	 * 开启预览
	 */
	suspend fun startPreview(): Boolean {
		return mCameraDevice?.startPreview() ?: false
	}
	
	/**
	 * 关闭预览
	 */
	fun stopPreview(): Boolean {
		return mCameraDevice?.stopPreview() ?: false
	}
	
	/**
	 * 拍照
	 */
	suspend fun takePicture(showView: View): Bitmap? {
		return withContext(Dispatchers.IO) {
			return@withContext mCameraDevice?.takePicture(showView.measuredWidth, showView.measuredHeight)
		}
	}
	
	/**
	 * 获取拍照是否需要镜像
	 */
	fun loadJpegMirrorState() = mCameraDevice?.loadJpegMirrorState() ?: false
	
	/**
	 * 关闭摄像头
	 */
	fun close() {
		mCameraDevice?.stopPreview()
		mCameraDevice?.close()
		mCameraDevice = null
		currentCameraState = CameraState.DEFAULT
	}
	
	/**
	 * 获取当前图像变换的矩阵
	 */
	fun getCurrentOrientation(): Int {
		return mCameraDevice?.getParameter(Parameters.ORIENTATION) ?: 0
	}
	
	/**
	 * 设置预览控件
	 */
	fun setDisplaySurface(view: SurfaceView) {
		mCameraDevice?.setDisplaySurface(view)
	}
	
	/**
	 * 设置预览控件
	 * TextureView 需要同时配置预览方向
	 */
	fun setDisplaySurface(view: TextureView) {
		mCameraDevice?.setDisplaySurface(view)
	}
	
	/**
	 * 打开usb摄像头
	 */
	private fun openCameraUsb(): Result<ICameraDevice> {
		val uvcDevices = CameraControlHelper.loadUvcDevices()
		if (uvcDevices.isNullOrEmpty()) return Result.failure(CameraStateException("未发现摄像头"))
		return CameraControlHelper.openCamera(uvcDevices[0])
	}
	
	/**
	 * 打开摄像头2
	 */
	private suspend fun openCamera2(context: Context): Result<ICameraDevice> {
		val native2Cameras = CameraControlHelper.loadNative2Cameras() // 默认先获取camera2的摄像头，如果不存在，再获取camera1，最后使用usb摄像头
		if (native2Cameras.isNullOrEmpty()) return Result.failure(CameraStateException("未发现摄像头"))
		return CameraControlHelper.openCamera(native2Cameras[0], context.loadRotation()) //打开第一个摄像头
	}
	
	/**
	 * 打开摄像头1
	 */
	private suspend fun openCamera1(context: Context): Result<ICameraDevice> {
		if (CameraControlHelper.loadNative1Cameras() <= 0) return Result.failure(
			CameraStateException("未发现摄像头")
		)
		return CameraControlHelper.openCamera(0, context.loadRotation())
	}
	
}

/**
 * 获取activity的方向
 */
private fun Context.loadRotation(): Int {
	return when {
		// Android 11+ 推荐使用 Context.display
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> this.display.rotation
		this is Activity -> this.windowManager.defaultDisplay.rotation
		else -> (this.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
	}
}

/**
 * 获取支持的最佳图像格式
 */
private fun Array<CameraPreviewFormat>.checkFormat(): CameraPreviewFormat {
	if (this.contains(CameraPreviewFormat.JPEG)) return CameraPreviewFormat.JPEG
	if (this.contains(CameraPreviewFormat.MJPEG)) return CameraPreviewFormat.MJPEG
	if (this.contains(CameraPreviewFormat.NV21)) return CameraPreviewFormat.NV21
	if (this.contains(CameraPreviewFormat.RGB)) return CameraPreviewFormat.RGB
	if (this.contains(CameraPreviewFormat.BGR)) return CameraPreviewFormat.BGR
	return this[0]
}

/**
 * 获取支持的分辨率信息
 */
private fun Array<CameraSize>.checkSize(): CameraSize {
	var size = this.find { (it.width == 1920 && it.height == 1080) || (it.height == 1920 && it.width == 1080) }
	if (size == null) size = this.find { (it.width == 1280 && it.height == 720) || (it.height == 1280 && it.width == 720) }
	if (size == null) size = this.find { (it.width == 640 && it.height == 480) || (it.height == 640 && it.width == 480) }
	if (size == null) size = this[0]
	return size
}

/**
 * 当前摄像头状态
 */
enum class CameraState { DEFAULT, OPENING, OPENED }

/**
 * 对bitmap，进行裁剪显示处理
 */
fun Bitmap.bitmapCutup(view: View, isJpegMirror: Boolean): Bitmap {
	// 2. 构建变换矩阵（跟预览 TextureView 一样的规则）
	val viewWidth = view.measuredWidth
	val viewHeight = view.measuredHeight
	
	val imageWidth = this.width.toFloat()
	val imageHeight = this.height.toFloat()
	
	val matrix = Matrix()
	val cx = imageWidth / 2f
	val cy = imageHeight / 2f
	
	// CENTER_CROP 缩放
	val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
	
	matrix.postScale(scale, scale, cx, cy)
	
	// 镜像处理
	if (isJpegMirror) matrix.postScale(-1f, 1f, cx, cy)
	
	// 生成 Bitmap
	val scaledBitmap = Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
	
	// 再从中心裁剪到控件大小
	val startX = ((scaledBitmap.width - viewWidth) / 2f).coerceAtLeast(0f).toInt()
	val startY = ((scaledBitmap.height - viewHeight) / 2f).coerceAtLeast(0f).toInt()
	
	Log.d("CameraBusHelper", "sourceBitmap = ${this.width}*${this.height}")
	// 4️⃣ 应用变换生成新的 Bitmap
	return Bitmap.createBitmap(scaledBitmap, startX, startY, viewWidth, viewHeight).also {
		Log.d("CameraBusHelper", "cutBitmap = ${it.width}*${it.height}")
		scaledBitmap.recycle()
	}
}