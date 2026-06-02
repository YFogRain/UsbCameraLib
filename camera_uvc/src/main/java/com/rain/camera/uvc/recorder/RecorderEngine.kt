package com.rain.camera.uvc.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author yuan
 * @createTime: 2026/5/2
 * @des 视频录制引擎
 */
class RecorderEngine {
	
	private val videoEngine = VideoEngine()
	private val audioEngine = AudioEngine()
	
	private var mRecordMuxer: MediaMuxer? = null
	private var mSavePath: String? = null
	
	private val muxerLock = Any()
	private var videoTrack = -1
	private var audioTrack = -1
	private val muxerStarted = AtomicBoolean(false)
	
	private val packetQueue = LinkedBlockingQueue<AvPacket>(200)
	private var muxerJob: Job? = null
	
	// audio的使用状态
	private var audioUseState: AudioUseState = AudioUseState.Not
	fun prepareVideo(width: Int, height: Int, fps: Int = 30): Boolean {
		return videoEngine.prepareSurface(width, height, fps)
	}
	
	/**
	 * 设置录制帧率（需要在录制前调用）
	 */
	fun setFps(fps: Int) {
		videoEngine.setFps(fps)
	}
	
	fun getVideoSurface() = videoEngine.getSurface()
	
	fun prepare(context: Context, rotation: Int, outputPath: String, useAudio: Boolean): Boolean {
		
		if (videoEngine.getSurface() == null) return false
		
		initRecordPath(outputPath)
		
		mRecordMuxer = MediaMuxer(
			outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
		)
		mRecordMuxer?.setOrientationHint(rotation)
		mSavePath = outputPath
		
		// 🔥 Video 回调 → 入队
		videoEngine.setMuxerCallback(object : VideoEngine.MuxerCallback {
			
			override fun onVideoFormat(format: MediaFormat) {
				synchronized(muxerLock) {
					if (muxerStarted.get()) return
					if (videoTrack != -1) return // 🔥 防重复
					videoTrack = mRecordMuxer?.addTrack(format) ?: return
					tryStartMuxer()
				}
			}
			
			override fun onVideoData(data: ByteBuffer, info: MediaCodec.BufferInfo) {
				if (!muxerStarted.get()) return
				packetQueue.offer(copyPacket(videoTrack, data, info))
			}
		})
		// 🔥 Audio
		audioUseState = if (useAudio) {
			if (audioEngine.prepare(context)) {
				audioEngine.setAudioCallback(object : AudioEngine.Callback {
					
					override fun onAudioFormat(format: MediaFormat) {
						synchronized(muxerLock) {
							if (muxerStarted.get()) return
							if (audioTrack != -1) return // 🔥 防重复
							audioTrack = mRecordMuxer?.addTrack(format) ?: return
							tryStartMuxer()
						}
					}
					
					override fun onAudioData(data: ByteBuffer, info: MediaCodec.BufferInfo) {
						if (!muxerStarted.get()) return
						packetQueue.offer(copyPacket(audioTrack, data, info))
					}
				})
				AudioUseState.PrepareSuccess
			} else {
				AudioUseState.PrepareFailed
			}
		} else AudioUseState.Not
		
		return true
	}
	
	private fun copyPacket(track: Int, src: ByteBuffer, info: MediaCodec.BufferInfo): AvPacket {
		val buffer = ByteBuffer.allocateDirect(info.size)
		val oldPos = src.position()
		val oldLimit = src.limit()
		src.position(info.offset)
		src.limit(info.offset + info.size)
		buffer.put(src)
		buffer.flip()
		
		src.position(oldPos)
		src.limit(oldLimit)
		val infoCopy = MediaCodec.BufferInfo().apply {
			set(0, info.size, info.presentationTimeUs, info.flags)
		}
		return AvPacket(track, buffer, infoCopy)
	}
	
	/**
	 * 开始合成
	 */
	private fun tryStartMuxer() {
		if (muxerStarted.get()) return
		val needAudio = audioUseState is AudioUseState.PrepareSuccess
		if (videoTrack != -1 && (!needAudio || audioTrack != -1)) {
			mRecordMuxer?.start()
			muxerStarted.set(true)
			startMuxerLoop()
		}
	}
	
	private fun startMuxerLoop() {
		muxerJob = recordScope.launch {
			var lastVideoPts = 0L
			var lastAudioPts = 0L
			while (isActive || packetQueue.isNotEmpty()) {
				val packet = packetQueue.poll(50, TimeUnit.MILLISECONDS) ?: continue
				val muxer = mRecordMuxer ?: continue
				try {
					when (packet.track) {
						videoTrack -> {
							if (packet.info.presentationTimeUs < lastVideoPts) {
								packet.info.presentationTimeUs = lastVideoPts + 1
							}
							lastVideoPts = packet.info.presentationTimeUs
							muxer.writeSampleData(videoTrack, packet.buffer, packet.info)
						}
						
						audioTrack -> {
							if (packet.info.presentationTimeUs < lastAudioPts) {
								packet.info.presentationTimeUs = lastAudioPts + 1
							}
							lastAudioPts = packet.info.presentationTimeUs
							muxer.writeSampleData(audioTrack, packet.buffer, packet.info)
						}
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
	}
	
	fun start(width: Int, height: Int): Boolean {
		if (!videoEngine.start(width, height)) return false
		if (audioUseState is AudioUseState.PrepareSuccess) audioEngine.start()
		return true
	}
	
	suspend fun stop(): String? = withContext(Dispatchers.IO) {
		// 1️⃣ 停编码
		Log.d("RecorderEngine", "停止录制～～")
		videoEngine.stop()
		audioEngine.stop()
		// 2️⃣ 等 muxer 写完
		muxerJob?.cancelAndJoin()
		muxerJob = null
		
		val path = mSavePath
		
		synchronized(muxerLock) {
			Log.d("RecorderEngine", "录制状态 = ${muxerStarted.get()}")
			Log.d("RecorderEngine", "停止录制完成")
			runCatching { mRecordMuxer?.release() }
			mRecordMuxer = null
			
			muxerStarted.set(false)
			videoTrack = -1
			audioTrack = -1
		}
		packetQueue.clear()
		mSavePath = null
		return@withContext path
	}
	
	fun releaseVideo() {
		videoEngine.releaseSurface()
	}
	
	private fun initRecordPath(path: String) {
		val file = File(path)
		file.parentFile?.takeIf { !it.exists() }?.mkdirs()
	}
}

object TimeSource {
	private val startNs = System.nanoTime()
	
	fun nowUs(): Long {
		return (System.nanoTime() - startNs) / 1000
	}
}

private data class AvPacket(val track: Int, val buffer: ByteBuffer, val info: MediaCodec.BufferInfo)

/**
 * 音频使用状态
 */
private sealed class AudioUseState {
	// 初始化成功
	data object PrepareSuccess : AudioUseState()
	
	// 初始化失败
	data object PrepareFailed : AudioUseState()
	
	// 不使用
	data object Not : AudioUseState()
}

// 录制使用的协程
val recordScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)