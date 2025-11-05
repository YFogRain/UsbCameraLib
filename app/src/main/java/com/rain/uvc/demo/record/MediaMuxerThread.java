package com.rain.uvc.demo.record;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;


import com.rain.uvc.provider.OverallContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created By Chengjunsen on 2018/9/8
 */
public class MediaMuxerThread extends Thread implements Runnable {
    private final Queue<MutexBean> mMutexBeanQueue;
    private boolean isRecording;
    private AudioRecordThread mAudioThread;
    private VideoRecordThread mVideoThread;
    private final Uri path;
    private MediaMuxer mMediaMuxer;
    private int mAudioTrack;
    private int mVideoTrack;
    private boolean isMediaMuxerStart;
    private static final String TAG = "MediaMuxerThread";
    private boolean isOpenVoice = true; //是否打开录音
    private ParcelFileDescriptor mFd;

    public MediaMuxerThread(Uri path, boolean isOpenVoice) {
        Log.d("Camera1Manager", "MediaMuxerThread-isOpenVoice:" + isOpenVoice);
        this.isRecording = false;
        this.isMediaMuxerStart = false;
        this.isOpenVoice = isOpenVoice;
        this.path = path;
        this.mMutexBeanQueue = new ArrayBlockingQueue(100);
    }

    @SuppressLint("NewApi")
    public boolean prepareMediaMuxer(int width, int height) {
        try {
            mFd = OverallContext.baseContext.getContentResolver().openFileDescriptor(path, "w");
            if (mFd == null) return false;
            mAudioTrack = -1;
            mVideoTrack = -1;
            mMediaMuxer = new MediaMuxer(mFd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mVideoThread = new VideoRecordThread(this, width, height);
            Log.d(TAG, "isOpenVoice:" + isOpenVoice);
            if (isOpenVoice && AudioRecordThread.isHaveMicrophone()) {
                mAudioThread = new AudioRecordThread(this);
                boolean prepare = mAudioThread.prepare();
                Log.d(TAG, "prepare:" + prepare);
                if (!prepare) {
                    mAudioThread = null;
                }
            }
            boolean prepare = mVideoThread.prepare();
            if (prepare) {
                return true;
            }

        } catch (IOException e) {
            Log.e(TAG, "initMediaMuxer: " + e.toString());
            e.printStackTrace();
        }
        mVideoThread = null;
        mMediaMuxer = null;
        return false;
    }

    private void startMediaMutex() {
        if (isMediaMuxerStart || !isVideoTrackExist()) return;
//        if (isOpenVoice && !isAudioTrackExist()) return;
        Log.e(TAG, "run: MediaMuxerStart");
        mMediaMuxer.start();
        isMediaMuxerStart = true;
        start();
    }

    public void addAudioTrack(MediaFormat mediaFormat) {
        if (mMediaMuxer == null) {
            Log.e(TAG, "addAudioTrack: mMediaMuxer is null");
            return;
        }
        try {
            mAudioTrack = mMediaMuxer.addTrack(mediaFormat);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        startMediaMutex();
    }

    public void addVedioTrack(MediaFormat mediaFormat) {
        if (mMediaMuxer == null) {
            Log.e(TAG, "addAudioTrack: mMediaMuxer is null");
            return;
        }
        mVideoTrack = mMediaMuxer.addTrack(mediaFormat);
        startMediaMutex();
    }

    public boolean isVideoTrackExist() {
        return mVideoTrack >= 0;
    }

    public boolean isAudioTrackExist() {
        return mAudioTrack >= 0;
    }

    public boolean isRecordState() {
        return isRecording;
    }

    public void updateOriginDegrees(int degrees) {
        if (mMediaMuxer != null) mMediaMuxer.setOrientationHint(degrees);
    }

    public boolean begin(int width, int height) {
        boolean prepareResult = prepareMediaMuxer(width, height);
        Log.d("Camera1Manager", "prepareResult:" + prepareResult);
        if (!prepareResult) {
            return false;
        }
        isRecording = true;
        isMediaMuxerStart = false;
        mVideoThread.begin();
        Log.d("Camera1Manager", "begin-isOpen:" + isOpenVoice);
        if (isOpenVoice && mAudioThread != null) mAudioThread.begin();
        return true;
    }

    public void frame(byte[] data) {
        Log.d("Camera1Manager", "isRecording:" + isRecording);
        if (isRecording) {
            mVideoThread.frame(data);
        }
    }

    public Uri end() {
        try {
            Log.i(TAG, "end: stop recode");
            isRecording = false;
            if (mVideoThread != null) {
                mVideoThread.end();
                mVideoThread.join();
            }
            mVideoThread = null;
            if (isOpenVoice && mAudioThread != null) {
                mAudioThread.end();
                mAudioThread.join();
            }
            mAudioThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            if (mFd != null) {
                mFd.close();
            }
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0);
            OverallContext.baseContext.getContentResolver().update(path, contentValues, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return path;
    }

    public void addMutexData(MutexBean data) {
        mMutexBeanQueue.offer(data);
    }

    @Override
    public void run() {
        while (true) {
            if (!mMutexBeanQueue.isEmpty()) {
                MutexBean data = mMutexBeanQueue.poll();
                if (data != null) {
                    MediaCodec.BufferInfo bufferInfo = data.getBufferInfo();
                    ByteBuffer byteBuf = data.getByteBuffer();
                    if (bufferInfo != null && byteBuf != null) {
                        if (bufferInfo.size >= 0 && bufferInfo.offset >= 0 && (bufferInfo.offset + bufferInfo.size) <= byteBuf.capacity()) {
                            mMediaMuxer.writeSampleData(data.isVideo() ? mVideoTrack : mAudioTrack, byteBuf, bufferInfo);
                        }
                    }
                }
            } else {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!isRecording && mMutexBeanQueue.isEmpty()) {
                    break;
                }
            }
        }
        release();
    }

    private void release() {
        if (mMediaMuxer != null && isMediaMuxerStart) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
    }

}
