package com.rain.uvc.demo.utils

import android.view.View
import androidx.databinding.BindingAdapter

/**
 * dataBinding绑定的布局
 */

@BindingAdapter("singClick")
fun setSingClick(view: View, clickListener: View.OnClickListener) {
    view.singleClick(clickListener)
}

@BindingAdapter("viewShow")
fun setViewShow(view: View, isShow: Boolean) {
    if (isShow && view.visibility != View.VISIBLE) {
        view.visibility = View.VISIBLE
        return
    }
    if (!isShow && view.visibility != View.GONE) view.visibility = View.GONE
}