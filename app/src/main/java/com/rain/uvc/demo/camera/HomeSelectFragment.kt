package com.rain.uvc.demo.camera

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.updateMargins
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCameraSelectBinding
import com.rain.uvc.demo.utils.LanguageHelper
import com.rain.uvc.demo.utils.jumpNav
import com.rain.uvc.demo.utils.singleClick
import kotlin.getValue

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des 选择项
 */
class HomeSelectFragment : BaseDataBindFragment<FgCameraSelectBinding>() {
	override val viewModel by viewModels<HomeSelectViewModel>()
	
	private val permissionCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (!it) {
			Toast.makeText(requireContext(), "请检查摄像头权限", Toast.LENGTH_SHORT).show()
			return@registerForActivityResult
		}
	}
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera_select
	
	override fun initializeEnd(savedInstanceState: Bundle?) {
		Log.d("HomeSelectFragment", "执行初始化～～")
		setStatusBarTextColor(true)
		initRec()
		initEvent()
	}
	
	private fun initRec() {
		mBinding.recList.layoutManager = LinearLayoutManager(requireContext())
		viewModel.adapter.setListener { groupPosition, childPosition ->
			itemClick(groupPosition, childPosition)
		}
		mBinding.recList.adapter = viewModel.adapter
	}
	
	private fun initEvent() {
		mBinding.cardRefresh.singleClick { viewModel.loadDevice() }
	}
	
	private fun itemClick(groupPosition: Int, childPosition: Int) {
		// 如果没有权限，则去申请权限
		if (ContextCompat.checkSelfPermission(
				requireContext(), android.Manifest.permission.CAMERA
			) != PackageManager.PERMISSION_GRANTED) {
			permissionCall.launch(android.Manifest.permission.CAMERA)
			return
		}
		val item = viewModel.adapter.getItem(groupPosition, childPosition) ?: return
		Log.d("HomeSelectFragment", "当前使用的类型:${item.deviceType}")
		when (val device = item.deviceType) {
//			is CameraDeviceMode.Native1 -> jumpNav(
//				R.id.camera_to_native1, "cameraId" to device.cameraId
//			)
//			is CameraDeviceMode.Native2 -> jumpNav(
//				R.id.camera_to_native2, "cameraId" to device.cameraId
//			)
			is CameraDeviceMode.USB -> jumpNav(R.id.camera_to_cpp, "usb_device" to device.device)
			is CameraDeviceMode.V4L2 -> jumpNav(
				R.id.camera_to_cpp, "video_path" to device.videoPath
			)
			is CameraDeviceMode.LOCALE -> {
				Log.d("HomeSelectFragment", "当前语言类型:${device.locale.toLanguageTag()}")
				LanguageHelper.updateLocale(requireContext(), device.locale)
				val navController = findNavController()
				navController.popBackStack(navController.currentDestination?.id ?: 0, true)
				navController.navigate(navController.graph.startDestinationId)
			}
			else -> {}
		}
	}
	
	override fun initBottomHeight(bottom: Int): Int {
		val params = mBinding.recList.layoutParams as ViewGroup.MarginLayoutParams
		params.updateMargins(
			params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin + bottom
		)
		val refreshParams = mBinding.cardRefresh.layoutParams as ViewGroup.MarginLayoutParams
		refreshParams.updateMargins(
			refreshParams.leftMargin,
			refreshParams.topMargin,
			refreshParams.rightMargin,
			refreshParams.bottomMargin + bottom
		)
		return 0
	}
}