package com.rain.uvc.demo.camera.cpp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraControlHelper
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.camera.CameraOptions
import com.rain.uvc.demo.camera.CameraSupportOptions
import com.rain.uvc.demo.camera.resolvePreviewSize
import com.rain.uvc.demo.provider.OverallContext
import com.rain.uvc.demo.utils.PictureUtils
import com.rain.uvc.demo.utils.awaitAvailable
import com.rain.uvc.factory.NativeUsbDevice
import com.rain.uvc.mode.UvcCameraSize
import com.rain.uvc.parameters.UvcCameraParameter
import com.rain.uvc.parameters.UvcPreviewFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.ref.WeakReference

/**
 * @author yuan
 * @createTime: 2025/6/12
 * @des
 */
class CameraViewModel : BaseViewModel() {
	private var usbCameraDevice: UsbDevice? = null
	private var mVideoPath: String? = null
	
	// 相机控制类
	private var mCameraSession: NativeUsbDevice? = null
	
	// 最后一张照片的uri
	val lastPicPreview = MutableLiveData<Uri?>()
	
	// 当前类型：false=拍照模式, true=录像模式
	val isVideoMode = MutableLiveData(true)
	
	// 当前是否正在录制中
	val recordIng = MutableLiveData(false)
	
	// 录制时间（秒）
	val recordTime = MutableLiveData(0)
	
	// 录制时间格式化显示
	val recordTimeText = MutableLiveData("00:00")
	
	// 当前缩放值显示（如 "1.0x"）
	val zoomText = MutableLiveData("1.0x")
	
	// 相机打开状态
	private var mOpenJob: Job? = null
	private var mPreviewViewRef: WeakReference<TextureView>? = null
	
	// 当前参数
	val cameraOptions = CameraOptions()
	
	// 相机支持参数
	val cameraSupportOptions = CameraSupportOptions()
	
	// 录制帧率
	private var mVideoFps: Int = 30
	
	private var mRecordTimerJob: Job? = null
	private var mRecordStartTime: Long = 0
	
	fun initCameraId(usbDevice: UsbDevice?, videoPath: String?) {
		this.usbCameraDevice = usbDevice
		this.mVideoPath = videoPath
	}
	
	/**
	 * 打开相机
	 */
	fun open(view: TextureView) {
		val context = view.context
		mPreviewViewRef = WeakReference(view)
		// 1. 检查权限
		if (ContextCompat.checkSelfPermission(
				context, Manifest.permission.CAMERA
			) != PackageManager.PERMISSION_GRANTED) {
			return
		}
		val device = usbCameraDevice
		val videoPath = mVideoPath
		if (device == null && videoPath.isNullOrEmpty()) return
		if (mOpenJob?.isActive == true) return
		
		mOpenJob = viewModelScope.launch {
			val cameraResult = if (device != null) {
				CameraControlHelper.openCamera(context, device)
			} else if (!videoPath.isNullOrEmpty()) CameraControlHelper.openCamera(
				context, videoPath
			)
			else return@launch
				val cameraSession = cameraResult.getOrNull()
				if (cameraResult.isFailure || cameraSession == null) {
					Log.d("CameraViewModel", "打开结果 = ${cameraResult.exceptionOrNull()}")
					return@launch
				}
				// 初始化支持的参数
				cameraSupportOptions.initSupport(cameraSession)
				cameraOptions.initParameter(cameraSession)
				val previewSize = cameraSupportOptions.previewSizes?.resolvePreviewSize(cameraOptions.previewSize) ?: let {
					cameraSession.close()
					return@launch
				}
				cameraSession.setPreviewSize(
					previewSize.width, previewSize.height, UvcPreviewFormat.MJPEG
				)
				cameraOptions.previewSize = previewSize
				if (isVideoMode.value == true && !cameraSession.prepareRecord(mVideoFps)) {
					isVideoMode.postValue(false)
				}
				mCameraSession = cameraSession
			Log.d("CameraBusUtils", "等待初始化~~~")
			view.awaitAvailable()
			Log.d("CameraBusUtils", "等待初始化完成~~~")
			// 设置矩阵变化尺寸
//			if (isActive) cameraSession.updateTexture(view)
			Log.d("CameraBusUtils", "更新矩阵完成")
			// 这里等待加载完毕
			if (isActive) cameraSession.setDisplaySurface(view)
			Log.d("CameraBusUtils", "设置预览完成")
			if (isActive) mCameraSession?.startPreview()
			Log.d("CameraBusUtils", "开始预览完成")
			
			// 分辨率查询放到预览开始后再异步执行，不阻塞预览显
		}
	}
	
