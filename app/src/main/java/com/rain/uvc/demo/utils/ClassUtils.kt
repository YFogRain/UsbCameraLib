package com.rain.uvc.demo.utils

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.coroutineScope
import androidx.viewbinding.ViewBinding
import com.rain.uvc.demo.base.fragment.BaseFragment
import com.rain.uvc.demo.base.activity.BaseActivity
import com.rain.uvc.demo.base.activity.BaseDataBindActivity
import com.rain.uvc.demo.base.fragment.BaseDataBindFragment
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

val appLifeScope by lazy { ProcessLifecycleOwner.get().lifecycle.coroutineScope }

//fragment中使用视图绑定对于的协程方法
val Fragment.viewLifeScope: LifecycleCoroutineScope
	get() = viewLifecycleOwner.lifecycle.coroutineScope

fun <VB : ViewDataBinding> ViewGroup.getBind(@LayoutRes layoutResId: Int): VB = DataBindingUtil.inflate(LayoutInflater.from(context), layoutResId, this, false)

/**
 * 获取当前fragment的viewBinding对象
 */
@Suppress("UNCHECKED_CAST")
fun <T : ViewBinding> Fragment.conversionViewBind(inflater: LayoutInflater, container: ViewGroup?): T {
	val aClass =  findBaseGenericType(false)
	if (aClass ==null|| aClass !is Class<*>)throw IllegalArgumentException("ViewBind class type not found.")
	val method = aClass.getDeclaredMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java)
	return method.invoke(null, inflater, container, false) as T
}

/**
 * 获取当前fragment的viewModel对象
 * 此种方式，仅限于直接继承baseFragment和baseDataBindFragment使用
 */
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> Fragment.conversionViewModel(): T {
	val aClass = this.findBaseGenericType(true) ?: throw IllegalArgumentException("ViewModel class type not found.")
	return ViewModelProvider(this)[aClass as Class<T>]
}

@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> AppCompatActivity.conversionViewModel(): T {
	val aClass = this.findBaseGenericType(true) ?: throw IllegalArgumentException("ViewModel class type not found.")
	return ViewModelProvider(this)[aClass as Class<T>]
}

@Suppress("UNCHECKED_CAST")
fun <T : ViewBinding> AppCompatActivity.conversionViewBind(): T {
	val aClass =  findBaseGenericType(false)
	if (aClass ==null|| aClass !is Class<*>)throw IllegalArgumentException("ViewBind class type not found.")
	val method = aClass.getDeclaredMethod("inflate", LayoutInflater::class.java,)
	return method.invoke(null, layoutInflater) as T
}

fun Fragment.findBaseGenericType(isViewModel: Boolean): Type? {
	if (!BaseFragment::class.java.isAssignableFrom(this.javaClass)) {
		return null
	}
	//直接获取当前类的父类，不使用循环是因为多继承会导致泛型擦除
	val genericSuperclass = this.javaClass.genericSuperclass
	Log.d("ClassUtilsTag", "genericSuperclass:$genericSuperclass")
	if (genericSuperclass !is ParameterizedType) return null
	val superclass = this.javaClass.superclass
	Log.d("ClassUtilsTag", "superclass:$superclass")
	if (superclass == BaseDataBindFragment::class.java) {
		val actualTypeArguments = genericSuperclass.actualTypeArguments
		return if (isViewModel) actualTypeArguments[1] else actualTypeArguments[0]
	}
	if (superclass == BaseFragment::class.java) {
		return genericSuperclass.actualTypeArguments.firstOrNull()
	}
	return null
}

fun Activity.findBaseGenericType(isViewModel: Boolean): Type? {
	if (!BaseActivity::class.java.isAssignableFrom(this.javaClass)) {
		return null
	}
	//直接获取当前类的父类，不使用循环是因为多继承会导致泛型擦除
	val genericSuperclass = this.javaClass.genericSuperclass
	Log.d("ClassUtilsTag", "genericSuperclass:$genericSuperclass")
	if (genericSuperclass !is ParameterizedType) return null
	val superclass = this.javaClass.superclass
	Log.d("ClassUtilsTag", "superclass:$superclass")
	if (superclass == BaseDataBindActivity::class.java) {
		val actualTypeArguments = genericSuperclass.actualTypeArguments
		return if (isViewModel) actualTypeArguments[1] else actualTypeArguments[0]
	}
	if (superclass == BaseActivity::class.java) {
		return genericSuperclass.actualTypeArguments.firstOrNull()
	}
	return null
}

