package com.rain.uvc.demo

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.rain.uvc.CameraUvcManager
import com.rain.uvc.demo.base.activity.BaseActivity

class MainActivity : BaseActivity() {
	private lateinit var mNavController: NavController
	override fun initCreateView() {
		setContentView(R.layout.activity_main)
		CameraUvcManager.debuggable(true)
	}
	
	override fun initializeEnd(savedInstanceState: Bundle?) {
		mNavController = findNavController(R.id.nav_home_container)
	}
	
	override fun onSupportNavigateUp(): Boolean {
		return mNavController.navigateUp() || super.onSupportNavigateUp()
	}
}