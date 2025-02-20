@file:Suppress("UNCHECKED_CAST")

package com.rain.uvc.demo.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.annotation.IdRes
import androidx.annotation.NavigationRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import java.io.Serializable

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des intent跳转扩展类
 */

/**
 * activity跳转页面
 */
inline fun <reified T : Activity> Activity.jump(vararg params: Pair<String, Any>) {
	startActivity(Intent(this, T::class.java).also { intent ->
		if (params.isNotEmpty()) {
			params.forEach { AnkoInternals.putParam(intent, it.first, it.second) }
		}
	})
}

/**
 * Fragment跳转页面
 */
inline fun <reified T : Activity> Fragment.jump(vararg params: Pair<String, Any>) {
	startActivity(Intent(requireContext(), T::class.java).also { intent ->
		if (params.isNotEmpty()) {
			params.forEach { AnkoInternals.putParam(intent, it.first, it.second) }
		}
	})
}

/**
 * context跳转，如果
 */
inline fun <reified T : Activity> Context.jump(vararg params: Pair<String, Any>) {
	val intent = Intent(this, T::class.java).also { intent ->
		if (params.isNotEmpty()) {
			params.forEach { AnkoInternals.putParam(intent, it.first, it.second) }
		}
	}
	//如果当前context使用的是全局context，就需要创建新的任务栈，否则会因为没有栈导致报错
	if (this is Application) intent.newTask()
	startActivity(intent)
}

/**
 * 初始化intent对象
 */
inline fun <reified T : Any> Context.intentFor(vararg params: Pair<String, Any>): Intent {
	return Intent(this, T::class.java).also { intent ->
		if (params.isNotEmpty()) {
			params.forEach { AnkoInternals.putParam(intent, it.first, it.second) }
		}
	}
}

/**
 * 跳转页面
 */
fun Fragment.jumpNav(@IdRes id: Int, vararg params: Pair<String, Any>) {
	val navControl = runCatching { findNavController() }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	if (navControl == null) return
	val navOptions = navControl.currentDestination?.id?.run {
		NavOptions.Builder().setPopUpTo(this, false).setLaunchSingleTop(true).build()
	}
	navControl.navigate(id, if (params.isEmpty()) null else Bundle().also { bundle ->
		params.forEach {
			AnkoInternals.putParam(bundle, it.first, it.second)
		}
	}, navOptions)
}

/**
 * 跳转页面
 */
fun Fragment.jumpNav(@IdRes id: Int, paramsBlock: Bundle.() -> Unit) {
	val navControl = runCatching { findNavController() }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	if (navControl == null) return
	val navOptions = navControl.currentDestination?.id?.run {
		NavOptions.Builder().setPopUpTo(this, false).build()
	}
	navControl.navigate(id, Bundle().also(paramsBlock), navOptions)
}

/**
 * 跳转页面，并清空上层的栈
 */
fun Fragment.jumpNavClear(@IdRes id: Int, vararg params: Pair<String, Any>) {
	val navControl = runCatching { findNavController() }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	if (navControl == null) return
	navControl.navigate(id, if (params.isEmpty()) null else Bundle().also { bundle ->
		params.forEach {
			AnkoInternals.putParam(bundle, it.first, it.second)
		}
	}, NavOptions.Builder().setPopUpTo(navControl.graph.id, true).build())
}

/**
 * 跳转页面
 */
fun Activity.jumpNav(@IdRes viewId: Int, @IdRes id: Int, vararg params: Pair<String, Any>) {
	val navControl = runCatching { findNavController(viewId) }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	if (navControl == null) return
	val navOptions = navControl.currentDestination?.id?.run {
		NavOptions.Builder().setPopUpTo(this, false).build()
	}
	navControl.navigate(id, if (params.isEmpty()) null else Bundle().also { bundle ->
		params.forEach {
			AnkoInternals.putParam(bundle, it.first, it.second)
		}
	}, navOptions)
}

/**
 * 跳转页面，并清空上层的栈
 */
fun Activity.jumpNavClear(@IdRes viewId: Int, @IdRes id: Int, vararg params: Pair<String, Any>) {
	val navControl = runCatching { findNavController(viewId) }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	if (navControl == null) return
	navControl.navigate(id, if (params.isEmpty()) null else Bundle().also { bundle ->
		params.forEach {
			AnkoInternals.putParam(bundle, it.first, it.second)
		}
	}, NavOptions.Builder().setPopUpTo(navControl.graph.id, true).build())
}

/**
 * 设置启动的fragment，以及配置的参数信息
 */
fun Activity.startGraph(@IdRes viewId: Int, @NavigationRes id: Int, vararg params: Pair<String, Any>) {
	findNavController(viewId).setGraph(id, if (params.isEmpty()) null else Bundle().also { bundle ->
		params.forEach { AnkoInternals.putParam(bundle, it.first, it.second) }
	})
}

fun NavController.startGraph(@NavigationRes id: Int, vararg params: Pair<String, Any>) {
	setGraph(id, if (params.isEmpty()) null else Bundle().also { bundle ->
		params.forEach { AnkoInternals.putParam(bundle, it.first, it.second) }
	})
}

fun <T> NavController.putParam(key: String, value: T) {
	previousBackStackEntry?.savedStateHandle?.set(key, value)
}

fun Fragment.popStack(vararg params: Pair<String, Any>) {
	val navControl = runCatching { findNavController() }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	if (navControl == null) return
	val savedStateHandle = navControl.previousBackStackEntry?.savedStateHandle
	if (savedStateHandle != null) {
		params.forEach {
			savedStateHandle[it.first] = it.second
		}
	}
	navControl.popBackStack()
}

