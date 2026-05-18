package com.rain.uvc.demo.camera.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.adapter.BaseRecAdapter
import com.rain.uvc.demo.base.adapter.BaseRecHolder
import com.rain.uvc.demo.base.dialog.BaseBottomSheetDialogFragment
import com.rain.uvc.demo.camera.CameraOptionSelect
import com.rain.uvc.demo.camera.cpp.CameraViewModel
import com.rain.uvc.demo.databinding.DialogCamera2SettingsBinding
import com.rain.uvc.demo.databinding.ItemCameraSettingsViewBinding
import com.rain.uvc.demo.utils.setViewShow
import kotlin.getValue

/**
 * @author yuan
 * @createTime: 2026/5/12
 * @des camera相机设置类
 */
class CameraSettingsDialog : BaseBottomSheetDialogFragment<DialogCamera2SettingsBinding>() {
	override fun loadLayoutResId(): Int = R.layout.dialog_camera2_settings
	override fun isNotInitViewModel(): Boolean = true
	override val viewModel by viewModels<CameraViewModel>(ownerProducer = { requireParentFragment() })
	override fun initializeEnd(savedInstanceState: Bundle?) {
		initView()
		
		setViewShow(mBinding.llBrightness, viewModel.cameraSupportOptions.brightnessRange != null)
		setViewShow(
			mBinding.tvBrightnessTitle, viewModel.cameraSupportOptions.brightnessRange != null
		)
		
//		val isNotHaveWhiteBalance = viewModel.cameraSupportOptions.whiteBalanceSupportList.isNullOrEmpty()
//		setViewShow(mBinding.recWhiteBalance, !isNotHaveWhiteBalance)
//		setViewShow(mBinding.tvWhiteBalanceTitle, !isNotHaveWhiteBalance)
	}
	
	private fun initView() {
		mBinding.recWhiteBalance.layoutManager = LinearLayoutManager(
			requireContext(), RecyclerView.HORIZONTAL, false
		)
		mBinding.recWhiteBalance.adapter = CameraSettingsAdapter().apply {
			setOnItemClickListener {
				val item = getItem(it) ?: return@setOnItemClickListener
				if (!updateItem(item.value)) return@setOnItemClickListener
//				viewModel.updateWhiteBalance(item.value)
			}
//			setItems(
//				viewModel.cameraSupportOptions.whiteBalanceSupportList,
//				viewModel.cameraOptions.mCurrentWhiteBalance
//			)
		}
		mBinding.sliderBrightness.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
			override fun onStartTrackingTouch(slider: Slider) {
			}
			
			override fun onStopTrackingTouch(slider: Slider) { // 停止滑动时修改亮度
				val brightnessRange = viewModel.cameraSupportOptions.brightnessRange ?: return
				
				val brightnessProcess = (slider.value / 100f) // 亮度百分比
				// 计算亮度
				val brightness = (brightnessRange.first + (brightnessRange.last - brightnessRange.first) * brightnessProcess).toInt()
				Log.d("CameraSettingsDialog", "修改亮度值 = $brightness")
				viewModel.updateBrightness(brightness)
			}
		})
	}
}

class CameraSettingsAdapter : BaseRecAdapter<CameraOptionSelect>() {
	private var mCurrentMode: String? = null
	override fun getLayoutResId(viewType: Int): Int = R.layout.item_camera_settings_view
	
	override fun getVariableId(viewType: Int): Int = -1
	
	fun setItems(data: MutableList<CameraOptionSelect>?, mode: String?) {
		this.mCurrentMode = mode
		super.setItems(data)
	}
	
	fun updateItem(mode: String): Boolean {
		if (!mCurrentMode.isNullOrEmpty() && mode == mCurrentMode) return false
		this.mCurrentMode = mode
		notifyItemRangeChanged(0, itemCount, "update_select")
		return true
	}
	
	@SuppressLint("SetTextI18n")
	override fun collectHolder(holder: BaseRecHolder<CameraOptionSelect, *>, mode: CameraOptionSelect, position: Int) {
		val binding = holder.mBinding
		if (binding !is ItemCameraSettingsViewBinding) return
		binding.tvSize.text = mode.name
		
		binding.ivChoose.setImageResource(if (mCurrentMode.let {
				!it.isNullOrEmpty() && it == mode.value
			}) R.drawable.ic_current else R.drawable.ic_choose)
	}
	
	override fun collectHolder(holder: BaseRecHolder<CameraOptionSelect, *>, mode: CameraOptionSelect, position: Int, payload: Any) {
		val binding = holder.mBinding
		if (binding !is ItemCameraSettingsViewBinding) return
		if (payload == "update_select") {
			binding.ivChoose.setImageResource(if (mCurrentMode.let {
					!it.isNullOrEmpty() && it == mode.value
				}) R.drawable.ic_current else R.drawable.ic_choose)
		}
	}
	
}