	/**
	 * 关闭相机
	 */
	fun close() {
		mOpenJob?.cancel()
		mCameraSession?.close()
		mCameraSession = null
		mPreviewViewRef = null
	}
	
	/**
	 * 设置缩放比例
	 */
	@SuppressLint("DefaultLocale")
	fun setZoom(ratio: Float) {
		Log.d("CameraViewModel", "setZoom = $ratio")
		val range = cameraSupportOptions.zoomRange ?: return
		if (range.first == 0 && range.last == 0) return // 不支持缩放
		
		// 将 0.0-1.0 映射到实际 zoom 范围
		val zoomValue = (range.first + (range.last - range.first) * ratio).toInt()
		cameraOptions.zoom = zoomValue
		
		mCameraSession?.setParameter(UvcCameraParameter.ZOOM, zoomValue)
		zoomText.value = String.format(
			"%.1fx", range.first + (range.last - range.first) * ratio
		)
	}
	
	/**
	 * 切换模式 false:拍照 / true：录像
	 */
	fun updateMode(isVideo: Boolean) {
		val session = mCameraSession ?: return
		val oldState = isVideoMode.value ?: false
		if (oldState == isVideo || recordIng.value == true) return // 相同时不处理
		val stopPreviewResult = session.stopPreview()
		var newVideoState: Boolean
		if (isVideo) {
			if (!session.prepareRecord(mVideoFps)) {
				newVideoState = false
				Toast.makeText(
					OverallContext.baseContext, "切换模式失败", Toast.LENGTH_SHORT
				).show()
			} else {
				newVideoState = true
			}
		} else {
			session.releaseRecord()
			newVideoState = false
		}
		if (stopPreviewResult && session.startPreview()) {
			cameraOptions.applyParameters(session)
		}
		isVideoMode.value = newVideoState
	}
	
	/**
	 * 设置视频帧率
	 */
	fun updateVideoFps(fps: Int) {
		mVideoFps = fps
		// 设置录制帧率
	}
	
	/**
	 * 执行拍照录制
	 */
	fun captureOrRecord() {
		val isVideo = isVideoMode.value ?: false
		if (isVideo) {
			toggleRecord()
		} else takePicture()
	}
	
	/**
	 * 拍照
	 */
	fun takePicture() {
		Log.d("CameraPicture", "开始执行拍照~~~~:")
			viewModelScope.launch {
				val data = mCameraSession?.takePicture()
				if (data != null) {
					val uri = ByteArrayInputStream(data).use {
						PictureUtils.saveGallery(
							OverallContext.baseContext,
							it,
							0,
							"IMG_${SystemClock.elapsedRealtime()}.jpeg"
						)
					}
					if (uri != null) {
						lastPicPreview.value = uri
					}
				
			}
		}
	}
	
	/**
	 * 切换录制状态
	 */
	private fun toggleRecord() {
		val isRecording = recordIng.value ?: false
		if (isRecording) {
			viewModelScope.launch {
				val outputPath = mCameraSession?.stopRecord()
				val saveUri = outputPath?.let { PictureUtils.saveVideoGallery(OverallContext.baseContext, File(it)) }
				Log.d("CameraViewModel", "停止录制结果 = $outputPath, saveUri = $saveUri")
				stopRecordTimer()
				recordIng.value = false
			}
		} else {
			val result = mCameraSession?.startRecord(OverallContext.baseContext)
			Log.d("CameraViewModel", "开始录制结果 = $result")
			if (result == true) {
				recordIng.value = true
				startRecordTimer()
			}
		}
	}
	
	/**
	 * 开始录制计时
	 */
	@SuppressLint("DefaultLocale")
	private fun startRecordTimer() {
		mRecordStartTime = SystemClock.elapsedRealtime()
		recordTime.value = 0
		recordTimeText.value = "00:00"
		mRecordTimerJob?.cancel()
		mRecordTimerJob = viewModelScope.launch {
			while (isActive) {
				delay(1000) // 每秒更新一次
				val elapsed = ((SystemClock.elapsedRealtime() - mRecordStartTime) / 1000).toInt()
				recordTime.value = elapsed
				recordTimeText.value = String.format("%02d:%02d", elapsed / 60, elapsed % 60)
			}
		}
	}
	
	/**
	 * 停止录制计时
	 */
	private fun stopRecordTimer() {
		mRecordTimerJob?.cancel()
		mRecordTimerJob = null
	}
	
