package com.rain.uvc.demo

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.demo.base.activity.BaseActivity
import com.rain.uvc.demo.base.viewModel.BaseViewModel

class MainActivity : BaseActivity<BaseViewModel>() {
	private lateinit var mNavController: NavController
	override fun initCreateView() {
		setStatusBarColor(ContextCompat.getColor(this, R.color.white))
		setStatusBarTextColor(true)
		setContentView(R.layout.activity_main)
		CameraUvcManager.debuggable(true)
	}
	
	override fun initializeCreated(savedInstanceState: Bundle?) {
		mNavController = findNavController(R.id.nav_home_container)
	}
	
	override fun onSupportNavigateUp(): Boolean {
		return mNavController.navigateUp() || super.onSupportNavigateUp()
	}
	
	override fun isCreatedViewModel(): Boolean {
		return false
	}
	
	override val whiteStateBarText: Boolean
		get() = true
}