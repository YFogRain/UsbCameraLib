//
// Created by MI T on 2025/6/16.
//

#include "video_encoder.h"
#include "Log.h"
#include "libswscale/swscale.h"
#include "img_util.h"

VideoEncoder::VideoEncoder(const std::string filePath, int width, int height, int fps, int rotation)
        : mFilePath(filePath), mFrameFps(fps),
          mRotation(rotation),
          mIsRecordRunning(false) {
    cv::Size size = getRotatedSize(width, height, rotation);
    frameWidth = size.width;
    frameHeight = size.height;
}

VideoEncoder::~VideoEncoder() {
}

bool VideoEncoder::prepare(AVFormatContext *avFormatContext, recorder_format format) {
    auto codec = avcodec_find_encoder(select_video_codec_id_by_format(format));
    videoCodecCtx = avcodec_alloc_context3(codec);
    videoCodecCtx->width = frameWidth; //分辨率
    videoCodecCtx->height = frameHeight;//分辨率
    videoCodecCtx->time_base = {1, mFrameFps}; //设置码率
    videoCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P; //设置录制像素格式
    videoCodecCtx->bit_rate = 4000000; // 可选：设置码率
    videoCodecCtx->gop_size = 6;      // 可选：每 12 帧一个 I 帧
    videoCodecCtx->max_b_frames = 0;   // H.264 在实时录制中建议关闭 B 帧
    int result = avcodec_open2(videoCodecCtx, codec, nullptr); // 打开编码器
    if (result < 0) {
        LOG_E("打开编码器失败: %s", av_err2str(result));
        avcodec_free_context(&videoCodecCtx);
        return false;
    }
    videoStream = avformat_new_stream(avFormatContext, codec); //在封装器中创建一个新的视频流
    if (videoStream == nullptr) {
        LOG_E("创建视频流失败");
        avcodec_free_context(&videoCodecCtx);
        return false;
    }
    avcodec_parameters_from_context(videoStream->codecpar, videoCodecCtx); //拷贝编码器参数到流中，使封装器知道该流的编码格式
    result = avio_open(&avFormatContext->pb, mFilePath.c_str(), AVIO_FLAG_WRITE);//打开输出文件并写入头部
    if (result < 0) {
        LOG_E("打开输出文件失败: %s", av_err2str(result));
        avcodec_free_context(&videoCodecCtx);

        return false;
    }
    result = avformat_write_header(avFormatContext, nullptr); //写入封装文件头
    if (result < 0) {
        LOG_E("写文件头失败: %s", av_err2str(result));
        avcodec_free_context(&videoCodecCtx);
        avio_closep(&avFormatContext->pb);
        return false;
    }
    swsContext = sws_getContext(
            frameWidth, frameHeight, AV_PIX_FMT_BGR24, // 假设输入是NV21，根据你的输入调整
            frameWidth, frameHeight, videoCodecCtx->pix_fmt,
            SWS_BILINEAR, nullptr, nullptr, nullptr);

    // 保存外部传入的formatContext
    this->formatContext = avFormatContext;
    return true;
}

bool VideoEncoder::start() {
    if (mIsRecordRunning.load()) {
        LOG_E("录制已经启动");
        return false;
    }
    mIsRecordRunning.store(true);
    // 创建录制线程
    recordThread = std::thread(&VideoEncoder::thread_func_record, this);
    LOG_D("录制开始");
    return true;

}

bool VideoEncoder::stop() {
    if (!mIsRecordRunning.load()) {
        LOG_E("录制未启动，无需停止");
        return false;
    }
    // 停止录制
    mIsRecordRunning.store(false);

    // 通知等待线程退出
    recordCond.notify_one();

    // 等待线程退出
    if (recordThread.joinable()) {
        recordThread.join();
    }
    // 写尾部并关闭文件
    if (formatContext) {
        av_write_trailer(formatContext);
        if (formatContext->pb) {
            avio_closep(&formatContext->pb);
        }
        if (videoCodecCtx) {
            avcodec_free_context(&videoCodecCtx);
            videoCodecCtx = nullptr;
        }
        videoStream = nullptr;
        formatContext = nullptr;
    }
    if (swsContext) {
        sws_freeContext(swsContext);
        swsContext = nullptr;
    }
    clearRecordFrames();
    return true;
}