fun Intent.clearTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

fun Intent.clearTop(): Intent = addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

fun Intent.newTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun Intent.clearStartTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun <T> Fragment.observeResult(key: String, block: (data: T) -> Unit) {
	val navControl = runCatching { findNavController() }.onFailure {
		Log.e("Fragment", "查找跳转路由异常:${it.message}")
	}.getOrNull()
	val savedStateHandle = navControl?.currentBackStackEntry?.savedStateHandle ?: return
	savedStateHandle.getLiveData<T>(key).observe(viewLifecycleOwner) {
		block.invoke(it)
	}
}

private inline fun <reified T : Any> ArrayList<*>.isArrayOf(): Boolean {
	return this.firstOrNull()?.let { it is T } ?: false
}

/**
 * 当前执行intent的跳转功能
 */
object AnkoInternals {
	/**
	 * Bundle直接插入一条参数
	 */
	@JvmStatic
	fun putParam(bundle: Bundle, key: String, value: Any) {
		when (value) {
			is Int -> bundle.putInt(key, value)
			is Long -> bundle.putLong(key, value)
			is Double -> bundle.putDouble(key, value)
			is Float -> bundle.putFloat(key, value)
			is Boolean -> bundle.putBoolean(key, value)
			is Short -> bundle.putShort(key, value)
			is Char -> bundle.putChar(key, value)
			is Byte -> bundle.putByte(key, value)
			is String -> bundle.putString(key, value)
			is CharSequence -> bundle.putCharSequence(key, value)
			is IntArray -> bundle.putIntArray(key, value)
			is LongArray -> bundle.putLongArray(key, value)
			is DoubleArray -> bundle.putDoubleArray(key, value)
			is BooleanArray -> bundle.putBooleanArray(key, value)
			is FloatArray -> bundle.putFloatArray(key, value)
			is CharArray -> bundle.putCharArray(key, value)
			is ShortArray -> bundle.putShortArray(key, value)
			is ByteArray -> bundle.putByteArray(key, value)
			is Array<*> -> {
				when {
					value.isArrayOf<String>() -> bundle.putStringArray(key, value as Array<String>)
					value.isArrayOf<CharSequence>() -> bundle.putCharSequenceArray(key, value as Array<CharSequence>)
					value.isArrayOf<Parcelable>() -> bundle.putParcelableArray(key, value as Array<Parcelable>)
					else -> Log.e("AnkoInternals", "未知的数组类型: ${value::class.java.simpleName}")
				}
			}
			is ArrayList<*> -> {
				when {
					value.isArrayOf<String>() -> bundle.putStringArrayList(key, value as ArrayList<String>)
					value.isArrayOf<CharSequence>() -> bundle.putCharSequenceArrayList(key, value as ArrayList<CharSequence>)
					value.isArrayOf<Parcelable>() -> bundle.putParcelableArrayList(key, value as ArrayList<Parcelable>)
					else -> Log.e("AnkoInternals", "未知的数组类型: ${value::class.java.simpleName}")
				}
			}
			is Serializable -> bundle.putSerializable(key, value)
			is Parcelable -> bundle.putParcelable(key, value)
			else -> Log.e("AnkoInternals", "当前类型未知！！")
		}
	}
	
	/**
	 * Intent直接插入一条参数
	 */
	@JvmStatic
	fun putParam(intent: Intent, key: String, value: Any) {
		when (value) {
			is Int -> intent.putExtra(key, value)
			is Long -> intent.putExtra(key, value)
			is Double -> intent.putExtra(key, value)
			is Float -> intent.putExtra(key, value)
			is Boolean -> intent.putExtra(key, value)
			is Short -> intent.putExtra(key, value)
			is Char -> intent.putExtra(key, value)
			is Byte -> intent.putExtra(key, value)
			is String -> intent.putExtra(key, value)
			is CharSequence -> intent.putExtra(key, value)
			is IntArray -> intent.putExtra(key, value)
			is LongArray -> intent.putExtra(key, value)
			is DoubleArray -> intent.putExtra(key, value)
			is BooleanArray -> intent.putExtra(key, value)
			is FloatArray -> intent.putExtra(key, value)
			is CharArray -> intent.putExtra(key, value)
			is ShortArray -> intent.putExtra(key, value)
			is ByteArray -> intent.putExtra(key, value)
			is Array<*> -> {
				when {
					value.isArrayOf<String>() -> intent.putExtra(key, value as Array<String>)
					value.isArrayOf<CharSequence>() -> intent.putExtra(key, value as Array<CharSequence>)
					value.isArrayOf<Parcelable>() -> intent.putExtra(key, value as Array<Parcelable>)
					else -> Log.e("AnkoInternals", "未知的数组类型: ${value::class.java.simpleName}")
				}
			}
			is ArrayList<*> -> {
				when {
					value.isArrayOf<String>() -> intent.putStringArrayListExtra(key, value as ArrayList<String>)
					value.isArrayOf<CharSequence>() -> intent.putCharSequenceArrayListExtra(key, value as ArrayList<CharSequence>)
					value.isArrayOf<Parcelable>() -> intent.putParcelableArrayListExtra(key, value as ArrayList<Parcelable>)
					else -> Log.e("AnkoInternals", "未知的数组类型: ${value::class.java.simpleName}")
				}
			}
			is Serializable -> intent.putExtra(key, value)
			is Parcelable -> intent.putExtra(key, value)
			else -> {
				Log.e("AnkoInternals", "当前类型未知！！")
			}
		}
	}
}