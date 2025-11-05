//package com.rain.uvc.capture;
//
//import android.media.MediaMuxer;
//
//import com.rain.uvc.utils.CameraNativeUtils;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.atomic.AtomicBoolean;
//
///**
// * @author yuan
// * @createTime: 2025/11/2
// * @des
// */
//public class CaptureHelper {
//    private VideoCapture videoCapture;
//    private AudioCapture audioCapture;
//    private final Object lock = new Object();
//    private MediaMuxer mediaMuxer;
//    private int videoTrackIndex = -1;
//    private int audioTrackIndex = -1;
//    private ExecutorService muxerExecutor;
//    private final AtomicBoolean recordState = new AtomicBoolean(false);
//
//    public boolean startRecord(String filePath, int width, int height, int fps, boolean audioEnabled) {
//        synchronized (lock) {
//            if (recordState.get()) {
//                return false;
//            }
//            File file = new File(filePath);
//            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
//                return false;
//            }
//
//            videoCapture = new VideoCapture(width, height, fps, width * height * 5); // bitrate可按需调整
//
//            if (audioEnabled) {
//                audioCapture = new AudioCapture();
//            }
//            muxerExecutor = Executors.newSingleThreadExecutor();
//            muxerExecutor.execute(() -> {
//                try {
//                    mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//                    // 初始化编码器
//                    videoCapture.prepare();
//                } catch (Exception e) {
//                }
//
//
//            });
//            return true;
//        }
//
//
//    }
//
//    public boolean stopRecord() {
//
//
//    }
//
//
//    public void start(String videoFilePath) throws Exception {
//        mediaMuxer = new MediaMuxer(videoFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//
//        videoCapture.start();
//        audioCapture.start();
//
//        // 异步编码写入
//        muxerExecutor.execute(this::encodeLoop);
//    }
//
//    public void stop() {
//        videoCapture.stop();
//        audioCapture.stop();
//
//        muxerExecutor.execute(() -> {
//            try {
//                if (mediaMuxer != null) {
//                    mediaMuxer.stop();
//                    mediaMuxer.release();
//                    mediaMuxer = null;
//                    Log.i(TAG, "MediaMuxer stopped");
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//}
//
