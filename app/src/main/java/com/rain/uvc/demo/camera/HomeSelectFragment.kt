package com.rain.uvc.demo.camera

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
//		if (!checkManageExternal()) {
//			externalPerResult.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
//				it.data = Uri.fromParts("package", OverallContext.baseContext.packageName, null)
//			})
//			return@registerForActivityResult
//		}
		viewModel.loadDevice()
	}
	private val externalPerResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
		if (!checkManageExternal()) {
			Toast.makeText(requireContext(), "请检查文件权限", Toast.LENGTH_SHORT).show()
			return@registerForActivityResult
		}
		// 悬浮窗权限检查
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
//		if (!checkManageExternal()) {
//			 externalPerResult.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).also {
//				 it.data = Uri.fromParts("package", OverallContext.baseContext.packageName, null)
//			 })
//			return
//		}
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
		mBinding.cardRefresh.singleClick { viewModel.loadDevice() }
	}
	
	private fun itemClick(groupPosition: Int, childPosition: Int) {
		val item = viewModel.adapter.getItem(groupPosition, childPosition) ?: return
		Log.d("HomeSelectFragment", "当前使用的类型:${item.deviceType}")
		when (val device = item.deviceType) {
			is CameraDeviceMode.Native1 -> jumpNav(R.id.camera_to_native1, "cameraId" to device.cameraId)
			is CameraDeviceMode.Native2 -> jumpNav(R.id.camera_to_native2, "cameraId" to device.cameraId)
			is CameraDeviceMode.OTHER -> jumpNav(R.id.camera_to_usb)
			is CameraDeviceMode.USB -> jumpNav(R.id.camera_to_cpp, "usb_device" to device.device)
			is CameraDeviceMode.V4L2 -> jumpNav(R.id.camera_to_cpp, "video_path" to device.videoPath)
			is CameraDeviceMode.LOCALE -> {
				Log.d("HomeSelectFragment", "当前语言类型:${device.locale.toLanguageTag()}")
				LanguageHelper.updateLocale(requireContext(), device.locale)
				val navController = findNavController()
				navController.popBackStack(navController.currentDestination?.id ?: 0, true)
				navController.navigate(navController.graph.startDestinationId)
			}
		}
	}
	
	fun checkManageExternal(): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return true
		}
		return Environment.isExternalStorageManager()
	}
}