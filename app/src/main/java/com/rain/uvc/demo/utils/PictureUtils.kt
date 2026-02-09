package com.rain.uvc.demo.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.rain.uvc.demo.provider.OverallContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream

/**
 * 保存图片
 */
object PictureUtils {
	/**
	 * 保存文件到视频资源目录下
	 */
	@JvmStatic
	fun saveToMediaDir(savePath: String?): Uri? {
		if (savePath.isNullOrBlank()) return null
		val file = File(savePath)
		if (!file.exists() || !file.isFile) return null
		val contentValues = loadContentValues(outputFile = file, false)
		val resolver = OverallContext.baseContext.contentResolver
		
		val saveUri = resolver.insert(
			MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
		) ?: return null
		try {
			// 1️⃣ 写入阶段
			resolver.openFileDescriptor(saveUri, "w")?.use { pfd ->
				FileOutputStream(pfd.fileDescriptor).use { outputStream ->
					file.source().use { source ->
						outputStream.sink().buffer().use { sink ->
							sink.writeAll(source)
							sink.flush() // 确保尾部数据写入
						}
					}
				}
			}
			contentValues.clear()
			contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
			resolver.update(saveUri, contentValues, null, null)
			MediaScannerConnection.scanFile(
				OverallContext.baseContext, arrayOf(savePath), null, null
			)
			return saveUri
		} catch (e: Exception) {
			e.printStackTrace()
			// 拷贝失败要删除无效记录
			resolver.delete(saveUri, null, null)
		}
		return null
	}
	
	@JvmStatic
	fun loadSaveUri(): Uri? {
		val fileName = "camera_video${File.separator}capture_${System.currentTimeMillis()}.mp4"
		val contentValues = ContentValues().apply {
			put(MediaStore.Video.Media.TITLE, fileName)
			put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
			put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
			put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/uvc")
			put(MediaStore.Video.Media.IS_PENDING, 1)
			
		}
		val resolver = OverallContext.baseContext.contentResolver
		return resolver.insert(
			MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues
		)
		
	}
	
	@JvmStatic
	private fun loadContentValues(outputFile: File, isImage: Boolean): ContentValues {
		val timeLong = System.currentTimeMillis()
		return ContentValues().apply {
			put(MediaStore.Video.Media.TITLE, outputFile.name)
			put(MediaStore.Video.Media.DISPLAY_NAME, outputFile.name)
			put(MediaStore.Video.Media.MIME_TYPE, if (isImage) "image/jpeg" else "video/mp4")
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				put(MediaStore.MediaColumns.DATE_TAKEN, timeLong)
				// 配置相对路径
				put(
					MediaStore.MediaColumns.RELATIVE_PATH, if (isImage) {
						Environment.DIRECTORY_PICTURES + "/uvc"
					} else {
						Environment.DIRECTORY_MOVIES + "/uvc"
					}
				)
			} else {
				// 旧版本使用绝对路径
				put(MediaStore.MediaColumns.DATA, outputFile.absolutePath)
			}
			
			put(MediaStore.MediaColumns.DATE_MODIFIED, timeLong / 1000)
			put(MediaStore.MediaColumns.DATE_ADDED, timeLong / 1000)
			put(MediaStore.MediaColumns.SIZE, outputFile.length())
		}
	}
	
	/**
	 * 保存bytes
	 */
	@JvmStatic
	fun saveJpegBytes(context: Context,bytes: ByteArray): String {
		return try {
			val fileName = "IMG_${System.currentTimeMillis()}.jpg"
			val file = File(context.filesDir , fileName)
			FileOutputStream(file).use { fos ->
				fos.write(bytes)
				fos.flush()
			}
			file.absolutePath
		} catch (e: Exception) {
			e.printStackTrace()
			""
		}
	}
	
	/**
	 * 保存bitmap
	 */
	@JvmStatic
	fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String = "IMG_${System.currentTimeMillis()}.jpg"): String? {
		return try {
			val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "images")
			if (!dir.exists()) dir.mkdirs()
			val file = File(dir, fileName)
			FileOutputStream(file).use { fos ->
				bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
				fos.flush()
			}
			file.absolutePath
		} catch (e: Exception) {
			e.printStackTrace()
			null
		}
	}
}