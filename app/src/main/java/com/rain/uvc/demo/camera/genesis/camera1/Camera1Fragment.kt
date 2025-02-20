package com.rain.uvc.demo.camera.genesis.camera1

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.databinding.FgCameraBinding
import com.rain.uvc.demo.utils.popStack

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des usb类型相机代码
 */
class Camera1Fragment : BaseDataBindFragment<FgCameraBinding>() {
	override val mViewModel by viewModels<CameraViewModel>()
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera
	
	override fun onKeyDown(): Boolean {
		popStack()
		return true
	}
	
	override fun initializeView(savedInstanceState: Bundle?) {
		val cameraId = arguments?.getInt("cameraId")
		if (cameraId == null) {
			Toast.makeText(requireContext(), "未输入id", Toast.LENGTH_SHORT).show()
			popStack()
			return
		}
		mBinding.surfaceView.post {
			mViewModel.openCamera(cameraId) {
				mViewModel.startPreview(mBinding.surfaceView)
			}
		}
	}
}