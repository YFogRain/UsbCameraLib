package com.rain.uvc.demo.camera.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.rain.camera.uvc.mode.UvcCameraSize
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.adapter.BaseRecAdapter
import com.rain.uvc.demo.base.adapter.BaseRecHolder
import com.rain.uvc.demo.base.dialog.BaseBottomSheetDialogFragment
import com.rain.uvc.demo.camera.cpp.CameraViewModel
import com.rain.uvc.demo.databinding.DialogPicSettingsBinding
import com.rain.uvc.demo.databinding.ItemPicSettingsSizeBinding

/**
 * @author yuan
 * @createTime: 2026/5/12
 * @des 照片等设置
 */
class PicSettingsDialog : BaseBottomSheetDialogFragment<DialogPicSettingsBinding>() {
	override fun loadLayoutResId(): Int = R.layout.dialog_pic_settings
	override fun isNotInitViewModel(): Boolean = true
	override val viewModel by viewModels<CameraViewModel>(ownerProducer = { requireParentFragment() })
	override fun initializeEnd(savedInstanceState: Bundle?) {
		initView()
	}
	
	@SuppressLint("SetTextI18n")
	private fun initView() {
		mBinding.tvTitle.text = "${if (viewModel.isVideoMode.value != true) "照片" else "录像"}设置"
		if (viewModel.isVideoMode.value != true) {
			mBinding.rgVideoFps.visibility = View.GONE
			mBinding.tvFpsTitle.visibility = View.GONE
			return
		}
		mBinding.rgVideoFps.visibility = View.VISIBLE
		mBinding.tvFpsTitle.visibility = View.VISIBLE
		mBinding.rgVideoFps.setOnCheckedChangeListener { _, checkedId ->
			val fps = when (checkedId) {
				R.id.rb_fps_30 -> 30
				R.id.rb_fps_60 -> 60
				else -> 30
			}
			viewModel.updateVideoFps(fps)
		}
		mBinding.rgVideoFps.check(
			when (viewModel.cameraOptions.mVideoFps) {
				60 -> R.id.rb_fps_60
				else -> R.id.rb_fps_30
			}
		)
	}
}

class PicSettingsSizeAdapter : BaseRecAdapter<UvcCameraSize>() {
	private var mCurrentSize: UvcCameraSize? = null
	override fun getLayoutResId(viewType: Int): Int = R.layout.item_pic_settings_size
	
	override fun getVariableId(viewType: Int): Int = -1
	
	fun setItems(data: MutableList<UvcCameraSize>?, selectSize: UvcCameraSize?) {
		this.mCurrentSize = selectSize
		super.setItems(data)
	}
	
	fun updateItem(mode: UvcCameraSize): Boolean {
		val selectState = mCurrentSize?.let {
			it.width == mode.width && it.height == mode.height
		} ?: false
		if (selectState) return false
		this.mCurrentSize = mode
		notifyItemRangeChanged(0, itemCount, "update_select")
		return true
	}
	
	@SuppressLint("SetTextI18n")
	override fun collectHolder(holder: BaseRecHolder<UvcCameraSize, *>, mode: UvcCameraSize, position: Int) {
		val binding = holder.mBinding
		if (binding !is ItemPicSettingsSizeBinding) return
		binding.tvSize.text = "${mode.width}*${mode.height}"
		
		val selectState = mCurrentSize?.let {
			it.width == mode.width && it.height == mode.height
		} ?: false
		binding.ivChoose.setImageResource(if (selectState) R.drawable.ic_current else R.drawable.ic_choose)
	}
	
	override fun collectHolder(holder: BaseRecHolder<UvcCameraSize, *>, mode: UvcCameraSize, position: Int, payload: Any) {
		val binding = holder.mBinding
		if (binding !is ItemPicSettingsSizeBinding) return
		if (payload == "update_select") {
			val selectState = mCurrentSize?.let {
				it.width == mode.width && it.height == mode.height
			} ?: false
			binding.ivChoose.setImageResource(if (selectState) R.drawable.ic_current else R.drawable.ic_choose)
		}
	}
	
}