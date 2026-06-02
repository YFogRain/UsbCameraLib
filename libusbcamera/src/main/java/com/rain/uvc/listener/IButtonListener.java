package com.rain.uvc.listener;

/**
 * @author yuan
 * @createTime: 2026/3/18
 * @des button按钮的事件监听
 */
public interface IButtonListener {
    /**
     * 按钮点击
     *
     * @param type  按钮类型
     * @param state 按钮状态
     */
    void buttonClick(int type, int state);
}

