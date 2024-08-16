package com.rain.uvc.provider;

import android.app.Application;
import android.content.Context;

/**
 * @author yuan
 * @createTime: 2024/8/16
 * @des 全局context实例
 */
public class OverallContext {
    public static Application baseContext;

    protected static void init(Context context) {
        if (context == null) {
            return;
        }
        baseContext = (Application) context.getApplicationContext();
    }
}

