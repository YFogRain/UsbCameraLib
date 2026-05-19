package com.rain.uvc.demo.camera.dialog

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.viewModels
import com.google.android.material.slider.Slider
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.dialog.BaseBottomSheetDialogFragment
import com.rain.uvc.demo.camera.cpp.CameraViewModel
import com.rain.uvc.demo.databinding.DialogCamera2SettingsBinding
import com.rain.uvc.demo.utils.setViewShow
import com.rain.uvc.parameters.UvcCameraParameter
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
		val support = viewModel.cameraSupportOptions
		val options = viewModel.cameraOptions
		
		// 自动开关
		setupAutoToggle(
			mBinding.llAutoExposure, mBinding.switchAutoExposure,
			support.isSupportAutoExposure, options.isAutoExposure
		) {
			options.isAutoExposure = it
			viewModel.updateBoolParameter(UvcCameraParameter.AUTO_EXPOSURE, it)
		}
		
		setupAutoToggle(
			mBinding.llAutoFocus, mBinding.switchAutoFocus,
			support.isSupportAutoFocus, options.isAutoFocus
		) {
			options.isAutoFocus = it
			viewModel.updateBoolParameter(UvcCameraParameter.AUTO_FOCUS, it)
		}
		
		setupAutoToggle(
			mBinding.llAutoHue, mBinding.switchAutoHue,
			support.isSupportAutoHue, options.isAutoHue
		) {
			options.isAutoHue = it
			viewModel.updateBoolParameter(UvcCameraParameter.AUTO_HUE, it)
		}
		
		setupAutoToggle(
			mBinding.llAutoWhiteBalance, mBinding.switchAutoWhiteBalance,
			support.isSupportAutoWhiteBalance, options.isAutoWhiteBalance
		) {
			options.isAutoWhiteBalance = it
			viewModel.updateBoolParameter(UvcCameraParameter.AUTO_WHITE_BALANCE, it)
		}
		
		// 滑块参数
		setupSlider(
			mBinding.tvExposureTitle, mBinding.llExposure, mBinding.sliderExposure,
			mBinding.tvExposureMin, mBinding.tvExposureMax,
			support.exposureRange, options.exposure
		) {
			options.exposure = it
			viewModel.updateIntParameter(UvcCameraParameter.EXPOSURE, it)
		}
		
		setupSlider(
			mBinding.tvBrightnessTitle, mBinding.llBrightness, mBinding.sliderBrightness,
			mBinding.tvBrightnessMin, mBinding.tvBrightnessMax,
			support.brightnessRange, options.brightness
		) {
			options.brightness = it
			viewModel.updateIntParameter(UvcCameraParameter.BRIGHTNESS, it)
		}
		
		setupSlider(
			mBinding.tvContrastTitle, mBinding.llContrast, mBinding.sliderContrast,
			mBinding.tvContrastMin, mBinding.tvContrastMax,
			support.contrastRange, options.contrast
		) {
			options.contrast = it
			viewModel.updateIntParameter(UvcCameraParameter.CONTRAST, it)
		}
		
		setupSlider(
			mBinding.tvGainTitle, mBinding.llGain, mBinding.sliderGain,
			mBinding.tvGainMin, mBinding.tvGainMax,
			support.gainRange, options.gain
		) {
			options.gain = it
			viewModel.updateIntParameter(UvcCameraParameter.GAIN, it)
		}
		
		setupSlider(
			mBinding.tvSaturationTitle, mBinding.llSaturation, mBinding.sliderSaturation,
			mBinding.tvSaturationMin, mBinding.tvSaturationMax,
			support.saturationRange, options.saturation
		) {
			options.saturation = it
			viewModel.updateIntParameter(UvcCameraParameter.SATURATION, it)
		}
		
		setupSlider(
			mBinding.tvFocusTitle, mBinding.llFocus, mBinding.sliderFocus,
			mBinding.tvFocusMin, mBinding.tvFocusMax,
			support.focusRange, options.focus
		) {
			disableAutoModeIfNeeded(
				isAutoEnabled = options.isAutoFocus == true,
				switch = mBinding.switchAutoFocus
			) {
				options.isAutoFocus = false
				viewModel.updateBoolParameter(UvcCameraParameter.AUTO_FOCUS, false)
			}
			options.focus = it
			viewModel.updateIntParameter(UvcCameraParameter.FOCUS, it)
		}
		
		setupSlider(
			mBinding.tvIrisTitle, mBinding.llIris, mBinding.sliderIris,
			mBinding.tvIrisMin, mBinding.tvIrisMax,
			support.irisRange, options.iris
		) {
			options.iris = it
			viewModel.updateIntParameter(UvcCameraParameter.IRIS, it)
		}
		
		setupSlider(
			mBinding.tvHueTitle, mBinding.llHue, mBinding.sliderHue,
			mBinding.tvHueMin, mBinding.tvHueMax,
			support.hueRange, options.hue
		) {
			disableAutoModeIfNeeded(
				isAutoEnabled = options.isAutoHue == true,
				switch = mBinding.switchAutoHue
			) {
				options.isAutoHue = false
				viewModel.updateBoolParameter(UvcCameraParameter.AUTO_HUE, false)
			}
			options.hue = it
			viewModel.updateIntParameter(UvcCameraParameter.HUE, it)
		}
		
		setupSlider(
			mBinding.tvWhiteBalanceTitle, mBinding.llWhiteBalance, mBinding.sliderWhiteBalance,
			mBinding.tvWhiteBalanceMin, mBinding.tvWhiteBalanceMax,
			support.whiteBalanceRange, options.whiteBalance
		) {
			disableAutoModeIfNeeded(
				isAutoEnabled = options.isAutoWhiteBalance == true,
				switch = mBinding.switchAutoWhiteBalance
			) {
				options.isAutoWhiteBalance = false
				viewModel.updateBoolParameter(UvcCameraParameter.AUTO_WHITE_BALANCE, false)
			}
			options.whiteBalance = it
			viewModel.updateIntParameter(UvcCameraParameter.WHITE_BALANCE, it)
		}
	}

	private fun disableAutoModeIfNeeded(
		isAutoEnabled: Boolean,
		switch: SwitchCompat,
		onDisableAuto: () -> Unit
	) {
		if (!isAutoEnabled) return
		onDisableAuto()
		if (switch.isChecked) {
			switch.isChecked = false
		}
	}
	
	private fun setupAutoToggle(
		container: View,
		switch: SwitchCompat,
		isSupported: Boolean,
		currentValue: Boolean?,
		onChanged: (Boolean) -> Unit
	) {
		setViewShow(container, isSupported)
		if (!isSupported) return
		switch.isChecked = currentValue ?: false
		switch.setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
	}
	
	private fun setupSlider(
		titleView: View,
		container: View,
		slider: Slider,
		minText: TextView,
		maxText: TextView,
		range: IntRange?,
		currentValue: Int?,
		onChanged: (Int) -> Unit
	) {
		val isSupported = range != null && range.last > range.first
		setViewShow(titleView, isSupported)
		setViewShow(container, isSupported)
		if (range == null || range.last <= range.first) return
		
		minText.text = range.first.toString()
		maxText.text = range.last.toString()
		slider.valueFrom = range.first.toFloat()
		slider.valueTo = range.last.toFloat()
		slider.stepSize = 1f
		slider.value = (currentValue ?: range.first).toFloat()
			.coerceIn(range.first.toFloat(), range.last.toFloat())
		
		slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
			override fun onStartTrackingTouch(slider: Slider) {}
			override fun onStopTrackingTouch(slider: Slider) {
				onChanged(slider.value.toInt())
			}
		})
	}
}
