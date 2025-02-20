package com.rain.uvc.demo.base.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.rain.uvc.demo.utils.singleClick

/**
 * dataBind基类 - VB 为[ViewDataBinding]
 * 在[loadVariableId]不为-1的情况下进行[ViewDataBinding]和[mViewModel]的绑定
 */
abstract class BaseDataBindFragment<DB : ViewDataBinding> : BaseFragment() {
	protected lateinit var mBinding: DB
	
	/**
	 * 布局中设置的绑定的id
	 */
	protected open fun loadVariableId(): Int = -1 //佈局内的id设置null代表不需要dataBind
	
	/**
	 * 布局id
	 */
	@LayoutRes
	protected abstract fun loadLayoutResId(): Int //布局id
	
	/**
	 * 初始化绑定model中的LiveData
	 */
	open fun initModelObserve() = Unit
	
	override fun initCreateView(inflater: LayoutInflater, container: ViewGroup?): View? {
		return DataBindingUtil.inflate<DB>(inflater, loadLayoutResId(), container, false).apply {
			mBinding = this
			mBinding.root.singleClick { hideInput() }
		}.root
	}
	
	override fun initMVVMState() {
		super.initMVVMState()
		mBinding.run {
			val variableId = loadVariableId()
			if (variableId != -1 && mViewModel != null) setVariable(variableId, mViewModel)
			lifecycleOwner = viewLifecycleOwner //navigation的生命周期需要跟view绑定，否则会出现异常
		}
		initModelObserve()
	}
}