package com.rain.uvc.demo.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * 扩展函数类
 */
val appLifecycleScope by lazy { ProcessLifecycleOwner.get().lifecycleScope }

fun <VB : ViewDataBinding> ViewGroup.getBind(@LayoutRes layoutResId: Int): VB =
        DataBindingUtil.inflate(LayoutInflater.from(context), layoutResId, this, false)

fun Activity.isOrientationPortrait(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}

@SuppressLint("SoonBlockedPrivateApi")
fun <T : SurfaceView> T.isSurfaceCreated(): Boolean {
    return runCatching {
        val classLoader = if (this is GLSurfaceView) {
            //海燕华捷需要获取其超类的当前值
            this.javaClass.superclass.superclass
        } else this.javaClass.superclass
        val field = classLoader.getDeclaredField("mSurfaceCreated")
        field.isAccessible = true
        return@runCatching field.get(this) as? Boolean
    }.onFailure {
        it.printStackTrace()
    }.onSuccess {
    }.getOrNull() ?: false
}

suspend fun <T : SurfaceView> T.loadCreatedState(): Boolean {
    if (this@loadCreatedState.isSurfaceCreated()) return true
    return runCatching {
        withTimeout(3000) {
            suspendCancellableCoroutine { continuation ->
                val surfaceCall = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        continuation.resume(true)
                        this@loadCreatedState.holder.removeCallback(this)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        continuation.resume(false)
                        this@loadCreatedState.holder.removeCallback(this)
                    }
                }
                continuation.invokeOnCancellation {
                    Log.d("surfaceTag", "it:$it")
                    this@loadCreatedState.holder.removeCallback(surfaceCall)
                }
                if (this@loadCreatedState.isSurfaceCreated()) {
                    continuation.resume(true)
                } else this@loadCreatedState.holder.addCallback(surfaceCall)
            }
        }
    }.getOrNull() ?: this@loadCreatedState.isSurfaceCreated()
}

object Common{
    @JvmStatic
    fun isUvcCamera(usbDevice: UsbDevice): Boolean {
        return usbDevice.deviceClass == 239 && usbDevice.deviceSubclass == 2 && when (usbDevice.productId) {
            24581, 33054 -> false
            else -> true
        } && !usbDevice.productName.run { !this.isNullOrEmpty() && this.contains("Android", true) }
    }
}