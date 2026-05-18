package com.rain.uvc.demo.base.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.rain.uvc.demo.R
import com.rain.uvc.demo.utils.viewLifeScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel

abstract class BaseFragment : Fragment() {
	//返回键拦截
	private val backDispatcher by lazy { requireActivity().onBackPressedDispatcher }
	protected open fun loadUsAcViewModel(): Boolean = false
	
	//viewModel
	protected open val viewModel: BaseViewModel? = null
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return initCreateView(inflater, container)
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		applyWindowInsets(view)
		// 添加返回键拦截
		backDispatcher.addCallback(
			viewLifecycleOwner, object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() {
					if (onKeyDown()) return
					//将当前enable设置为false
					isEnabled = false
					backDispatcher.onBackPressed()
				}
				
			})
		initMVVMState()
		initializeEnd(savedInstanceState)
	}
	
	/**
	 * 初始化view创建
	 */
	protected abstract fun initCreateView(inflater: LayoutInflater, container: ViewGroup?): View
	
	/**
	 * view初始化之后
	 */
	protected abstract fun initializeEnd(savedInstanceState: Bundle?)
	
	protected open fun initMVVMState() {
		//设置loading回调
		if (loadUsAcViewModel()) return // 如果是activity注册的，则不需要处理
		viewModel?.setDialogStateChange(viewLifeScope) {
			if (it) showDialogLoad() else dismissDialogLoad()
		}
	}
	
	/**
	 * 显示dialog
	 */
	@SuppressLint("InflateParams")
	protected fun showDialogLoad() {
	}
	
	/**
	 * 隐藏loading
	 */
	protected fun dismissDialogLoad() {
	}
	
	/**
	 * 隐藏弹窗
	 */
	protected fun hideInput() {
		try {
			val inputManager = context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
			if (!inputManager.isActive) return
			inputManager.hideSoftInputFromWindow(activity?.currentFocus?.windowToken, 0)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	/**
	 * 返回按钮点击
	 */
	protected open fun onKeyDown(): Boolean {
		return false
	}
	
	private fun applyWindowInsets(root: View) {
		ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			setupStatusBarHeight(root, systemBars.top)
			val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
			lp?.let {
				it.bottomMargin = initBottomHeight(systemBars.bottom)
				view.layoutParams = it
			}
			insets
		}
		ViewCompat.requestApplyInsets(root)
		setStatusBarTextColor(true)
	}
	
	private fun setupStatusBarHeight(view: View, statusBarHeight: Int) {
		view.findViewById<View>(R.id.statusBarBg)?.also {
			it.updateLayoutParams {
				height = statusBarHeight
			}
		}
		view.findViewById<View>(R.id.statusBarBg)?.updateLayoutParams {
			height = statusBarHeight
		}
	}
	
	/**
	 * 设置状态栏文字颜色
	 * @param isDark true = 深色文字（适合浅色背景），false = 浅色文字（适合深色背景）
	 */
	protected fun setStatusBarTextColor(isDark: Boolean) {
		val window = activity?.window ?: return
		WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isDark
	}
	
	/**
	 *  初始化底部高度
	 */
	protected open fun initBottomHeight(bottom: Int): Int = bottom
	
	override fun onDestroyView() {
		super.onDestroyView()
		dismissDialogLoad()
	}
}