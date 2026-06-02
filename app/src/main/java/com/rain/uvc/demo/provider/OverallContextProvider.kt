package com.rain.uvc.demo.provider

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 *  全局context实例对象
 *
 *  Describe：context初始化类
 *  History：修改记录：【作者】：【时间】：【修改内容】
 */
object OverallContext {
	@JvmStatic
	lateinit var baseContext: Application
	
	@JvmStatic
	internal fun init(context: Context?) {
		baseContext = context?.applicationContext as Application
	}
}

internal class OverallContextProvider : ContentProvider() {
	override fun onCreate(): Boolean {
		OverallContext.init(context)
		return true
	}
	
	override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
	
	override fun getType(uri: Uri): String? = null
	
	override fun insert(uri: Uri, values: ContentValues?): Uri? = null
	
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
	
	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}