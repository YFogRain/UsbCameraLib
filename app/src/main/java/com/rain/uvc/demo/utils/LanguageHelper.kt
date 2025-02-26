package com.rain.uvc.demo.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.core.content.edit
import java.util.Locale

/**
 * @author yuan
 * @createTime: 2025/2/25
 * @des
 */
object LanguageHelper {
	//保存Locale的key
	private const val LOCALE_KEY = "LOCALE_KEY"
	
	//保存SharedPreferences的文件名
	private const val LOCALE_FILE = "LOCALE_FILE"
	
	/**
	 * 获取用户设置的Locale,保存languageTag的方式
	 * 使用store保存最好
	 */
	@JvmStatic
	fun getUserLocale(context: Context): Locale? {
		return getUserLocaleStr(context)?.let {
			return Locale.forLanguageTag(it)
		}
	}
	
	/**
	 * 保存用户设置的Locale对应的languageTag
	 */
	@JvmStatic
	private fun getUserLocaleStr(context: Context): String? {
		return context.getSharedPreferences(LOCALE_FILE, Context.MODE_PRIVATE)?.getString(LOCALE_KEY, "")
	}
	
	/**
	 * 保存用户设置的Locale
	 */
	@JvmStatic
	private fun saveUserLocale(context: Context, locale: Locale) {
		context.getSharedPreferences(LOCALE_FILE, Context.MODE_PRIVATE)?.edit {
			putString(LOCALE_KEY, locale.toLanguageTag())
		}
	}
	
	/**
	 * 获取当前context内的语言配置
	 */
	@JvmStatic
	private fun getCurrentLocale(configuration: Configuration): Locale {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			configuration.locales.get(0)
		} else configuration.locale
	}
	
	/**
	 * 检查是否需要多语言切换
	 */
	@JvmStatic
	fun isNeedChangeLocale(configuration: Configuration, newLocaleTag: String): Boolean {
		return newLocaleTag != getCurrentLocale(configuration).toLanguageTag()
	}
	
	/**
	 * 初始化context
	 * 在Application和activity的attachBaseContext中调用
	 */
	@JvmStatic
	fun attachContext(context: Context): Context {
		val userLocaleStr = getUserLocaleStr(context)
		if (userLocaleStr.isNullOrEmpty() || !isNeedChangeLocale(context.resources.configuration, userLocaleStr)) {//如果缓存的context为空或者null，则说明不需要修改context中的locale实例
			return context
		}
		applyLocale(context, Locale.forLanguageTag(userLocaleStr))
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			return context.createConfigurationContext(context.resources.configuration)
		}
		return context
	}
	
	/**
	 * 更新locale
	 * 如果是activity，则需要finish和startActivity
	 */
	@JvmStatic
	fun updateLocale(context: Context, locale: Locale, isUpdateApplication: Boolean = true) {
		if (!isNeedChangeLocale(context.resources.configuration, locale.toLanguageTag())) {//如果不需要修改locale，则直接返回
			Log.d("LanguageHelper", "不需要修改locale")
			return
		}
		//保存用户设置的locale
		saveUserLocale(context, locale)
		//更新context
		applyLocale(context, locale)
		if (isUpdateApplication) {
			//更新application的locale
			applyLocale(context.applicationContext, locale)
		}
	}
	
	/**
	 * 设置locale
	 */
	@Suppress("DEPRECATION")
	@JvmStatic
	private fun applyLocale(context: Context, locale: Locale) {
		val configuration = context.resources.configuration
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			// 7.0 以上系统,设置语言
			configuration.setLocales(LocaleList(locale).apply {
				LocaleList.setDefault(this) //设置默认使用的语言
			})
		} else configuration.setLocale(locale)//设置当前的语言
		//更新当前context的语言(这个会直接更新当前context的locale)
		context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
	}
}