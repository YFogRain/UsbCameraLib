package com.rain.uvc.demo.camera

import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.camera.ICameraDevice
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.activity.BaseDataBindActivity
import com.rain.uvc.demo.databinding.ActivityCameraBinding
import com.rain.uvc.demo.utils.GsonHelper
import com.rain.uvc.demo.utils.singleClick
import com.rain.uvc.listener.IFrameListener
import com.rain.uvc.state.CameraParameter
import com.rain.uvc.state.CameraSupportParameters
import com.rain.uvc.state.DisplayTransformState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 */
class CameraActivity : BaseDataBindActivity<ActivityCameraBinding>() {
	override fun initLayoutResId(): Int = R.layout.activity_camera
	
	override fun initView() {
	}
	
	private var mCameraDevice: ICameraDevice? = null
	private var currentRotation = 0 //旋转角度
	
	private val permissionCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (!it) {
			Toast.makeText(this, "请检查摄像头权限", Toast.LENGTH_SHORT).show()
			return@registerForActivityResult
		}
		openCamera()
	}
	
	override fun initData() {
		Log.d("cameraTimeTag", "开始加载时间-initData")
		if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissionCall.launch(android.Manifest.permission.CAMERA)
			Log.d("cameraTimeTag", "开始申请权限-launch")
			return
		}
		//使用post，保证当前的surfaceView中创建surface完成
		Log.d("cameraTimeTag", "等待view加载完毕后开始执行打开操作")
		mBinding.surfaceView.post {
			openCamera()
		}
	}
	
	override fun initEvent() {
		mBinding.tvDisplay.singleClick {
			currentRotation++
			if (currentRotation > 11) {
				currentRotation = 0
			}
			mCameraDevice?.setParameter(CameraParameter.DISPLAY_TRANSFORM, DisplayTransformState.orientationToState(currentRotation))
		}
	}
	
	override fun onStart() {
		super.onStart()
		mCameraDevice?.startPreview()
	}
	
	override fun onStop() {
		super.onStop()
		mCameraDevice?.stopPreview()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		mCameraDevice?.close()
		mCameraDevice = null
	}
	
	private val mFrameListener = IFrameListener { width, height, frame ->
//		Log.d("CameraActivity", "${width}*${height} : ${frame.capacity()}")
	}
	
	private fun openCamera() {
		//获取uvc摄像头列表
		Log.d("cameraTimeTag", "准备开始执行打开操作")
		lifecycleScope.launch(Dispatchers.IO) {
			val usbDevices = CameraUvcManager.getCameraDevices()
			Log.d("cameraTimeTag", "获取到的usb列表:${usbDevices?.size}")
			if (usbDevices.isNullOrEmpty()) return@launch
			val cameraDevice = runCatching { CameraUvcManager.openCameraSync(usbDevices[0]) }.getOrNull()
			Log.d("cameraTimeTag", "打开结果:${cameraDevice != null}")
			if (cameraDevice == null) {//报错说明打开失败
				return@launch
			}
			//赋值当前的对象
			initParameter(cameraDevice)
			Log.d("cameraTimeTag", ":::初始化参数信息完成")
			//设置预览控件
			cameraDevice.setDisplaySurface(mBinding.surfaceView)
			Log.d("cameraTimeTag", ":::设置预览控件完成")
			//设置监听
			cameraDevice.setPreviewListener(mFrameListener)
			Log.d("cameraTimeTag", ":::设置监听控件完成")
			cameraDevice.startPreview()
			Log.d("cameraTimeTag", ":::开启预览成功")
			this@CameraActivity.mCameraDevice = cameraDevice
		}
	}
	
	private fun initParameter(cameraDevice: ICameraDevice) {
		Log.d("CameraActivity", "曝光度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.EXPOSURE))}")
		Log.d("CameraActivity", "亮度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.BRIGHTNESS))}")
		Log.d("CameraActivity", "对比度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.CONTRAST))}")
		Log.d("CameraActivity", "增益值列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.GAIN))}")
		Log.d("CameraActivity", "饱和度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.SATURATION))}")
		Log.d("CameraActivity", "分辨率列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE))}")
		
		Log.d("CameraActivity", "当前自动曝光状态:${cameraDevice.getParameter(CameraParameter.AUTO_EXPOSURE)}")
		Log.d("CameraActivity", "当前曝光度:${cameraDevice.getParameter(CameraParameter.EXPOSURE)}")
		Log.d("CameraActivity", "当前亮度:${cameraDevice.getParameter(CameraParameter.BRIGHTNESS)}")
		Log.d("CameraActivity", "当前增益值:${cameraDevice.getParameter(CameraParameter.GAIN)}")
		Log.d("CameraActivity", "当前对比度:${cameraDevice.getParameter(CameraParameter.CONTRAST)}")
		Log.d("CameraActivity", "当前饱和度:${cameraDevice.getParameter(CameraParameter.SATURATION)}")
		
		
		Log.d("CameraActivity", "自动对焦支持:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_FOCUS))}")
		Log.d("CameraActivity", "焦距范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.FOCUS))}")
		Log.d("CameraActivity", "光圈范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.IRIS))}")
		Log.d("CameraActivity", "自动色值:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_HUE))}")
		Log.d("CameraActivity", "色值范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.HUE))}")
		Log.d("CameraActivity", "自动白平衡:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_WHITE_BALANCE))}")
		Log.d("CameraActivity", "白平衡范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.WHITE_BALANCE))}")
		Log.d("CameraActivity", "场景模式:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.SCENE_MODE))}")
		Log.d("CameraActivity", "隐私模式支持:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.PRIVACY))}")
		
		
		Log.d("CameraActivity", "自动对焦状态:${cameraDevice.getParameter(CameraParameter.AUTO_FOCUS)}")
		Log.d("CameraActivity", "焦距:${cameraDevice.getParameter(CameraParameter.FOCUS)}")
		Log.d("CameraActivity", "光圈:${cameraDevice.getParameter(CameraParameter.IRIS)}")
		Log.d("CameraActivity", "自动色值状态:${cameraDevice.getParameter(CameraParameter.AUTO_HUE)}")
		Log.d("CameraActivity", "色值:${cameraDevice.getParameter(CameraParameter.HUE)}")
		Log.d("CameraActivity", "自动白平衡状态:${cameraDevice.getParameter(CameraParameter.AUTO_WHITE_BALANCE)}")
		Log.d("CameraActivity", "白平衡:${cameraDevice.getParameter(CameraParameter.WHITE_BALANCE)}")
		Log.d("CameraActivity", "场景模式:${cameraDevice.getParameter(CameraParameter.SCENE_MODE)}")
		Log.d("CameraActivity", "隐私模式状态:${cameraDevice.getParameter(CameraParameter.PRIVACY)}")
	}
}