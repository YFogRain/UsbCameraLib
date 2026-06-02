package com.rain.uvc.demo.base.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.ViewCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.rain.uvc.demo.R
import com.rain.uvc.demo.utils.viewLifeScope
import com.rain.uvc.demo.base.viewModel.BaseViewModel

/**
 * @author yuan
 * @createTime: 2026/5/11
 * @des 底部弹窗的base基类
 */
class BaseBottomShellDialog(context: Context) : BottomSheetDialog(
	context, R.style.Theme_BaseBottomSheetDialog
) {
	
	@Suppress("DEPRECATION")
	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		window?.let { window ->
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
			window.navigationBarColor = Color.TRANSPARENT
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				window.isNavigationBarContrastEnforced = false
			}
			WindowCompat.getInsetsController(
				window, window.decorView
			).isAppearanceLightNavigationBars = true
		}
	}
}

/**
 * 底部弹窗的base基类
 */
abstract class BaseBottomSheetDialogFragment<T : ViewDataBinding> : BottomSheetDialogFragment() {
	private var _binding: T? = null
	protected val mBinding: T get() = _binding!!
	
	protected open val viewModel: BaseViewModel? = null
	
	private var bottomInsetOverlay: View? = null
	
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		return BaseBottomShellDialog(context = requireContext()).apply {
			setCanceledOnTouchOutside(false)
			setCancelable(true)
			dismissWithAnimation = true
			setOnKeyListener { _, keyCode, event ->
				if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
					handleBack()
				} else {
					false
				}
			}
			window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
			behavior.apply {
				isFitToContents = true
				skipCollapsed = true
				isDraggable = true
				isHideable = true
				isGestureInsetBottomIgnored = false
				peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
				state = BottomSheetBehavior.STATE_EXPANDED
			}
		}
	}
	
	/**
	 * 点击返回按钮是否拦截
	 */
	protected open fun handleBack() = false
	
	/**
	 * 对应的组件id
	 */
	@LayoutRes
	protected abstract fun loadLayoutResId(): Int
	
	/**
	 * 佈局内的id设置null代表不需要dataBind
	 */
	protected open fun loadVariableId(): Int = -1
	
	/**
	 * 是否不初始化viewModel绑定
	 */
	protected open fun isNotInitViewModel(): Boolean = false
	
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return DataBindingUtil.inflate<T>(inflater, loadLayoutResId(), container, false).apply {
			lifecycleOwner = this@BaseBottomSheetDialogFragment.viewLifecycleOwner
			_binding = this
		}.root
	}
	
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		isCancelable = true
		applyBottomInsets(view)
		initMVVMState()
		initializeEnd(savedInstanceState)
	}
	
	override fun onStart() {
		super.onStart()
		(dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
			@Suppress("DEPRECATION") bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
			attachBottomInsetOverlay(bottomSheetDialog)
		}
	}
	
	private fun initMVVMState() {
		//设置loading回调
		mBinding.run {
			val variableId = loadVariableId()
			if (variableId != -1 && viewModel != null) setVariable(variableId, viewModel)
		}
		if (!isNotInitViewModel()) {
			viewModel?.setDialogStateChange(viewLifeScope) {
				if (it) showDialogLoad() else dismissDialogLoad()
			}
		}
		initModelObserve()
	}
	
	/**
	 * 处理底部边距
	 */
	private fun attachBottomInsetOverlay(bottomSheetDialog: BottomSheetDialog) {
		val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
		val overlay = bottomInsetOverlay ?: View(requireContext()).apply {
			setBackgroundColor(Color.TRANSPARENT)
			isClickable = false
			isFocusable = false
			layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply {
				gravity = Gravity.BOTTOM
			}
			bottomSheet.addView(this)
			bottomInsetOverlay = this
		}
		ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { _, insets ->
			val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
			(overlay.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
				if (params.height != bottom) {
					params.height = bottom
					overlay.layoutParams = params
				}
			}
			insets
		}
		ViewCompat.requestApplyInsets(bottomSheet)
	}
	
	/**
	 * 初始化绑定model中的LiveData
	 */
	protected open fun initModelObserve() = Unit
	
	protected abstract fun initializeEnd(savedInstanceState: Bundle?)
	
	override fun onDestroyView() {
		dismissDialogLoad()
		hideInput()
		super.onDestroyView()
		// 清理 binding 避免内存泄漏
		_binding?.unbind()
		_binding = null
		bottomInsetOverlay = null
	}
	
	/**
	 * 显示loading
	 */
	@SuppressLint("InflateParams")
	protected fun showDialogLoad() {
	}
	
	/**
	 * 隐藏loading
	 */
	protected fun dismissDialogLoad() {
	}
	
	/**
	 * 隐藏弹窗
	 */
	protected fun hideInput() {
		try {
			dialog?.window?.currentFocus?.let { view ->
				val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
				imm.hideSoftInputFromWindow(view.windowToken, 0)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}
	
	/**
	 * 监听设置底部导航栏高度
	 */
	private fun applyBottomInsets(view: View) {
		val initialPaddingBottom = view.paddingBottom
		ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
			val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
			val bottomInset = maxOf(systemBarsInsets.bottom, imeInsets.bottom)
			target.setPadding(
				target.paddingLeft,
				target.paddingTop,
				target.paddingRight,
				initialPaddingBottom + bottomInset
			)
			insets
		}
		ViewCompat.requestApplyInsets(view)
	}
}

/**
 * 显示dialog
 * 必须存在无参构造
 */
inline fun <reified T : AppCompatDialogFragment> FragmentManager.show(tag: String, block: (fragment: T) -> Unit): T {
	val fragment = this.findFragmentByTag(tag) as? T
	if (fragment?.let {
			it.isAdded && it.dialog?.isShowing == true
		} == true) {
		return fragment
	}
	val begin = this.beginTransaction()
	fragment?.let {
		begin.remove(it).commitNowAllowingStateLoss()
	}
	return T::class.java.getDeclaredConstructor().newInstance().also {
		block.invoke(it)
		begin.add(it, tag).commitAllowingStateLoss()
	}
}