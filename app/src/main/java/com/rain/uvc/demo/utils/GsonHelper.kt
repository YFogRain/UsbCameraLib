package com.rain.uvc.demo.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken

/**
 * json解析工具类
 */
class GsonHelper private constructor() {
	private val gson: Gson by lazy {
		GsonBuilder()
			.disableHtmlEscaping()
			.setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
			.create()
	}
	
	companion object {
		private val instance: GsonHelper by lazy { GsonHelper() }
		
		@JvmName("create")
		@JvmStatic
		fun getHelper(): GsonHelper {
			return instance
		}
	}
	
	fun getGs(): Gson {
		return gson
	}
	
	fun <T> modeToJson(t: T?): String? {
		if (t == null) return null
		return runCatching {
			getGs().toJson(t)
		}.onFailure { it.printStackTrace() }.getOrNull()
	}
	
	inline fun <reified T> jsonToMode(str: String?): T? {
		if (str.isNullOrEmpty()) return null
		return try {
			getGs().fromJson<T>(str, object : TypeToken<T>() {}.type)
		} catch (e: Exception) {
			null
		}
	}
}