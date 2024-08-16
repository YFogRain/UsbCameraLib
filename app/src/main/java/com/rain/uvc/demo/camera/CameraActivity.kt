package com.rain.uvc.demo.camera

import androidx.activity.viewModels
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.activity.BaseDataBindActivity
import com.rain.uvc.demo.databinding.ActivityCameraBinding
import com.rain.uvc.demo.utils.singleClick

/**
 */
class CameraActivity : BaseDataBindActivity<ActivityCameraBinding>() {
	override val mViewModel by viewModels<CameraViewModel>()
	override fun initLayoutResId(): Int = R.layout.activity_camera
	
	override fun init() {
		super.init()
		mBinding.surfaceView.post {
			mViewModel.openCamera {
				if (it) mViewModel.startPreview(mBinding.surfaceView.holder.surface)
			}
		}
		mBinding.tvDisplay.singleClick {
			mViewModel.setDisplay()
		}
		mBinding.tvPreviewSize.singleClick {
			mViewModel.updatePreviewSize()
		}
	}
	
	override fun onStop() {
		super.onStop()
		mViewModel.stopPreview()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		mViewModel.destroyCamera()
	}
	
}