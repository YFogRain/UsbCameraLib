package com.rain.uvc.demo.base.activity

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel

/**
 * @author yuan
 * @createTime: 2024/10/29
 * @des
 */
@Suppress("DEPRECATION")
abstract class BaseActivity : AppCompatActivity() {
	//viewModel
	protected open val viewModel: BaseViewModel? = null
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// 开启沉浸式
		enableEdgeToEdge()
		initCreateView()
		// 后续适配考虑，在处理是否需要动态化
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, 0, systemBars.right, 0)
			insets
		}
//		setStatusBarColor(getColor(R.color.navigation_bar_color))
		initMVVMState()
		initializeEnd(savedInstanceState)
	}
	
	/**
	 * 初始化viewModel的loading
	 */
	@CallSuper
	protected open fun initMVVMState() {
		//设置loading回调
		viewModel?.setDialogStateChange(lifecycleScope) {
			if (it) showDialogLoad() else dismissDialogLoad()
		}
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
	 * 显示dialog
	 */
	fun showDialogLoad() {
	}
	
	/**
	 * 隐藏loading
	 */
	fun dismissDialogLoad() {
	}
	
	override fun onDestroy() {
		dismissDialogLoad()
		super.onDestroy()
	}
	
	/**
	 * 隐藏键盘
	 */
	protected fun hideInput() {
		try {
			val inputManager = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
			if (!inputManager.isActive) return
			inputManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
			
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	/**
	 * 隐藏系统导航栏和状态栏
	 */
//	protected fun hideSystemBar() {
//		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//			//android 11+ 使用WindowInsetsController
//			window?.setDecorFitsSystemWindows(false)
//			val controller = window?.insetsController ?: return
//			controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
//			controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//			return
//		}
//		window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
//		window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
//		window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//		window.statusBarColor = Color.TRANSPARENT
//	}
//
	/**
	 * 设置状态栏颜色
	 */
//	fun setStatusBarColor(@ColorInt color: Int) {
//		// 直接设置状态栏颜色（纯色完全OK）
//		// 沉浸式
//		WindowCompat.setDecorFitsSystemWindows(window, false)
//		window.statusBarColor = color
//		WindowInsetsControllerCompat(window, window.decorView).apply {
//			isAppearanceLightStatusBars = Utils.isLightColor(color)
//		}
//		// 设置实际状态栏的颜色
//	}

//	/**
//	 * 设置状态栏的高度
//	 * @param color 颜色设置，如果是多个，则说明使用渐变色
//	 */
//	private fun setupStatusBarBg(@ColorInt vararg colors: Int) {
//		if (colors.isEmpty()) return
//		val statusBarView = findViewById<View>(R.id.statusBarBg)
//		if (colors.size > 1) {
//			statusBarView.background = GradientDrawable(
//				GradientDrawable.Orientation.LEFT_RIGHT, colors
//			)
//		} else statusBarView.setBackgroundColor(colors[0])
//		// 1️⃣ 设置颜色
//		// 2️⃣ 获取状态栏高度
//		val insets = ViewCompat.getRootWindowInsets(window.decorView)
//		val statusBarHeight = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
//		// 3️⃣ 应用高度
//		statusBarView.updateLayoutParams {
//			height = statusBarHeight
//		}
//}
}