package com.rain.uvc.demo.camera.dialog

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.adapter.BaseRecAdapter
import com.rain.uvc.demo.base.adapter.BaseRecHolder
import com.rain.uvc.demo.base.dialog.BaseBottomSheetDialogFragment
import com.rain.uvc.demo.camera.cpp.CameraViewModel
import com.rain.uvc.demo.camera.supportedPreviewSizes
import com.rain.uvc.demo.databinding.DialogCameraPreviewSettingsBinding
import com.rain.uvc.demo.databinding.ItemCameraSettingsViewBinding
import com.rain.uvc.demo.databinding.ItemPicSettingsSizeBinding
import com.rain.uvc.mode.UvcCameraSize
import com.rain.uvc.demo.utils.setViewShow
import kotlin.getValue

class CameraPreviewSettingsDialog : BaseBottomSheetDialogFragment<DialogCameraPreviewSettingsBinding>() {
	override fun loadLayoutResId(): Int = R.layout.dialog_camera_preview_settings
	override fun isNotInitViewModel(): Boolean = true
	override val viewModel by viewModels<CameraViewModel>(ownerProducer = { requireParentFragment() })

	private val orientationAdapter = PreviewOptionAdapter<Int>(R.layout.item_camera_settings_view)
	private val previewSizeAdapter = PreviewOptionAdapter<UvcCameraSize>(R.layout.item_pic_settings_size)

	override fun initializeEnd(savedInstanceState: Bundle?) {
		initMirrorSetting()
		initOrientationSetting()
		initPreviewSizeSetting()
	}

	private fun initMirrorSetting() {
		mBinding.switchMirror.setOnCheckedChangeListener(null)
		mBinding.switchMirror.isChecked = viewModel.cameraOptions.mPicMirrorState
		mBinding.switchMirror.setOnCheckedChangeListener(mirrorCheckedChangeListener)
	}

	private fun initOrientationSetting() {
		mBinding.recOrientation.layoutManager = LinearLayoutManager(
			requireContext(), RecyclerView.HORIZONTAL, false
		)
		mBinding.recOrientation.adapter = orientationAdapter
		orientationAdapter.setOnItemClickListener {
			val option = orientationAdapter.getItem(it) ?: return@setOnItemClickListener
			if (orientationAdapter.isSelected(option.value)) return@setOnItemClickListener
			if (!viewModel.updateDisplayOrientation(option.value)) {
				Toast.makeText(requireContext(), "方向切换失败", Toast.LENGTH_SHORT).show()
				return@setOnItemClickListener
			}
			orientationAdapter.updateSelection(option.value)
		}
		orientationAdapter.submitOptions(
			ORIENTATION_OPTIONS,
			viewModel.cameraOptions.mPicOrientation
		)
	}

	private fun initPreviewSizeSetting() {
		val supportedSizes = viewModel.cameraSupportOptions.previewSizes?.supportedPreviewSizes().orEmpty()
		val hasPreviewSize = supportedSizes.isNotEmpty()
		setViewShow(mBinding.tvPreviewSizeTitle, hasPreviewSize)
		setViewShow(mBinding.recPreviewSize, hasPreviewSize)
		if (!hasPreviewSize) return
		mBinding.recPreviewSize.layoutManager = LinearLayoutManager(requireContext())
		mBinding.recPreviewSize.adapter = previewSizeAdapter
		previewSizeAdapter.setOnItemClickListener {
			val option = previewSizeAdapter.getItem(it) ?: return@setOnItemClickListener
			if (previewSizeAdapter.isSelected(option.value)) return@setOnItemClickListener
			if (viewModel.recordIng.value == true) {
				Toast.makeText(requireContext(), "录制中无法切换分辨率", Toast.LENGTH_SHORT).show()
				return@setOnItemClickListener
			}
			if (!viewModel.updatePreviewSize(option.value)) {
				Toast.makeText(requireContext(), "分辨率切换失败", Toast.LENGTH_SHORT).show()
				return@setOnItemClickListener
			}
			previewSizeAdapter.updateSelection(option.value)
		}
		previewSizeAdapter.submitOptions(
			supportedSizes.map {
				PreviewSettingOption(it, "${it.width} x ${it.height}")
			},
			viewModel.cameraOptions.previewSize
		)
	}

	private val mirrorCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
		if (viewModel.updateMirrorState(isChecked)) return@OnCheckedChangeListener
		Toast.makeText(requireContext(), "镜像切换失败", Toast.LENGTH_SHORT).show()
		initMirrorSetting()
	}
}

private val ORIENTATION_OPTIONS = listOf(
	PreviewSettingOption(0, "0 deg"),
	PreviewSettingOption(90, "90 deg"),
	PreviewSettingOption(180, "180 deg"),
	PreviewSettingOption(270, "270 deg"),
)

private data class PreviewSettingOption<T>(
	val value: T,
	val label: String,
)

private class PreviewOptionAdapter<T>(
	private val itemLayoutResId: Int,
) : BaseRecAdapter<PreviewSettingOption<T>>() {
	private companion object {
		const val PAYLOAD_SELECTION = "selection"
	}

	private var selectedValue: T? = null

	override fun getLayoutResId(viewType: Int): Int = itemLayoutResId

	override fun getVariableId(viewType: Int): Int = -1

	fun submitOptions(options: List<PreviewSettingOption<T>>, selectedValue: T?) {
		this.selectedValue = selectedValue
		setItems(options.toMutableList())
	}

	fun isSelected(value: T): Boolean = selectedValue == value

	fun updateSelection(value: T): Boolean {
		if (selectedValue == value) return false
		selectedValue = value
		notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
		return true
	}

	override fun collectHolder(
		holder: BaseRecHolder<PreviewSettingOption<T>, *>,
		mode: PreviewSettingOption<T>,
		position: Int
	) {
		bindItem(holder.mBinding, mode)
	}

	override fun collectHolder(
		holder: BaseRecHolder<PreviewSettingOption<T>, *>,
		mode: PreviewSettingOption<T>,
		position: Int,
		payload: Any
	) {
		if (payload == PAYLOAD_SELECTION) {
			bindSelectedState(holder.mBinding, mode)
			return
		}
		bindItem(holder.mBinding, mode)
	}

	private fun bindItem(binding: ViewDataBinding, mode: PreviewSettingOption<T>) {
		when (binding) {
			is ItemCameraSettingsViewBinding -> {
				binding.tvSize.text = mode.label
			}

			is ItemPicSettingsSizeBinding -> {
				binding.tvSize.text = mode.label
			}
		}
		bindSelectedState(binding, mode)
	}

	private fun bindSelectedState(binding: ViewDataBinding, mode: PreviewSettingOption<T>) {
		val selected = selectedValue == mode.value
		when (binding) {
			is ItemCameraSettingsViewBinding -> applySelectedState(
				binding.root,
				binding.ivChoose,
				binding.tvSize,
				selected
			)

			is ItemPicSettingsSizeBinding -> applySelectedState(
				binding.root,
				binding.ivChoose,
				binding.tvSize,
				selected
			)
		}
	}

	private fun applySelectedState(
		root: View,
		indicator: View,
		label: View,
		selected: Boolean
	) {
		indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
		label.alpha = if (selected) 1f else 0.72f
		root.alpha = if (selected) 1f else 0.9f
	}
}
