package com.rain.uvc.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * @author yuan
 * @createTime: 2026/5/2
 * @des 音频录制引擎
 */
class AudioEngine {
	interface Callback {
		fun onAudioFormat(format: MediaFormat)
		fun onAudioData(data: ByteBuffer, info: MediaCodec.BufferInfo)
	}
	
	private var callback: Callback? = null
	private var audioRecord: AudioRecord? = null
	private var encoder: MediaCodec? = null
	
	private var mRecordJob: Job? = null
	private var mRecordOutJob: Job? = null
	
	/**
	 * 初始化音频录制
	 */
	@SuppressLint("MissingPermission")
	fun prepare(context: Context, sampleRate: Int = 44100, channelCount: Int = AudioFormat.CHANNEL_IN_DEFAULT, bitrate: Int = 128000): Boolean {
		// 检查权限和录制音频所需条件
		if (ContextCompat.checkSelfPermission(
				context, Manifest.permission.RECORD_AUDIO
			) != PackageManager.PERMISSION_GRANTED || !context.isHaveMicrophone()) {
			return false
		}
		return runCatching {
			// 最小缓存大小
			val minBuffer = AudioRecord.getMinBufferSize(
				sampleRate, channelCount, AudioFormat.ENCODING_PCM_16BIT
			)
			audioRecord = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC) // 设置录音源为麦克风
				.setAudioFormat(
					AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT) // 编码比特率
						.setSampleRate(sampleRate) // 采样率
						.setChannelMask(channelCount) // 通道模式
						.build()
				).setBufferSizeInBytes(minBuffer * 2) // 缓存大小
				.build()
			
			if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
				audioRecord = null
				return false
			}
			val format = MediaFormat.createAudioFormat(
				MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
			).apply {
				setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
				setInteger(
					MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC
				)
			}
			encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
			encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
		}.onFailure {
			release()
		}.isSuccess
	}
	
	fun setAudioCallback(callback: Callback) {
		this.callback = callback
	}
	
	fun start() {
		encoder?.start()
		audioRecord?.startRecording()
		startLooper()
	}
	
	suspend fun stop() {
		mRecordJob?.cancelAndJoin()
		mRecordJob = null
		mRecordOutJob?.cancelAndJoin()
		mRecordOutJob = null
		audioRecord?.stop()
		encoder?.stop()
		release()
	}
	
	private fun release() {
		encoder?.release()
		encoder = null
		audioRecord?.release()
		audioRecord = null
		callback = null
	}
	
	private fun startLooper() {
		if (mRecordJob?.isActive == true) return
		mRecordJob = recordScope.launch {
			val buffer = ByteArray(2048)
			while (isActive) {
				val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
				if (read <= 0) continue
				val inIndex = encoder?.dequeueInputBuffer(10000) ?: continue
				if (inIndex < 0) continue
				val input = encoder?.getInputBuffer(inIndex)
				input?.clear()
				input?.put(buffer, 0, read)
				encoder?.queueInputBuffer(
					inIndex, 0, read, TimeSource.nowUs(), 0
				)
			}
		}
		startDrainLoop()
	}
	
	private fun startDrainLoop() {
		if (mRecordOutJob?.isActive == true) return
		mRecordOutJob = recordScope.launch {
			val bufferInfo = MediaCodec.BufferInfo()
			while (isActive) {
				val outIndex = encoder?.dequeueOutputBuffer(bufferInfo, 10000) ?: let {
					delay(100)
					continue
				}
				when {
					outIndex >= 0 -> {
						val out = encoder?.getOutputBuffer(outIndex) ?: let {
							delay(100)
							continue
						}
						
						if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
							callback?.onAudioData(out, bufferInfo)
						}
						
						encoder?.releaseOutputBuffer(outIndex, false)
					}
					
					outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
						encoder?.outputFormat?.let {
							callback?.onAudioFormat(it)
						}
					}
				}
			}
		}
	}
}

/**
 * 检查系统是否支持麦克风
 */
fun Context.isHaveMicrophone(): Boolean {
	return runCatching { packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) }.getOrNull() ?: false
}
