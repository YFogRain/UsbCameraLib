package com.rain.uvc.demo.camera

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import com.rain.uvc.demo.databinding.FgCameraSelectBinding
import com.rain.uvc.demo.utils.jumpNav
import com.rain.uvc.demo.utils.singleClick

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des 选择项
 */
class HomeSelectFragment : BaseDataBindFragment<FgCameraSelectBinding>() {
	override val mViewModel by viewModels<HomeSelectViewModel>()
	
	private val permissionCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (!it) {
			Toast.makeText(requireContext(), "请检查摄像头权限", Toast.LENGTH_SHORT).show()
			return@registerForActivityResult
		}
		mViewModel.loadDevice()
	}
	
	override fun loadLayoutResId(): Int = R.layout.fg_camera_select
	
	override fun initializeView(savedInstanceState: Bundle?) {
		initRec()
		initEvent()
	}
	
	override fun initializeData(savedInstanceState: Bundle?) {
		if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissionCall.launch(android.Manifest.permission.CAMERA)
			return
		}
		//在系统重建时
		mViewModel.loadDevice()
	}
	
	private fun initRec() {
		mBinding.recList.layoutManager = LinearLayoutManager(requireContext())
		mViewModel.adapter.setListener { groupPosition, childPosition ->
			itemClick(groupPosition, childPosition)
		}
		mBinding.recList.adapter = mViewModel.adapter
	}
	
	private fun initEvent() {
		mBinding.cardRefresh.singleClick { mViewModel.loadDevice() }
	}
	
	private fun itemClick(groupPosition: Int, childPosition: Int) {
		val item = mViewModel.adapter.getItem(groupPosition, childPosition) ?: return
		Log.d("HomeSelectFragment", "当前使用的类型:${item.deviceType}")
		when (val device = item.deviceType) {
			is CameraDeviceMode.Native1 -> jumpNav(R.id.camera_to_native1, "cameraId" to device.cameraId)
			is CameraDeviceMode.Native2 -> jumpNav(R.id.camera_to_native2, "cameraId" to device.cameraId)
			is CameraDeviceMode.USB -> jumpNav(R.id.camera_to_usb, "cameraId" to device.device)
			is CameraDeviceMode.V4L2 -> jumpNav(R.id.camera_to_v4l2, "cameraId" to device.videoPath)
		}
	}
}