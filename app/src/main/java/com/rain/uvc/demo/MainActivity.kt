package com.rain.uvc.demo

import android.os.Bundle
import com.rain.uvc.demo.base.activity.BaseActivity

class MainActivity : BaseActivity() {
	override fun initCreateView() {
		setContentView(R.layout.activity_main)
	}
	
	override fun initializeEnd(savedInstanceState: Bundle?) {
	}
}