void VideoEncoder::putFrame(recorder_frame_t *frame) {
    {
        std::lock_guard<std::mutex> lock(recordMutex);
        if (mIsRecordRunning.load()) {
            recordFrames.push_back(frame);
            frame = nullptr;
        }
    }
    recordCond.notify_one();
    if (frame) {
        free_frame(frame);
    }
}

void VideoEncoder::thread_func_record() {
    while (mIsRecordRunning.load() && videoStream != nullptr) {
        recorder_frame_t *frame = waitRecordFrame();
        if (!frame) {
            continue;
        }
        // 处理帧数据
        writeFrame(frame);
        free_frame(frame);
    }
}

// 等待获取录制流
recorder_frame_t *VideoEncoder::waitRecordFrame() {
    recorder_frame_t *frame = nullptr;
    {
        std::unique_lock<std::mutex> lock(recordMutex);
        recordCond.wait(lock, [this] { return !mIsRecordRunning.load() || !recordFrames.empty(); });
        if (mIsRecordRunning.load() && !recordFrames.empty()) {
            frame = recordFrames.front();
            recordFrames.pop_front();
        }
    }
    return frame;
}

// 清空缓存数据
void VideoEncoder::clearRecordFrames() {
    std::lock_guard<std::mutex> lock(recordMutex);
    if (!recordFrames.empty()) {
        for (recorder_frame_t *pFrame: recordFrames) { // 直接遍历，避免 size() 变化
            free_frame(pFrame);
        }
        recordFrames.clear();
    }
}

AVCodecID VideoEncoder::select_video_codec_id_by_format(const recorder_format &recorderFormat) {
    switch (recorderFormat) {
        case mp4:
            return AV_CODEC_ID_H264;
        case mkv:
            return AV_CODEC_ID_H264;
        default:
            return AV_CODEC_ID_MPEG4;

    }
    return AV_CODEC_ID_NONE;
}

void VideoEncoder::writeFrame(recorder_frame_t *frame) {
    if (!videoCodecCtx || !formatContext || !videoStream) {
        LOG_E("编码器或格式上下文未初始化");
        return;
    }
    if (!frame)return;
    cv::Mat img = ImgUtils::rotation(frame->data, frame->width, frame->height, mRotation);
    if (img.empty()) return;

    AVFrame *avFrame = av_frame_alloc();
    if (!avFrame) return;
    avFrame->format = videoCodecCtx->pix_fmt; // 例如 AV_PIX_FMT_YUV420P
    avFrame->width = videoCodecCtx->width;
    avFrame->height = videoCodecCtx->height;
    // 设置旋转元数据
    avFrame->pts = frame->timestamp / (1000000 / mFrameFps); // 换算为帧序号或时间基计数

    uint8_t *srcData[1] = {img.data};
    int srcLineSize[1] = {static_cast<int>(img.step)};

// avFrame 已通过 av_frame_get_buffer 分配好
    sws_scale(swsContext, srcData, srcLineSize, 0, img.rows,
              avFrame->data, avFrame->linesize);

    // 编码并写入
    encodeFrame(avFrame);
    av_frame_free(&avFrame);

}

bool VideoEncoder::encodeFrame(AVFrame *frame) {
    if (!videoCodecCtx || !formatContext || !videoStream) {
        LOG_E("编码器或格式上下文未初始化");
        return false;
    }
    int ret = avcodec_send_frame(videoCodecCtx, frame);
    if (ret < 0) {
        LOG_E("发送帧到编码器失败: %s", av_err2str(ret));
        return false;
    }
    while (ret >= 0) {
        AVPacket *pkt = av_packet_alloc();
        if (!pkt) {
            LOG_E("分配AVPacket失败");
            return false;
        }

        ret = avcodec_receive_packet(videoCodecCtx, pkt);
        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
            av_packet_free(&pkt);
            break; // 编码器暂时没有数据输出了
        } else if (ret < 0) {
            LOG_E("从编码器接收数据包失败: %s", av_err2str(ret));
            av_packet_free(&pkt);
            return false;
        }
        // 设置包流索引
        pkt->stream_index = videoStream->index;
        // 转换时间戳到封装器时间基
        av_packet_rescale_ts(pkt, videoCodecCtx->time_base, videoStream->time_base);

        // 写入封装文件
        ret = av_interleaved_write_frame(formatContext, pkt);
        if (ret < 0) {
            LOG_E("写入封装包失败: %s", av_err2str(ret));
            av_packet_free(&pkt);
            return false;
        }

        av_packet_free(&pkt);
    }

    return true;
}