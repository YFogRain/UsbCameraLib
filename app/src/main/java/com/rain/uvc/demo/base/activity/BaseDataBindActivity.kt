package com.rain.uvc.demo.base.activity

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.rain.uvc.demo.base.viewModel.BaseViewModel

/**
 * dataBinding使用父类
 */
abstract class BaseDataBindActivity<DB : ViewDataBinding> : AppCompatActivity() {
	protected lateinit var mBinding: DB
	protected abstract val mViewModel: BaseViewModel?
	
	/**
	 * 初始化layout的id
	 */
	@LayoutRes
	protected abstract fun initLayoutResId(): Int
	
	/**
	 * 初始化双向绑定id
	 */
	protected open fun initVariableId() = -1
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		mBinding = DataBindingUtil.setContentView(this, initLayoutResId())
		initViewDataBinding()
		initIntent(savedInstanceState)
		init()
	}
	
	private fun initViewDataBinding() {
		if (mViewModel != null) initVariableId().also {
			if (it != -1) mBinding.setVariable(it, mViewModel)
		}
		mBinding.lifecycleOwner = this
		initModelObserve()
	}
	
	@CallSuper
	open fun init() {
		initView()
		initEvent()
		initData()
	}
	
	/**
	 * 初始化绑定model中的LiveData
	 */
	open fun initModelObserve() = Unit
	
	/**
	 * 初始化获取intent传递的数据
	 */
	open fun initIntent(savedInstanceState: Bundle?) = Unit
	
	/**
	 * 初始化点击事件
	 */
	open fun initEvent() = Unit
	
	/**
	 * 初始化View
	 */
	open fun initView() = Unit
	
	/**
	 * 初始化数据
	 */
	open fun initData() = Unit
	
}