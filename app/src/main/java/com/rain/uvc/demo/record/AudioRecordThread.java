package com.rain.uvc.demo.record;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import com.rain.uvc.demo.provider.OverallContext;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Created By Chengjunsen on 2018/9/8
 */
public class AudioRecordThread extends Thread implements Runnable {
    private static final int TIMEOUT_S = 10000;// 1s
    private final WeakReference<MediaMuxerThread> mMutex;
    private final int mSampleRate = 22050;
    private final int BIT_RATE = 16000;
    private boolean isRecording;
    private MediaCodec mMediaCodec;
    private AudioRecord mAudioRecorder;
    private int minBufferSize;
    private long prevOutputPTSUs;
    private static final String TAG = "AudioRecordThread";

    public AudioRecordThread(MediaMuxerThread mediaMutexThread) {
        this.mMutex = new WeakReference<>(mediaMutexThread);
    }

    /**
     * 检查系统是否支持麦克风
     */
    public static boolean isHaveMicrophone() {
        try {
            return OverallContext.baseContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean initCodec() {
        try {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, mSampleRate, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRate);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize);
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private boolean initRecorder() {
        minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        Log.i(TAG, "initRecorder: --minBufferSize" + minBufferSize);
        mAudioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, mSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 2 * minBufferSize);
        if (mAudioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "initRecord: mAudioRecord init failed");
            isRecording = false;
            mAudioRecorder = null;
            return false;
        }
        mAudioRecorder.startRecording();
        return true;
    }

    @Override
    public void run() {
        byte[] bufferBytes = new byte[minBufferSize];
        int len;
        while (isRecording) {
            if (mAudioRecorder != null) {
                len = mAudioRecorder.read(bufferBytes, 0, minBufferSize);
                if (len > 0) {
                    record(bufferBytes, len, getPTSUs());
                }
            }
        }
        release();
    }

    private void record(byte[] bufferBytes, final int len, final long presentationTimeUs) {
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_S);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            Log.i(TAG, "record: inputbuffer.limit " + inputBuffer.limit() + " bufferbytes" + bufferBytes.length);
            if (inputBuffer != null) {
                inputBuffer.put(bufferBytes);
            }
            if (len <= 0) {
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
            } else {
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, len, presentationTimeUs, 0);
            }
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_S);
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.e(TAG, "audio run: INFO_OUTPUT_FORMAT_CHANGED");
            MediaMuxerThread mediaMutex = mMutex.get();
            if (mediaMutex != null && !mediaMutex.isAudioTrackExist()) {
                mediaMutex.addAudioTrack(mMediaCodec.getOutputFormat());
            }
        }

        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
            if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                Log.e(TAG, "audio run: BUFFER_FLAG_CODEC_CONFIG");
                bufferInfo.size = 0;
            }
            if (bufferInfo.size > 0) {
                MediaMuxerThread mediaMuxer = mMutex.get();
                if (mediaMuxer != null) {
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    bufferInfo.presentationTimeUs = getPTSUs();
                    Log.e(TAG, "audio presentationTimeUs : " + bufferInfo.presentationTimeUs);
                    mediaMuxer.addMutexData(new MutexBean(false, outData, bufferInfo));
                    prevOutputPTSUs = bufferInfo.presentationTimeUs;
                }
            }
            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            bufferInfo = new MediaCodec.BufferInfo();
            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_S);
        }
    }

    public void begin() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        prevOutputPTSUs = 0;
        isRecording = true;
        start();
    }

    public void end() {
        Log.i(TAG, "end: stop");
        isRecording = false;
    }

    public boolean prepare() {
        boolean resultInitRecorder = initRecorder();
        if (resultInitRecorder) {
            return initCodec();
        }
        return false;
    }

    private long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        return Math.max(result, prevOutputPTSUs);
    }

    private void release() {
        if (mAudioRecorder != null) {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
        }
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
    }
}
