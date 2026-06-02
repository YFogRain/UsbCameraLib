# JNI bridge
-keep class com.rain.uvc.bridge.UvcNativeBridge {
    native <methods>;
}

-keep class com.rain.uvc.mode.** { *; }
-keep class com.rain.uvc.parameters.UvcPreviewFormat { *; }
-keep class com.rain.uvc.parameters.UvcPreviewFormat* { *; }

# JNI callback interfaces
-keep interface com.rain.uvc.listener.IFrameListener
-keep interface com.rain.uvc.listener.IButtonListener

-keepnames class * implements com.rain.uvc.listener.IFrameListener
-keepnames class * implements com.rain.uvc.listener.IButtonListener


# Keep callback method names for JNI GetMethodID
-keepclassmembers class * implements com.rain.uvc.listener.IFrameListener {
    public void onFrame(java.nio.ByteBuffer, int, int);
}

-keepclassmembers class * implements com.rain.uvc.listener.IButtonListener {
    public void buttonClick(int, int);
}