package com.rain.uvc.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * @author yuan
 * @createTime: 2026/5/2
 * @des 视频录制引擎
 */
class VideoEngine {
	interface MuxerCallback {
		fun onVideoFormat(format: MediaFormat)
		fun onVideoData(data: ByteBuffer, info: MediaCodec.BufferInfo)
	}
	
	private var mVideoCodec: MediaCodec? = null
	
	private var mVideoSurface: Surface? = null
	
	// 录制的协程
	private var mRecordJob: Job? = null
	
	private var callback: MuxerCallback? = null
	fun setMuxerCallback(cb: MuxerCallback) {
		callback = cb
	}
	
	/**
	 * 初始化surface
	 */
	fun prepareSurface(width: Int, height: Int, fps: Int): Boolean {
		if (mVideoSurface != null) {
			mVideoSurface?.release()
		}
		val surface = runCatching { MediaCodec.createPersistentInputSurface() }.getOrNull().also {
			mVideoSurface = it
		} ?: return false
		// 初始化
		return prepareCodec(surface, width, height, fps).apply {
			if (!this) {
				mVideoSurface?.release()
				mVideoSurface = null
			}
			runCatching { mVideoCodec?.release() }
			mVideoCodec = null
		}
	}
	
	/**
	 * 获取输入的surface
	 */
	fun getSurface() = mVideoSurface
	
	private fun prepareCodec(surface: Surface, width: Int, height: Int, fps: Int): Boolean {
		Log.d("VideoEngine", "当前使用的分辨率 = $width*$height, fps: $fps")
		return runCatching {
			// 🔥 关键：设置尺寸
			val format = MediaFormat.createVideoFormat(
				MediaFormat.MIMETYPE_VIDEO_AVC, width, height
			).apply {
				setInteger(
					MediaFormat.KEY_COLOR_FORMAT,
					MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
				)
				// 设置bit帧率
				setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5)
				// 设置fps
				setInteger(MediaFormat.KEY_FRAME_RATE, fps)
				// 设置关键帧
				setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
			}
			mVideoCodec = MediaCodec.createEncoderByType(
				MediaFormat.MIMETYPE_VIDEO_AVC
			).apply {
				// 设置配置
				configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				// 设置surface
				setInputSurface(surface)
			}
		}.isSuccess
	}
	
	/**
	 * 开始录制
	 */
	fun start(width: Int, height: Int, fps: Int): Boolean {
		val surface = mVideoSurface ?: return false
		return if (prepareCodec(surface, width, height, fps)) {
			mVideoCodec?.start()
			startDrain()
			true
		} else false
	}
	
	/**
	 * 停止录制
	 */
	suspend fun stop() {
		// 等线程完全退出
		mRecordJob?.cancelAndJoin()
		mRecordJob = null
		// 🔥 2️⃣ 再安全停止 codec
		runCatching { mVideoCodec?.stop() }
		runCatching { mVideoCodec?.release() }
		mVideoCodec = null
	}
	
	/**
	 * 释放surface
	 */
	fun releaseSurface() {
		runCatching { mVideoSurface?.release() }
		mVideoSurface = null
		callback = null
	}
	
	/**
	 * 录制线程
	 */
	private fun startDrain() {
		if (mRecordJob?.isActive == true) return
		mRecordJob = recordScope.launch {
			val bufferInfo = MediaCodec.BufferInfo()
			try {
				while (isActive) {
					val codec = mVideoCodec ?: break
					val index = codec.dequeueOutputBuffer(bufferInfo, 2000)
					if (!isActive) break
					when (index) {
						MediaCodec.INFO_TRY_AGAIN_LATER -> continue
						MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> callback?.onVideoFormat(codec.outputFormat)
						else -> {
							if (index >= 0) {
								val out = codec.getOutputBuffer(index) ?: continue
								if (bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
									callback?.onVideoData(out, bufferInfo)
								}
								codec.releaseOutputBuffer(index, false)
							}
						}
					}
				}
			} catch (_: CancellationException) {
				// 正常退出
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
	
}