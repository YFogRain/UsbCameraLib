package com.rain.uvc.listener;

/**
 * @author yuan
 * @createTime: 2024/8/16
 * @des 摄像头打开结果回调
 */
public interface ICameraOpenListener {
    void success();

    void failed(String message);
}

