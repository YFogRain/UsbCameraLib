package com.rain.uvc.demo.base.viewModel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * viewModel父类
 */
abstract class BaseViewModel : ViewModel() {
	private val loadDialogStared by lazy { MutableSharedFlow<Boolean>() } //冷流，防止数据倒灌
	
	/**
	 * 显示load
	 */
	suspend fun showLoadDialog() {
		loadDialogStared.emit(true)
	}
	
	/**
	 * 隐藏load
	 */
	suspend fun dismissDialog() {
		loadDialogStared.emit(false)
	}
	
	/**
	 * 设置dialog回调
	 */
	fun setDialogStateChange(scope: CoroutineScope, block: (Boolean) -> Unit) {
		scope.launch {
			loadDialogStared.collect(block)
		}
	}
}