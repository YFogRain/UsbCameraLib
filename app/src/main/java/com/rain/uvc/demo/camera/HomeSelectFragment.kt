package com.rain.uvc.demo.camera

import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCameraSelectBinding
import com.rain.uvc.demo.utils.LanguageHelper
import com.rain.uvc.demo.utils.jumpNav
import com.rain.uvc.demo.utils.singleClick
import com.rain.uvc.utils.CameraNativeUtils
import java.io.File

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des 选择项
 */
class HomeSelectFragment : BaseDataBindFragment<FgCameraSelectBinding, HomeSelectViewModel>() {
	
	private val permissionCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (!it) {
			Toast.makeText(requireContext(), "请检查摄像头权限", Toast.LENGTH_SHORT).show()
			return@registerForActivityResult
		}
		viewModel.loadDevice()
	}
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera_select
	
	override fun initializeCreated(savedInstanceState: Bundle?) {
		setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.white))
		setStatusBarTextColor(true)
		initRec()
		initEvent()
	}
	
	override fun initializeFirstCreated(savedInstanceState: Bundle?) {
		if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissionCall.launch(android.Manifest.permission.CAMERA)
			return
		}
		//在系统重建时
		viewModel.loadDevice()
	}
	
	private fun initRec() {
		mBinding.recList.layoutManager = LinearLayoutManager(requireContext())
		viewModel.adapter.setListener { groupPosition, childPosition ->
			itemClick(groupPosition, childPosition)
		}
		mBinding.recList.adapter = viewModel.adapter
	}
	
	private fun initEvent() {
	}
	
	private fun itemClick(groupPosition: Int, childPosition: Int) {
		val item = viewModel.adapter.getItem(groupPosition, childPosition) ?: return
		Log.d("HomeSelectFragment", "当前使用的类型:${item.deviceType}")
		when (val device = item.deviceType) {
			is CameraDeviceMode.Native1 -> jumpNav(R.id.camera_to_native1, "cameraId" to device.cameraId)
			is CameraDeviceMode.Native2 -> jumpNav(R.id.camera_to_native2, "cameraId" to device.cameraId)
			is CameraDeviceMode.USB -> jumpNav(R.id.camera_to_usb, "cameraId" to device.device)
			is CameraDeviceMode.V4L2 -> jumpNav(R.id.camera_to_v4l2, "cameraId" to device.videoPath)
			is CameraDeviceMode.LOCALE -> {
				Log.d("HomeSelectFragment", "当前语言类型:${device.locale.toLanguageTag()}")
				LanguageHelper.updateLocale(requireContext(), device.locale)
				val navController = findNavController()
				navController.popBackStack(navController.currentDestination?.id ?: 0, true)
				navController.navigate(navController.graph.startDestinationId)
//				val intent = requireActivity().intent
//
//				// 结束当前 Activity，并重新启动
//				requireActivity().finish()
//				requireContext().startActivity(intent)
				// 使用动画淡入淡出（可选）
//				requireActivity().overridePendingTransition(0, 0)
			}
		}
	}
}