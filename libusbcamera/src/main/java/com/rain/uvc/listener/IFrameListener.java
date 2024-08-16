package com.rain.uvc.listener;

import java.nio.ByteBuffer;

/**
 * 预览数据回调
 */
public interface IFrameListener {
    /**
     * 回调预览数据接口
     *
     * @param width  宽
     * @param height 高
     * @param frame  对应数据的ByteBuffer对象
     */
    void onFrame(int width, int height, ByteBuffer frame);
}

