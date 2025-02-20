package com.rain.uvc.demo.base.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel

/**
 * @author yuan
 * @createTime: 2024/9/18
 * @des
 */
abstract class BaseFragment : Fragment() {
	//返回键拦截
	private val backDispatcher by lazy { requireActivity().onBackPressedDispatcher }
	
	private var isCreateReset: Boolean = true //是否销毁重建的，只有执行了onAttach方法，才会设置为false，因此，在onViewCreate时才能准确判断
	
	/**
	 * viewBind的对象
	 */
	protected open val mViewModel: BaseViewModel? = null
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
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
		Log.d("BaseFragment", "onCreateView-${this.javaClass}")
		return initCreateView(inflater, container)
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		Log.d("BaseFragment", "onViewCreated-${this.javaClass}")
		initMVVMState()
		initializeView(savedInstanceState)
		if (!isCreateReset) initializeData(savedInstanceState)
	}
	
	override fun onAttach(context: Context) {
		super.onAttach(context)
		Log.d("BaseFragment", "onAttach-${this.javaClass}")
		isCreateReset = false
	}
	
	override fun onDestroyView() {
		dismissDialogLoad()
		Log.d("BaseFragment", "onDestroyView-${this.javaClass}")
		isCreateReset = true
		super.onDestroyView()
	}
	
	/**
	 * 初始化view创建
	 */
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
	abstract fun initializeView(savedInstanceState: Bundle?)
	
	protected open fun initializeData(savedInstanceState: Bundle?){}
	
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
	
	protected fun hideInput() {
		try {
			val inputManager = context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
			if (!inputManager.isActive) return
			inputManager.hideSoftInputFromWindow(activity?.currentFocus?.windowToken, 0)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
}