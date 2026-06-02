package com.rain.uvc.demo.base.fragment

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import androidx.core.graphics.drawable.toDrawable

/**
 *  Created by 15921 on 2022/7/29 11:59
 *
 *  Describe：
 *  History：修改记录：【作者】：【时间】：【修改内容】
 */
abstract class BaseDialogFragment<VB : ViewDataBinding> : AppCompatDialogFragment() {
	protected lateinit var viewBind: VB
	protected abstract fun getLayoutResId(): Int
	protected abstract val viewModel: BaseViewModel?
	
	//佈局内的id设置null代表不需要dataBind
	protected open fun loadVariableId(): Int = -1
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return DataBindingUtil.inflate<VB>(inflater, getLayoutResId(), container, false).apply {
			viewBind = this
		}.root
	}
	
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val dialog = super.onCreateDialog(savedInstanceState)
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
		dialog.window?.run {
			setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
		}
		return dialog
	}
	
	override fun onStart() {
		super.onStart()
		dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initMVVMState()
		initializeEnd(savedInstanceState)
	}
	
	/**
	 * view初始化之后
	 */
	abstract fun initializeEnd(savedInstanceState: Bundle?)
	
	/**
	 * 初始化viewModel的loading
	 */
	private fun initMVVMState() {
		//设置loading回调
		viewBind.run {
			val variableId = loadVariableId()
			if (variableId != -1 && viewModel != null) setVariable(variableId, viewModel)
			lifecycleOwner = this@BaseDialogFragment
		}
		initModelObserve()
	}
	
	/**
	 * 初始化绑定model中的LiveData
	 */
	open fun initModelObserve() = Unit
}