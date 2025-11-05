package com.rain.uvc.demo.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.UserHandle
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.rain.uvc.provider.OverallContext
import java.io.File

/**
 * 保存图片
 */
object PictureUtils {
	private var lastSaveTime: Long = 0
	private var currentSaveIndex = 1
	
	
	
	
	@JvmStatic
	fun saveSandboxVideoToGallery(sandboxFile: File): Uri? {
		if (!sandboxFile.exists() || !sandboxFile.isFile) return null
		
		val resolver = OverallContext.baseContext.contentResolver
		
		// 构建媒体信息
		val values = ContentValues().apply {
			put(MediaStore.Video.Media.DISPLAY_NAME, sandboxFile.name)
			put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
			put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
			put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				// 指定相册目录（可以改成 Movies/MyApp）
				put(MediaStore.Video.Media.RELATIVE_PATH, "Movies")
			}
		}
		
		// 插入媒体库
		val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
		if (uri != null) {
			try {
				resolver.openOutputStream(uri)?.use { output ->
					sandboxFile.inputStream().use { input ->
						input.copyTo(output)
					}
				}
				
				// 可选：刷新媒体库（Android 10+ 一般不需要）
				MediaScannerConnection.scanFile(
					OverallContext.baseContext,
					arrayOf(sandboxFile.name),
					arrayOf("video/mp4")
				) { path, scannedUri ->
					Log.d("VideoSave", "Scanned: $path -> $scannedUri")
				}
				
				Log.d("VideoSave", "Video saved to gallery: $uri")
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		
		return uri
	}
	
	@JvmStatic
	fun videoSaveToSQL(path: String?,  saveLocationState:Boolean): Uri? {
		if (path.isNullOrEmpty()) return null
		val file = File(path)
		if (!file.exists() || !file.isFile) return null
		val contentResolver = OverallContext.baseContext.contentResolver
		
		val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
		val uri = contentResolver.insert(collection, loadContentValues(file, false, saveLocationState))
		if (uri != null){
			try {
				contentResolver.openOutputStream(uri)?.use { output ->
					file.inputStream().use { input ->
						input.copyTo(output)
					}
				}
				// 通知系统媒体库刷新
				MediaScannerConnection.scanFile(
					OverallContext.baseContext,
					arrayOf(file.absolutePath),
					arrayOf("video/mp4")
				) { path, scannedUri ->
					Log.d("VideoSave", "Scanned: $path -> $scannedUri")
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
		return uri
	}
	
	@SuppressLint("MissingPermission")
	@JvmStatic
	private fun loadContentValues(file: File, isImage: Boolean, isSaveLocation: Boolean): ContentValues {
		val timeLong = System.currentTimeMillis()
		val contentValues = ContentValues()
		contentValues.put(MediaStore.MediaColumns.TITLE, file.name)
		contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
		contentValues.put(MediaStore.MediaColumns.MIME_TYPE, if (isImage) "image/jpeg" else "video/mp4")
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			contentValues.put(MediaStore.MediaColumns.DATE_TAKEN, timeLong)
			contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, file.absolutePath)
		}
		contentValues.put(MediaStore.MediaColumns.DATE_MODIFIED, timeLong)
		contentValues.put(MediaStore.MediaColumns.DATE_ADDED, timeLong)
		contentValues.put(MediaStore.MediaColumns.DATA, file.absolutePath)
		contentValues.put(MediaStore.MediaColumns.SIZE, file.length())
		//保存位置信息
		if (isSaveLocation && ContextCompat.checkSelfPermission(OverallContext.baseContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(OverallContext.baseContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
			val manager = OverallContext.baseContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
			var location: Location? = null
			try {
				val allProviders = manager.getProviders(false)
				for (str in allProviders) {
					val lastKnownLocation = manager.getLastKnownLocation(str)
					if (lastKnownLocation != null) {
						location = lastKnownLocation
						break
					}
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
			if (location != null) {
				if (isImage) {
					contentValues.put(MediaStore.Images.ImageColumns.LONGITUDE, location.longitude)
					contentValues.put(MediaStore.Images.ImageColumns.LATITUDE, location.latitude)
				} else {
					contentValues.put(MediaStore.Video.VideoColumns.LONGITUDE, location.longitude)
					contentValues.put(MediaStore.Video.VideoColumns.LATITUDE, location.latitude)
				}
			}
		}
		return contentValues
	}
	
}