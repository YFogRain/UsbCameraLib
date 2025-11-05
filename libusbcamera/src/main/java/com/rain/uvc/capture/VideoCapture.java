package com.rain.uvc.capture;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yuan
 * @createTime: 2025/11/2
 * @des
 */
public class VideoCapture {
    private MediaCodec videoEncoder;
    private Surface inputSurface;
    private final AtomicBoolean recordState= new AtomicBoolean(false);
    private final int width, height, fps, bitrate;

    public VideoCapture(int width, int height, int fps, int bitrate) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrate = bitrate;
    }

    public Surface getInputSurface() {
        return inputSurface;
    }

    public void prepare() throws Exception{
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = videoEncoder.createInputSurface();
    }

    public void start() throws Exception {
        if (recordState.get()) return;
        videoEncoder.start();
        recordState.set(true);

    }

    public void stop() {
        if (!recordState.get()) return;

        videoEncoder.stop();
        videoEncoder.release();
        inputSurface = null;
        recordState.set(false);
    }

    public boolean isRecording() {
        return recordState.get();
    }

    public MediaCodec getEncoder() {
        return videoEncoder;
    }
}

