package com.rain.uvc.demo.base.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.conversionViewBind
import com.rain.uvc.demo.utils.singleClick

/**
 * @author yuan
 * @createTime: 2025/2/28
 * @des
 */
abstract class BaseViewBindFragment<T : ViewBinding, VM : BaseViewModel> : BaseFragment<VM>() {
	protected lateinit var mBinding: T
	
	override fun initCreateView(inflater: LayoutInflater, container: ViewGroup?): View? {
		return conversionViewBind<T>(inflater, container).also {
			mBinding = it
			mBinding.root.singleClick { hideInput() }
		}.root
	}
	
	
}