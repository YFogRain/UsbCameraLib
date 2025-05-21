package com.rain.uvc.demo.base.activity

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.utils.conversionViewBind
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * @author yuan
 * @createTime: 2025/4/15
 * @des
 */
@Keep
interface IUIState //ui状态更新

@Keep
interface IUIEventState //ui事件状态更新

@Keep
interface IUIDialogState //ui弹窗状态更新

/**
 * 基础activity
 */
abstract class BaseMviActivity<VB : ViewBinding, VM : BaseViewModel> : BaseActivity<VM>() {
	protected lateinit var mBinding: VB
	override fun initCreateView() {
		conversionViewBind<VB>().apply {
			mBinding = this
		}
	}
	
}

abstract class BaseViewModel : ViewModel() {
	private val loadDialogStared by lazy { MutableSharedFlow<IUIDialogState>() } //冷流，防止数据倒灌
}