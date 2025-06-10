package com.rain.uvc.demo.camera.v4l2

import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCameraBinding
import com.rain.uvc.demo.utils.popStack

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des usb类型相机代码
 */
class CameraV4L2Fragment : BaseDataBindFragment<FgCameraBinding, CameraViewModel>() {
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera
	
	override fun initializeCreated(savedInstanceState: Bundle?) {
		setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.black))
		setStatusBarTextColor(false)
		val cameraId = arguments?.getString("cameraId")
		if (cameraId.isNullOrEmpty()) {
			Toast.makeText(requireContext(), "未输入id", Toast.LENGTH_SHORT).show()
			popStack()
			return
		}
		mBinding.surfaceView.post {
			viewModel.openCamera(cameraId) {
				viewModel.startPreview(mBinding.surfaceView)
			}
		}
	}
}

