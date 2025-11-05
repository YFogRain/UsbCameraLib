package com.rain.uvc.demo.camera.genesis.camera2

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCamera2Binding
import com.rain.uvc.demo.utils.popStack
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
		val cameraId = arguments?.getString("cameraId")
		if (cameraId.isNullOrEmpty()) {
			Toast.makeText(requireContext(), "未输入id", Toast.LENGTH_SHORT).show()
			popStack()
			return
		}
		CameraBusHelper.helper.open(requireContext()) { isSuccess, message ->
			Log.d("cameraPreviewTag", "打开结果 = $isSuccess : $message")
			if (!isSuccess) return@open
			startPreview()
		}
	}
	
	private fun startPreview() {
		CameraBusHelper.helper.setPreviewListener { bytes, width, height ->
			Log.d("cameraPreviewTag", "预览结果 - bytes = ${bytes.size} = ${width}*${height}")
		}
		lifecycleScope.launch {
			val startPreview = CameraBusHelper.helper.startPreview()
			Log.d("cameraPreviewTag", "预览开启结果 = $startPreview")
			if (!startPreview) {
				CameraBusHelper.helper.close()
			}
		}
	}
	
	override fun onDestroyView() {
		super.onDestroyView()
		CameraBusHelper.helper.stopPreview()
		CameraBusHelper.helper.close()
	}
}