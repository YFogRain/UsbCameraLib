# UVC 摄像头预览
### 创建摄像头操作控件
```java 
UvcCamera camera = UvcCameraHelper.create(device);
```
### 打开设备
```java 
open(new ICameraOpenListener{
public void success(){
}
public void failed(String message)
});
```
### 关闭设备
`close()`
### 开启预览
`startPreview()`
### 关闭预览
`stopPreview()`
### 获取支持的分辨率列表
`getSupportPreviewSizes()`
### 设置分辨率信息
`setPreviewSize(int width, int height, boolean isUsbMjpeg)`
### 设置预览控件
`setDisplaySurface(Surface surface) `
### 获取支持的参数范围信息
`getParameterRange(CameraRangeState state) `
### 获取当前的参数信息
`getParameter(CameraNativeState.Key<T> key)`
### 获取当前是否支持自动曝光模式
`getSupportAutoExposure()`
### 获取当前分辨率信息
`getCurrentPreviewSize()`
### 设置对应参数信息
`setParameter(CameraNativeState.Key<T> key, T value) `
