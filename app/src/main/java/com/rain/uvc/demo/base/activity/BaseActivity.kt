package com.rain.uvc.demo.base.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.LanguageHelper
import com.rain.uvc.demo.utils.conversionViewModel

/**
 * @author yuan
 * @createTime: 2024/10/29
 * @des
 */
@Suppress("DEPRECATION")
abstract class BaseActivity<VM : BaseViewModel> : AppCompatActivity() {
	
	/**
	 * viewModel对象
	 */
	protected val viewModel: VM by lazy { conversionViewModel() }
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		initializeStart(savedInstanceState)
		initCreateView()
		initMVVMState()
		initializeCreated(savedInstanceState)
	}
	
	/**
	 * 初始化布局创建等
	 */
	abstract fun initCreateView()
	
	/**
	 * view初始化之后
	 */
	protected open fun initializeStart(savedInstanceState: Bundle?) {}
	
	protected abstract fun initializeCreated(savedInstanceState: Bundle?)
	
	/**
	 * 是否创建viewModel，默认为创建
	 */
	protected open fun isCreatedViewModel(): Boolean = true
	
	/**
	 * 初始化viewModel的loading
	 */
	@CallSuper
	protected open fun initMVVMState() {
		//设置loading回调
		if (!isCreatedViewModel()) return
		viewModel.setDialogStateChange(this.lifecycleScope) {
			if (it) showDialogLoad() else dismissDialogLoad()
		}
	}
	
	/**
	 * 显示loading
	 */
	private var loading: AlertDialog? = null
	protected fun showDialogLoad() {
	}
	
	/**
	 * 隐藏loading
	 */
	protected fun dismissDialogLoad() {
	}
	
	/**
	 * 是否是白色标题栏
	 */
	open val whiteStateBarText = true
	
	override fun onDestroy() {
		dismissDialogLoad()
		super.onDestroy()
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
	
	override fun attachBaseContext(newBase: Context) {
		super.attachBaseContext(LanguageHelper.attachContext(newBase))
	}
	
	/**
	 * 设置状态栏的颜色
	 */
	fun setStatusBarColor(@ColorInt color: Int) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			window.statusBarColor = color
		}
	}
	
	/**
	 * 设置状态栏文字的颜色
	 * @param isLight true 白色 false 黑色(浅色背景时，文字为深色，深色时相反)
	 */
	fun setStatusBarTextColor(isLight: Boolean) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			val flags = window.decorView.systemUiVisibility
			window.decorView.systemUiVisibility = if (isLight) {
				flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
			} else {
				flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
			}
		}
	}
}