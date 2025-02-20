package com.rain.uvc.demo.base.activity

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel

/**
 * @author yuan
 * @createTime: 2024/10/29
 * @des
 */
abstract class BaseActivity : AppCompatActivity() {
	/**
	 * viewModel对象
	 */
	protected open val mViewModel: BaseViewModel? = null
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		initializeStart()
		stateBarTextColor()
		initCreateView()
		initMVVMState()
		initializeEnd(savedInstanceState)
	}
	
	/**
	 * 初始化布局创建等
	 */
	abstract fun initCreateView()
	
	/**
	 * view初始化之后
	 */
	abstract fun initializeEnd(savedInstanceState: Bundle?)
	
	/**
	 * view初始化之前
	 */
	open fun initializeStart() {}
	
	/**
	 * 初始化viewModel的loading
	 */
	@CallSuper
	protected open fun initMVVMState() {
		//设置loading回调
		mViewModel?.setDialogStateChange(lifecycleScope) {
			if (it) showDialogLoad() else dismissDialogLoad()
		}
	}
	
	/**
	 * 显示loading
	 */
	protected fun showDialogLoad() {
	}
	
	/**
	 * 隐藏loading
	 */
	protected fun dismissDialogLoad() {
	}
	
	override fun onDestroy() {
		dismissDialogLoad()
		super.onDestroy()
	}
	
	/**
	 * 设置标题栏文字颜色 白色和黑色
	 */
	@Suppress("DEPRECATION")
	private fun stateBarTextColor() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && whiteStateBarText) {
			window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
		}
	}
	
	/**
	 * 是否是白色标题栏
	 */
	open val whiteStateBarText = true
	
	/**
	 * 设置toolbar的颜色
	 */
	
	protected fun setBarColor(@ColorInt color: Int) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) window.statusBarColor = color
	}
	
	protected fun hideInput() {
		try {
			val inputManager = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
			if (!inputManager.isActive) return
			inputManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
			
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		Log.d("BaseActivity", "keyCode:${keyCode},event:${event}")
		return super.onKeyDown(keyCode, event)
	}
}