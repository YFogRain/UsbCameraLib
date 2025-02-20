package com.rain.uvc.demo.camera

import android.content.Context
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import androidx.lifecycle.viewModelScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.provider.OverallContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des 选择器的类
 */
class HomeSelectViewModel : BaseViewModel() {
	
	val adapter by lazy { HomeSelectAdapter() }
	
	fun loadDevice() {
		//获取相机参数的所有信息
		adapter.clear()
		viewModelScope.launch(Dispatchers.IO) {
			//获取系统api1的数据
			val deviceList = mutableListOf<HomeSelectMode>()
			deviceList.add(loadCamera1())
			deviceList.add(loadCamera2())
			deviceList.add(loadCameraUsb())
			deviceList.add(loadCameraV4L2())
			withContext(Dispatchers.Main) {
				adapter.setData(deviceList)
			}
		}
	}
	
	private fun loadCamera1(): HomeSelectMode {
		val homeSelectMode = HomeSelectMode("系统相机API-1", mutableListOf())
		val numberOfCameras = runCatching { Camera.getNumberOfCameras() }.getOrNull() ?: 0
		if (numberOfCameras == 0) return homeSelectMode
		for (i in 0 until numberOfCameras) {
			homeSelectMode.devices.add(HomeDeviceMode("系统相机-$i", CameraDeviceMode.Native1(i)))
		}
		return homeSelectMode
	}
	
	private fun loadCamera2(): HomeSelectMode {
		val homeSelectMode = HomeSelectMode("系统相机API-2", mutableListOf())
		val cameraManager = OverallContext.baseContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
		val cameraIds = runCatching { cameraManager.cameraIdList }.getOrNull()
		if (cameraIds.isNullOrEmpty()) return homeSelectMode
		cameraIds.forEach {
			homeSelectMode.devices.add(HomeDeviceMode("系统相机-$it", CameraDeviceMode.Native2(it)))
		}
		return homeSelectMode
	}
	
	private fun loadCameraUsb(): HomeSelectMode {
		val homeSelectMode = HomeSelectMode("USB相机", mutableListOf())
		val cameraDevices = CameraUvcManager.getCameraDevices()
		if (cameraDevices.isNullOrEmpty()) return homeSelectMode
		
		cameraDevices.forEach {
			homeSelectMode.devices.add(HomeDeviceMode(it.productName ?: "UVC相机:${it.vendorId}-${it.productId}", CameraDeviceMode.USB(it)))
		}
		return homeSelectMode
	}
	
	private fun loadCameraV4L2(): HomeSelectMode {
		val homeSelectMode = HomeSelectMode("V4L2相机", mutableListOf())
		val v4L2Devices = CameraUvcManager.getV4L2Devices()
		if (v4L2Devices.isNullOrEmpty()) return homeSelectMode
		v4L2Devices.forEach {
			homeSelectMode.devices.add(HomeDeviceMode(it.split("/").last(), CameraDeviceMode.V4L2(it)))
		}
		return homeSelectMode
	}
	
}