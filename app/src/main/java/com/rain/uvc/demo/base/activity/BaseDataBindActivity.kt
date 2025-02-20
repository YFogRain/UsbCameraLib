package com.rain.uvc.demo.base.activity

import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.rain.uvc.demo.utils.singleClick

/**
 * @author yuan
 * @createTime: 2024/10/29
 * @des
 */
abstract class BaseDataBindActivity<DB: ViewDataBinding> : BaseActivity() {
	protected lateinit var mBinding:DB
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
	
	override fun initCreateView() {
		mBinding = DataBindingUtil.setContentView(this, loadLayoutResId())
		mBinding.root.singleClick { hideInput() }
	}
	
	override fun initMVVMState() {
		super.initMVVMState()
		mBinding.run {
			val variableId = loadVariableId()
			if (variableId != -1 && mViewModel != null) setVariable(variableId, mViewModel)
			lifecycleOwner = this@BaseDataBindActivity
		}
		initModelObserve()
	}
}