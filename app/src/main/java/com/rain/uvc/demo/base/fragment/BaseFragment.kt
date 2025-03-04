package com.rain.uvc.demo.base.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.demo.base.activity.BaseActivity
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.conversionViewModel
import com.rain.uvc.demo.utils.viewLifeScope

abstract class BaseFragment<VM : BaseViewModel> : Fragment() {
	/**
	 * viewBind的对象
	 */
	protected val viewModel: VM by lazy { conversionViewModel() }
	
	//返回键拦截
	private val backDispatcher by lazy { requireActivity().onBackPressedDispatcher }
	
	private var isCreateReset: Boolean = true //是否销毁重建的，只有执行了onAttach方法，才会设置为false，因此，在onViewCreate时才能准确判断
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		isCreateReset = false
		Log.d("BaseFragment", "onCreate-${this.javaClass}")
		backDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				Log.d("BaseFragment", "handleOnBackPressed:${isEnabled}")
				if (onKeyDown()) return
				//将当前enable设置为false
				isEnabled = false
				backDispatcher.onBackPressed()
			}
		})
	}
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return initCreateView(inflater, container)
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		Log.d("BaseFragment", "onViewCreated-${this.javaClass}")
		initMVVMState()
		initializeCreated(savedInstanceState)
		if (!isCreateReset) initializeFirstCreated(savedInstanceState)
	}
	
	override fun onDestroyView() {
		dismissDialogLoad()
		isCreateReset = true
		super.onDestroyView()
	}
	
	abstract fun initCreateView(inflater: LayoutInflater, container: ViewGroup?): View?
	
	/**
	 * 返回按钮点击
	 */
	open fun onKeyDown(): Boolean {
		return false
	}
	
	/**
	 * view初始化之后
	 */
	protected abstract fun initializeCreated(savedInstanceState: Bundle?)
	
	protected open fun initializeFirstCreated(savedInstanceState: Bundle?) {}
	
	/**
	 * 是否创建viewModel，默认为创建
	 */
	protected open fun isCreatedViewModel(): Boolean = true
	
	/**
	 * 初始化viewModel的loadingx
	 */
	@CallSuper
	protected open fun initMVVMState() {
		//设置loading回调
		if (!isCreatedViewModel()) return
		viewModel.setDialogStateChange(viewLifeScope) {
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
	
	protected fun hideInput() {
		try {
			val inputManager = context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
			if (!inputManager.isActive) return
			inputManager.hideSoftInputFromWindow(activity?.currentFocus?.windowToken, 0)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	protected fun setStatusBarColor(@ColorInt color: Int) {
		(activity as? BaseActivity<*>)?.setStatusBarColor(color)
	}
	
	protected fun setStatusBarTextColor(isLight: Boolean) {
		(activity as? BaseActivity<*>)?.setStatusBarTextColor(isLight)
	}
	
}