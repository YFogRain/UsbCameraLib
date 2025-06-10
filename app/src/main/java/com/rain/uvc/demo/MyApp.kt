package com.rain.uvc.demo

import android.app.Application
import android.content.Context
import com.rain.uvc.demo.utils.LanguageHelper

/**
 * @author yuan
 * @createTime: 2025/2/25
 * @des
 */
class MyApp : Application() {
	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(LanguageHelper.attachContext(base))
	}
}