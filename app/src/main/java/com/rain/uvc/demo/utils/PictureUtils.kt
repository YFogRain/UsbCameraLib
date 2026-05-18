package com.rain.uvc.demo.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.rain.uvc.demo.provider.OverallContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 保存图片
 */
object PictureUtils {
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
	
	@RequiresApi(Build.VERSION_CODES.N)
	private fun writeExif(context: Context, uri: Uri, rotation: Int) {
		runCatching {
			context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
				val exif = ExifInterface(pfd.fileDescriptor)
				val orientation = when (rotation) {
					90 -> ExifInterface.ORIENTATION_ROTATE_90
					180 -> ExifInterface.ORIENTATION_ROTATE_180
					270 -> ExifInterface.ORIENTATION_ROTATE_270
					else -> ExifInterface.ORIENTATION_NORMAL
				}
				
				exif.setAttribute(
					ExifInterface.TAG_ORIENTATION, orientation.toString()
				)
				exif.saveAttributes()
			}
		}.onFailure {
			it.printStackTrace()
		}
	}
	
	/**
	 * 保存至相册
	 * @param inputStream 需要保存的流对象
	 * @param filename 文件名称
	 * @param mimeType 文件类型
	 */
	@JvmStatic
	fun saveGallery(context: Context, inputStream: InputStream, orientation: Int, filename: String, mimeType: String = "image/jpeg"): Uri? {
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			return saveGallery23(context, inputStream, filename)
		}
		return saveGallery29(context, inputStream, orientation, filename, mimeType)
	}
	
	/**
	 * 29+写入相册的方法
	 */
	@RequiresApi(Build.VERSION_CODES.Q)
	@JvmStatic
	private fun saveGallery29(context: Context, inputStream: InputStream, orientation: Int, filename: String, mimeType: String = "image/jpeg"): Uri? {
		val resolver = context.contentResolver
		val now = System.currentTimeMillis()
		val uri = resolver.insert(
			MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
				put(MediaStore.Images.Media.DISPLAY_NAME, filename)
				put(MediaStore.Images.Media.MIME_TYPE, mimeType)
				put(MediaStore.Images.Media.DATE_TAKEN, now)
				put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
				put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000)
				put(
					MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera"
				)
				put(MediaStore.Images.Media.IS_PENDING, 1)
			}) ?: return null
		// ⭐ Okio 核心写法
		val state = runCatching {
			resolver.openOutputStream(uri)?.sink()?.buffer()?.use { sink ->
				inputStream.source().use { source ->
					sink.writeAll(source)
				}
			}
			// 👇 ⭐ 在这里写 EXIF（关键）
			writeExif(context, uri, orientation)
			resolver.update(uri, ContentValues().apply {
				put(MediaStore.Images.Media.IS_PENDING, 0)
			}, null, null)
		}.isSuccess
		if (!state) {
			resolver.delete(uri, null, null)
			return null
		}
		return uri
	}
	
	/**
	 * 29以下写入相册的方法
	 */
	@JvmStatic
	private fun saveGallery23(context: Context, inputStream: InputStream, filename: String): Uri? {
		if (ContextCompat.checkSelfPermission( // 检查权限
				context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
			) != PackageManager.PERMISSION_GRANTED) {
			return null
		}
		val dir = runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Camera") }.getOrNull() ?: return null
		if (!dir.exists()) {
			dir.mkdirs()
		}
		val file = File(dir, filename)
		
		return runCatching {
			file.outputStream().use { out ->
				inputStream.use { input ->
					input.copyTo(out)
				}
			}
			val path = file.absolutePath
			// ❗ 旧系统必须用 file path（content:// 不行）
			MediaScannerConnection.scanFile(
				context, arrayOf(path), null, null
			)
			// 兜底广播（部分 ROM 依赖）
			context.sendBroadcast(
				Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
					data = Uri.fromFile(File(path))
				})
			Uri.fromFile(file)
		}.getOrNull()
	}
}