	/**
	 * 获取图库数据
	 */
	fun loadImage() {
		if (!hasImagePermission()) return
		val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
		val projection = arrayOf(
			MediaStore.Images.Media._ID,
			MediaStore.Images.Media.DATE_TAKEN,
			MediaStore.Images.Media.DATE_ADDED,
			MediaStore.Images.Media.RELATIVE_PATH
		)
		
		// 👉 是否只查相机目录（推荐）
		val selection: String?
		val selectionArgs: Array<String>?
		
		if (Build.VERSION.SDK_INT >= 29) {
			selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
			selectionArgs = arrayOf("%DCIM/Camera%")
		} else {
			selection = null
			selectionArgs = null
		}
		// 👉 双排序，兼容各种 ROM
		val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, " + "${MediaStore.Images.Media.DATE_ADDED} DESC"
		OverallContext.baseContext.contentResolver.query(
			collection, projection, selection, selectionArgs, sortOrder
		)?.use { cursor ->
			val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
			Log.d("CameraViewModel", "查询到的id = $idIndex")
			if (cursor.moveToFirst()) {
				val id = cursor.getLong(idIndex)
				lastPicPreview.value = ContentUris.withAppendedId(collection, id).also {
					Log.d("CameraViewModel", "uri = $it")
				}
			}
		}
	}
	
	/**
	 * 检查权限
	 */
	fun hasImagePermission(): Boolean {
		return if (Build.VERSION.SDK_INT >= 33) {
			ContextCompat.checkSelfPermission(
				OverallContext.baseContext, Manifest.permission.READ_MEDIA_IMAGES
			) == PackageManager.PERMISSION_GRANTED
		} else {
			ContextCompat.checkSelfPermission(
				OverallContext.baseContext, Manifest.permission.READ_EXTERNAL_STORAGE
			) == PackageManager.PERMISSION_GRANTED
		}
	}
	
	/**
	 * 更新Int类型相机参数
	 */
	fun updateIntParameter(key: UvcCameraParameter.Key<Int>, value: Int): Boolean {
		return mCameraSession?.setParameter(key, value) ?: false
	}
	
	/**
	 * 更新Boolean类型相机参数
	 */
	fun updateBoolParameter(key: UvcCameraParameter.Key<Boolean>, value: Boolean): Boolean {
		return mCameraSession?.setParameter(key, value) ?: false
	}

	fun updateDisplayOrientation(orientation: Int): Boolean {
		val session = mCameraSession ?: return false
		val previewView = mPreviewViewRef?.get()
		val shouldRestartRecordPipeline = isVideoMode.value == true && recordIng.value != true && previewView != null
		if (!shouldRestartRecordPipeline) {
			val result = updateIntParameter(UvcCameraParameter.ORIENTATION, orientation)
			if (result) {
				cameraOptions.mPicOrientation = orientation
			}
			return result
		}
		val activePreviewView = previewView ?: return false
		if (!session.stopPreview()) return false
		session.releaseRecord()
		if (!session.setParameter(UvcCameraParameter.ORIENTATION, orientation)) {
			restartPreview(session, activePreviewView, true)
			return false
		}
		val restarted = restartPreview(session, activePreviewView, true)
		if (restarted) {
			cameraOptions.mPicOrientation = orientation
		}
		return restarted
	}

	fun updateMirrorState(isMirror: Boolean): Boolean {
		val result = updateBoolParameter(UvcCameraParameter.MIRROR, isMirror)
		if (result) {
			cameraOptions.mPicMirrorState = isMirror
		}
		return result
	}

	fun updatePreviewSize(size: UvcCameraSize): Boolean {
		val session = mCameraSession ?: return false
		val previewView = mPreviewViewRef?.get() ?: return false
		if (recordIng.value == true) return false
		if (cameraOptions.previewSize == size) return true
		val shouldPrepareRecord = isVideoMode.value == true
		if (!session.stopPreview()) return false
		if (shouldPrepareRecord) {
			session.releaseRecord()
		}
		if (!session.setPreviewSize(size.width, size.height, UvcPreviewFormat.MJPEG)) {
			restartPreview(session, previewView, shouldPrepareRecord)
			return false
		}
		val restarted = restartPreview(session, previewView, shouldPrepareRecord)
		if (restarted) {
			cameraOptions.previewSize = size
		}
		return restarted
	}

	private fun restartPreview(
		session: NativeUsbDevice,
		previewView: TextureView,
		shouldPrepareRecord: Boolean
	): Boolean {
		if (shouldPrepareRecord && !session.prepareRecord(mVideoFps)) {
			isVideoMode.postValue(false)
		}
		session.setDisplaySurface(previewView)
		val started = session.startPreview()
		if (started) {
			cameraOptions.applyParameters(session)
		}
		return started
	}
	
	override fun onCleared() {
		super.onCleared()
		cameraSupportOptions.clear()
		cameraOptions.clear()
		mPreviewViewRef = null
	}
}
