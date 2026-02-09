package com.rain.uvc.demo.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.WindowManager
import com.rain.uvc.mode.CameraSize
import com.rain.uvc.mode.CameraSupportSize
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 扩展函数类
 */
fun Activity.isOrientationPortrait(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}

@SuppressLint("SoonBlockedPrivateApi")
fun <T : SurfaceView> T.isSurfaceCreated(): Boolean {
    return runCatching {
        val classLoader = if (this is GLSurfaceView) {
            //海燕华捷需要获取其超类的当前值
            this.javaClass.superclass
        } else this.javaClass
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
/**
 * 等待surface组件创建完毕回调
 */
suspend fun TextureView.awaitAvailable(): Boolean {
	if (this.isAvailable) return true
	return withTimeoutOrNull(5_000L) {
		suspendCancellableCoroutine { continuation ->
			val listener = object : TextureView.SurfaceTextureListener {
				override fun onSurfaceTextureAvailable(
					surface: SurfaceTexture,
					width: Int,
					height: Int
				) {
					this@awaitAvailable.surfaceTextureListener = null
					if (continuation.isActive) continuation.resume(true)
				}
				
				override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
				override fun onSurfaceTextureSizeChanged(
					surface: SurfaceTexture,
					width: Int,
					height: Int
				) = Unit
				
				override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
			}
			continuation.invokeOnCancellation {
				if (surfaceTextureListener === listener) {
					surfaceTextureListener = null
				}
			}
			this@awaitAvailable.surfaceTextureListener = listener
			// 🔑 防止 race：设置 listener 后再检查一次
			if (isAvailable && continuation.isActive) {
				surfaceTextureListener = null
				continuation.resume(true)
			}
		}
	} ?: true
}

fun Context.loadRotation(): Int {
	return when {
		// Android 11+ 推荐使用 Context.display
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> this.display.rotation
		this is Activity -> this.windowManager.defaultDisplay.rotation
		else -> (this.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
	}
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