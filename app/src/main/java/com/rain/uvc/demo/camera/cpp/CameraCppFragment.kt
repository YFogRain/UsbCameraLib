package com.rain.uvc.demo.camera.cpp

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.updateMargins
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.dialog.show
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.camera.dialog.CameraSettingsDialog
import com.rain.uvc.demo.databinding.FgCameraBinding
import com.rain.uvc.demo.utils.popStack
import com.rain.uvc.demo.utils.singleClick
import kotlin.math.max
import kotlin.math.min

/**
 * @author yuan
 * @createTime: 2025/6/12
 * @des
 */
class CameraCppFragment : BaseDataBindFragment<FgCameraBinding>() {
	override val viewModel by viewModels<CameraViewModel>()
	private val permissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		viewModel.loadImage()
	}
	
	// 缩放手势检测器
	private var scaleGestureDetector: ScaleGestureDetector? = null
	
	// 当前缩放比例 (0.0 - 1.0)
	private var currentZoomRatio = 0f
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera
	
	override fun initializeEnd(savedInstanceState: Bundle?) {
		setStatusBarTextColor(false)
		mBinding.toolbar.setNavigationOnClickListener {
			popStack()
		}
		val usbDevice = arguments?.getParcelable<UsbDevice>("usb_device")
		val videoPath = arguments?.getString("video_path")
		if (usbDevice == null && videoPath.isNullOrEmpty()) {
			return
		}
		
		viewModel.initCameraId(usbDevice, videoPath)
		initEvent()
		initZoomGesture()
	}
	
	/**
	 * 初始化缩放手势
	 */
	@SuppressLint("ClickableViewAccessibility")
	private fun initZoomGesture() {
		scaleGestureDetector = ScaleGestureDetector(
			requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
				override fun onScale(detector: ScaleGestureDetector): Boolean {
					val scaleFactor = detector.scaleFactor
					Log.d("CameraViewModel", "onScale = $scaleFactor")
					// 根据手势缩放调整 zoom 值
					// scaleFactor > 1 表示放大，< 1 表示缩小
					val delta = (scaleFactor - 1f) * 0.5f // 调整灵敏度
					currentZoomRatio = max(0f, min(1f, currentZoomRatio + delta))
					viewModel.setZoom(currentZoomRatio)
					return true
				}
			})
		
		// 为预览区域设置触摸监听
		mBinding.flSurface.setOnTouchListener { _, event ->
			scaleGestureDetector?.onTouchEvent(event) ?: false
		}
	}
	
	private fun initEvent() {
		// 拍照/录像按钮点击
		mBinding.icPicture.singleClick {
			viewModel.captureOrRecord()
		}
		
		// 切换为拍照模式
		mBinding.tvPhoto.singleClick {
			viewModel.updateMode(false)
		}
		
		// 切换为录像模式
		mBinding.tvVideo.singleClick {
			viewModel.updateMode(true)
		}
		
		// 设置按钮点击（右侧）
		mBinding.igSettings.singleClick {
			childFragmentManager.show<CameraSettingsDialog>("CameraSettingsDialog") {}
		}
		
		mBinding.cardPicture.singleClick {// 这里打开相册～
			val options = ActivityOptions.makeScaleUpAnimation(
				mBinding.icBitmap, 0, 0, mBinding.icBitmap.width, mBinding.icBitmap.height
			)
			try {
				requireContext().startActivity(Intent(Intent.ACTION_MAIN).apply {
					addCategory(Intent.CATEGORY_APP_GALLERY)
				}, options.toBundle())
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
	
	override fun onStart() {
		super.onStart()
		mBinding.surfaceView.post {
			if (!isAdded || isDetached) return@post
			viewModel.open(mBinding.surfaceView)
		}
		requestImagePermission()
	}
	
	override fun onStop() {
		super.onStop()
		viewModel.close()
	}
	
	override fun onKeyDown(): Boolean {
		popStack()
		return true
	}
	
	override fun initModelObserve() {
		viewModel.lastPicPreview.observe(viewLifecycleOwner) {
			if (it != null) {
				Glide.with(mBinding.icBitmap).load(it).circleCrop().into(mBinding.icBitmap)
			}
		}
		
		// 监听模式变化，更新UI
		viewModel.isVideoMode.observe(viewLifecycleOwner) { isVideo ->
			updateModeUI(isVideo)
		}
		
		// 监听录制状态，更新按钮图标和UI
		viewModel.recordIng.observe(viewLifecycleOwner) { isRecording ->
			updateRecordButtonUI(isRecording)
			updateRecordingUI(isRecording)
		}
		
		// 监听录制时间
		viewModel.recordTimeText.observe(viewLifecycleOwner) { timeText ->
			mBinding.tvRecordTime.text = timeText
		}
		
		// 监听缩放值
		viewModel.zoomText.observe(viewLifecycleOwner) { zoomText ->
			mBinding.tvZoom.text = zoomText
		}
	}
	
	/**
	 * 更新模式切换UI
	 */
	private fun updateModeUI(isVideo: Boolean) {
		if (isVideo) {
			// 录像模式：录像按钮高亮
			mBinding.tvVideo.setBackgroundResource(R.drawable.bg_type_selected)
			mBinding.tvPhoto.setBackgroundResource(0)
			// 默认显示录像图标
			if (viewModel.recordIng.value != true) {
				mBinding.icPicture.setImageResource(R.drawable.ic_video_record)
			}
		} else {
			// 拍照模式：拍照按钮高亮
			mBinding.tvPhoto.setBackgroundResource(R.drawable.bg_type_selected)
			mBinding.tvVideo.setBackgroundResource(0)
			mBinding.icPicture.setImageResource(R.drawable.ic_picture)
		}
	}
	
	/**
	 * 更新录制按钮UI
	 */
	private fun updateRecordButtonUI(isRecording: Boolean) {
		if (viewModel.isVideoMode.value == true) {
			mBinding.icPicture.setImageResource(
				if (isRecording) R.drawable.ic_video_stop else R.drawable.ic_video_record
			)
		}
	}
	
	/**
	 * 更新录制中的UI（显示时间，隐藏其他按钮）
	 */
	private fun updateRecordingUI(isRecording: Boolean) {
		if (isRecording) {
			// 显示录制时间
			mBinding.tvRecordTime.visibility = View.VISIBLE
			// 隐藏其他按钮
			mBinding.tvZoom.visibility = View.GONE
		} else {
			// 隐藏录制时间
			mBinding.tvRecordTime.visibility = View.GONE
			// 显示其他按钮
			mBinding.tvZoom.visibility = View.VISIBLE
		}
	}
	
	override fun initBottomHeight(bottom: Int): Int {
		val params = mBinding.cardType.layoutParams as ViewGroup.MarginLayoutParams
		params.updateMargins(
			params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + bottom
		)
		return 0
	}
	
	/**
	 * 获取权限
	 */
	fun requestImagePermission() {
		if (Build.VERSION.SDK_INT >= 33) {
			if (ContextCompat.checkSelfPermission(
					requireContext(), Manifest.permission.READ_MEDIA_IMAGES
				) != PackageManager.PERMISSION_GRANTED) {
				permissionResult.launch(Manifest.permission.READ_MEDIA_IMAGES)
				return
			}
			
		} else {
			if (ContextCompat.checkSelfPermission(
					requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
				) != PackageManager.PERMISSION_GRANTED) {
				permissionResult.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
				return
			}
		}
		viewModel.loadImage()
	}
}