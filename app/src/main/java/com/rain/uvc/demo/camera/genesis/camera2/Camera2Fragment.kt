package com.rain.uvc.demo.camera.genesis.camera2

import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCamera2Binding
import com.rain.uvc.demo.provider.OverallContext
import com.rain.uvc.demo.utils.popStack
import com.rain.uvc.demo.utils.singleClick
import com.rain.uvc.demo.utils.viewLifeScope
import kotlinx.coroutines.launch

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des usb类型相机代码
 */
class Camera2Fragment : BaseDataBindFragment<FgCamera2Binding, CameraViewModel>() {
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera_2
	
	override fun initializeCreated(savedInstanceState: Bundle?) {
		setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.black))
		setStatusBarTextColor(false)
		mBinding.toolbar.setNavigationOnClickListener {
			popStack()
		}
		if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(requireContext(), "请检查摄像头权限", Toast.LENGTH_SHORT).show()
			return
		}
		mBinding.cardCapture.singleClick {
			viewModel.takePicture()
		}
		mBinding.cardRecorder.singleClick {
			viewModel.recorder()
		}
		mBinding.surfaceView.post { open() }
	}
	
	
	private fun open() {
		val cameraId = arguments?.getString("cameraId")
		if (!cameraId.isNullOrEmpty()) {
			viewModel.openCamera(requireContext(),cameraId)
			return
		}
		Toast.makeText(OverallContext.baseContext, "请传入正确的摄像头id", Toast.LENGTH_SHORT).show()
	}
	
	override fun initModelObserve() {
		viewLifeScope.launch {
			viewModel.openResultFlow.collect {
				resultOpen(message = it)
			}
		}
		viewModel.recordState.observe(viewLifecycleOwner) {
			mBinding.tvRecorder.text = if (it) "停止录制" else "开始录制"
		}
	}
	
	private fun resultOpen(message: String?) {
		if (!message.isNullOrEmpty()) {
			Toast.makeText(requireContext(), "打开摄像头失败，原因:$message", Toast.LENGTH_SHORT).show()
			return
		}
		viewModel.initPreview(mBinding.surfaceView)
		viewModel.startPreview()
	}
	
	override fun onStart() {
		super.onStart()
		viewModel.startPreview()
	}
	
	override fun onStop() {
		super.onStop()
		viewModel.stopPreview()
	}
	
	override fun onDestroyView() {
		viewModel.closeCamera()
		super.onDestroyView()
	}
	override fun onKeyDown(): Boolean {
		popStack()
		return true
	}
}