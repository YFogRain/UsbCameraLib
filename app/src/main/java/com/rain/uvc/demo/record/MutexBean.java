package com.rain.uvc.demo.record;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

/**
 * Created By Chengjunsen on 2018/9/8
 */
public class MutexBean {
    private ByteBuffer byteBuffer;
    private MediaCodec.BufferInfo bufferInfo;
    private final boolean isVideo;

    public MutexBean(boolean isVideo, byte[] bytes, MediaCodec.BufferInfo bufferInfo) {
        this.isVideo = isVideo;
        this.byteBuffer = ByteBuffer.wrap(bytes);
        this.bufferInfo = bufferInfo;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        return bufferInfo;
    }
}
