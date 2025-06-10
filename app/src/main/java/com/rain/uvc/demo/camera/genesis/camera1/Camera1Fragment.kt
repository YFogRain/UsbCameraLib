package com.rain.uvc.demo.camera.genesis.camera1

import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
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
class Camera1Fragment : BaseDataBindFragment<FgCameraBinding, CameraViewModel>() {
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera
	
	override fun onKeyDown(): Boolean {
		popStack()
		return true
	}
	
	override fun initializeCreated(savedInstanceState: Bundle?) {
		setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.black))
		setStatusBarTextColor(false)
		val cameraId = arguments?.getInt("cameraId")
		if (cameraId == null) {
			Toast.makeText(requireContext(), "未输入id", Toast.LENGTH_SHORT).show()
			popStack()
			return
		}
		mBinding.surfaceView.outlineProvider = object : ViewOutlineProvider() {
			override fun getOutline(view: View, outline: Outline) {
				outline.setOval(0, 0, view.width, view.height)
			}
		}
		mBinding.surfaceView.clipToOutline = true
		mBinding.surfaceView.post {
			viewModel.openCamera(cameraId) {
				viewModel.startPreview(mBinding.surfaceView)
			}
		}
	}
}