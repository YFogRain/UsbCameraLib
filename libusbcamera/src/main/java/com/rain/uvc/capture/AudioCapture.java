package com.rain.uvc.capture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yuan
 * @createTime: 2025/11/2
 * @des
 */
public class AudioCapture {
    private AudioRecord audioRecorder;
    private MediaCodec audioEncoder;
    private final AtomicBoolean recording = new AtomicBoolean(false);


    public void prepare()  throws Exception {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormatType = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormatType);
        audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormatType, minBufferSize);

        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1);
        int bitrate = 64000;
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }
    public void start() throws Exception {
        if (recording.get()) return;
        audioEncoder.start();
        audioRecorder.startRecording();
        recording.set(true);
    }

    public void stop() {
        if (!recording.get()) return;

        audioRecorder.stop();
        audioRecorder.release();
        audioEncoder.stop();
        audioEncoder.release();

        recording.set(false);
    }

    public boolean isRecording() {
        return recording.get();
    }

    public AudioRecord getAudioRecorder() {
        return audioRecorder;
    }

    public MediaCodec getEncoder() {
        return audioEncoder;
    }
}

