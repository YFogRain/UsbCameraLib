package com.rain.uvc.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author yuan
 * @createTime: 2024/8/16
 * @des 全局context实例初始化的contextProvider，一般用于lib初始化，解决lib库内不存在context实例的问题
 */
public class OverallContextProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        OverallContext.init(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}

