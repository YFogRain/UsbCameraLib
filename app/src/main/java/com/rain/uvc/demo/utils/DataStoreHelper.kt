@file:Suppress("UNCHECKED_CAST")

package com.rain.uvc.demo.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rain.uvc.provider.OverallContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * 委托的方式创建存储dataStore
 */
private val Context.dataStore by preferencesDataStore(name = "${OverallContext.baseContext.packageName}_store")

/**
 * 存储dataStore工具类
 */
object DataStoreHelper {
	
	/**
	 * 懒加载初始化dataStore
	 */
	val dataPre by lazy { OverallContext.baseContext.dataStore }
	
	/**
	 * 清空缓存内容
	 */
	suspend fun clear() {
		dataPre.edit {
			it.clear()
		}
	}
	
	/**
	 * 获取set[String]类型的值
	 */
	suspend fun getSetStrings(key: String, defaultValue: Set<String>? = null): Set<String>? {
		return dataPre.data.map { preferences ->
			preferences[stringSetPreferencesKey(key)] ?: defaultValue
		}.firstOrNull()
	}
	
	inline fun <reified T : Any> getOrNullSync(key: String): T? {
		return runBlocking {
			getOrNull(key, T::class.java)
		}
	}
	
	suspend inline fun <reified T : Any> getOrNull(key: String): T? {
		return getOrNull(key, T::class.java)
	}
	
	suspend fun <T : Any> getOrNull(key: String, clazz: Class<T>): T? {
		val pKey = when (clazz) {
			String::class.java -> stringPreferencesKey(key)
			Int::class.java -> intPreferencesKey(key)
			Double::class.java -> doublePreferencesKey(key)
			Boolean::class.java -> booleanPreferencesKey(key)
			Float::class.java -> floatPreferencesKey(key)
			Long::class.java -> longPreferencesKey(key)
			else -> null
		} ?: return null
		return dataPre.data.map { preferences ->
			preferences[pKey]
		}.firstOrNull() as? T
	}
	
	fun <T : Any> getValueSync(key: String, defaultValue: T): T {
		return runBlocking { getValue(key, defaultValue) }
	}
	
	/**
	 * 获取其他类型的值
	 */
	@Suppress("UNCHECKED_CAST")
	suspend fun <T : Any> getValue(key: String, defaultValue: T): T {
		val pKey = when (defaultValue::class.java) {
			String::class.java -> stringPreferencesKey(key)
			Int::class.java -> intPreferencesKey(key)
			Double::class.java -> doublePreferencesKey(key)
			Boolean::class.java -> booleanPreferencesKey(key)
			Float::class.java -> floatPreferencesKey(key)
			Long::class.java -> longPreferencesKey(key)
			else -> null
		} ?: return defaultValue
		return dataPre.data.map { preferences ->
			preferences[pKey]
		}.firstOrNull() as? T ?: defaultValue
	}
	
	fun putSync(key: String, value: Any) {
		runBlocking { put(key, value) }
	}
	
	fun putSync(vararg params: Pair<String, Any>) {
		runBlocking { put(*params) }
	}
	
	/**
	 * 设置值根据类型设置
	 */
	suspend fun put(key: String, value: Any) {
		dataPre.edit { settings ->
			when (value::class.java) {
				String::class.java -> settings[stringPreferencesKey(key)] = value as String
				Int::class.java -> settings[intPreferencesKey(key)] = value as Int
				Double::class.java -> settings[doublePreferencesKey(key)] = value as Double
				Boolean::class.java -> settings[booleanPreferencesKey(key)] = value as Boolean
				Float::class.java -> settings[floatPreferencesKey(key)] = value as Float
				Long::class.java -> settings[longPreferencesKey(key)] = value as Long
				else -> settings[stringPreferencesKey(key)] = value.toString()
			}
		}
	}
	
	/**
	 * 批量存储
	 */
	suspend fun put(vararg params: Pair<String, Any>) {
		if (params.isEmpty()) return
		dataPre.edit { settings ->
			params.forEach {
				val first = it.first
				val second = it.second
				Log.d("dataStoreTag", "first:$first,second:$second")
				when (second::class.java) {
					String::class.java -> settings[stringPreferencesKey(first)] = second as String
					Int::class.java -> settings[intPreferencesKey(first)] = second as Int
					Double::class.java -> settings[doublePreferencesKey(first)] = second as Double
					Boolean::class.java -> settings[booleanPreferencesKey(first)] = second as Boolean
					Float::class.java -> settings[floatPreferencesKey(first)] = second as Float
					Long::class.java -> settings[longPreferencesKey(first)] = second as Long
					else -> settings[stringPreferencesKey(first)] = second.toString()
				}
			}
		}
	}
	
	inline fun <reified T : Any> removeSync(key: String) {
		runBlocking { remove<T>(key) }
	}
	
	/**
	 * 删除指定的key
	 */
	suspend inline fun <reified T : Any> remove(key: String) {
		val pKey = when (T::class) {
			String::class -> stringPreferencesKey(key)
			Int::class -> intPreferencesKey(key)
			Double::class -> doublePreferencesKey	(key)
			Boolean::class -> booleanPreferencesKey(key)
			Float::class -> floatPreferencesKey(key)
			Long::class -> longPreferencesKey(key)
			else -> null
		} ?: return
		dataPre.edit {
			it.remove(pKey)
		}
	}
}