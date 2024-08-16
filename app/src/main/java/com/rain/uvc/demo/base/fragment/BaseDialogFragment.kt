package com.rain.uvc.demo.base.fragment

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.rain.uvc.demo.base.viewModel.BaseViewModel

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
    protected open fun getVariableId(): Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return DataBindingUtil.inflate<VB>(inflater, getLayoutResId(), container, false).apply {
            viewBind = this
        }.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.run {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViewDataBinding()
        init()
    }

    /**
     * 初始化绑定viewDataBind
     */
    private fun initViewDataBinding() {
        viewBind.run {
            val variableId = getVariableId()
            if (variableId != -1 && viewModel != null) setVariable(variableId, viewModel)
            lifecycleOwner = this@BaseDialogFragment
        }
        initModelObserve()
    }

    /**
     * 初始化绑定model中的LiveData
     */
    open fun initModelObserve() = Unit

    @CallSuper
    open fun init() {
        initView()
        initEvent()
        initData()
    }

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