package com.rain.uvc.demo.camera.usb

import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCameraUsbBinding
import com.rain.uvc.demo.utils.viewLifeScope
import kotlinx.coroutines.launch

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des usb类型相机代码
 */
class CameraUsbFragment : BaseDataBindFragment<FgCameraUsbBinding, CameraViewModel>() {
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera_usb
	override fun initializeCreated(savedInstanceState: Bundle?) {
		setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.black))
		setStatusBarTextColor(false)
		mBinding.surfaceViewColor.post {
			viewModel.openCamera()
		}
	}
	
	override fun initModelObserve() {
		viewLifeScope.launch {
			viewModel.openResultFlow.collect {
				resultOpen(it)
			}
		}
	}
	
	private fun resultOpen(message: String?) {
		if (!message.isNullOrEmpty()) {
			Toast.makeText(requireContext(), "打开摄像头失败，原因:$message", Toast.LENGTH_SHORT).show()
			return
		}
		viewModel.setDisplaySurface(mBinding.surfaceViewColor, mBinding.surfaceViewIr)
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
		viewModel.close()
		super.onDestroyView()
	}
}