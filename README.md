# UvcCamera

### 简介

uvc摄像头预览操作程序

1. 支持无界面/有界面预览
2. 支持设置分辨率/曝光度等参数
3. 使用设备为OpenHarmony

### 软件架构

当前为dev版本

## 使用说明

### 操作基础类 `CameraUvcManager`

#### `debuggable`

debug模式开关，打开后会有基础日志输出

* `status`:需要设置的debug状态
* `return`:设置结果

```java
public static boolean debuggable(boolean status);
```

#### `getUvcDevices`

获取当前设备上的usb摄像头列表

* `return`:usb设备列表，如果不存在则返回null或者空

```java
public static List<UsbDevice> getCameraDevices();
```

#### `getDeviceForId`

根据传入的vId和pId获取对应的usb设备

* `vId`:usb设备的VendorId
* `pId`:usb设备的ProductId
* `return`:返回对应的usb设备

```java
public static UsbDevice getDeviceForId(int vId, int pId);
```

#### `checkDeviceUvc`

检查当前设备是否是uvc摄像头设备

* `usbDevice`:当前的usb设备对象

```java
public static boolean checkDeviceUvc(UsbDevice usbDevice);
```

#### 打开摄像头

需要检查摄像头权限，同时申请usb临时权限，使用videoPath类型的，需要保证应用有video的访问权限

##### 异步

* `usbDevice`:需要打开的usb设备
* `timeout`:超时时间
* `listener`:结果回调

```java
public static void openCamera(UsbDevice usbDevice, long timeout, ICameraOpenListener listener);
```

##### 同步

同步打开需要在子线程操作，防止因超时导致ANR,
存在默认10s超时的同名方法

* `usbDevice`:需要打开的usb设备
* `timeout`:超时时间
* `return`:返回摄像头操作对象
* `throws`:打开失败后抛出异常，异常信息展示对应的错误信息

```java
public static CameraDevice openCameraSync(UsbDevice usbDevice, long timeout) throws UvcCameraAccessException;
```

#### 取消正在执行的开启任务

执行后，可退出当前所有正在执行的任务，一般在页面或应用退出前调用

```java
public static void cancel();
```

### `CameraDevice` 摄像头操作类说明

包含所有摄像头可使用的操作对象
支持，参数设置，设置预览，设置分辨率等
部分参数需要停止预览后再设置

#### 关闭摄像头

摄像头关闭后重新打开需要使用`CameraUvcManager`重新初始化

* `return`: 返回关闭结果

```java
public boolean close();
```

#### 设置预览控件

当前支持三种设置方式

```java
public abstract boolean setDisplaySurface(Surface surface);
```

```java
public abstract boolean setDisplaySurface(SurfaceView view);
```

```java
public abstract boolean setDisplaySurface(TextureView view);
```

#### 设置使用分辨率

支持格式YUY2/MJPEG
通过`getSupportParameters`可获取当前设备支持的分辨率格式,宽,高。

* `width`:宽
* `height`:高
* `formatState`:当前使用的预览格式

```java
public abstract setPreviewSize(int width, int height, CameraPreviewFormat formatState);
```

#### 设置预览监听

回调监听设置，返回值为`ByteBuffer`对象

* `listener`:预览的数据回调，只有在有数据的情况下才会回调
* `format`:回调使用的格式，目前仅支持 `BGR`、 `NV21`、 `RGBA`;

```java
public boolean setPreviewListener(IFrameListener listener, CameraDataFormat format);
```

#### 开启预览

开启预览，建议在打开后同步的子线程使用，一般开启预览不会超过2s

* `return`:返回开启结果

```java
public boolean startPreview();
```

#### 关闭预览

停止预览操作

```java
public boolean stopPreview();
```

#### 参数设置

支持设置自动曝光/曝光度/分辨率/预览图形变换规则等参数

* `key`:支持获取的参数key,仅支持使用`CameraParameter`类常量参数
* `value`:需要设置的值
* `return`:返回对应的值，根据key的具体类型来

```java
public <T> boolean setParameter(CameraParameter.Key<T> key, T value);
```

#### 参数获取

获取当前key对应的参数值，如果返回null，则说明不支持

* `key`: 支持获取的参数key,仅支持使用`CameraParameter`类常量参数
* `return`:返回对应的值，根据key的具体类型来

```java
public <T> T getParameter(CameraParameter.Key<T> key);
```

#### 获取参数范围

可获取分辨率列表，支持的曝光度范围，亮度范围，等,
如果返回`null`或者空，则说明不支持

* `key`: 支持获取的参数key,仅支持使用`CameraSupportParameters`类常量参数
* `return`:返回对应的值，根据key的具体类型来

```java
public <T> T getSupportedParameter(CameraSupportParameters.Key<T> key);
```

#### 设置usb断开监听

* `listener`:在usb设备被移除的监听回调

```java
public void setDetachedCloseListener(IDetachedCloseListener listener);
```

#### 预览状态

获取当前的预览状态信息

* `return`: 返回`true`表示正在预览

```java
public boolean cameraIsPreviewing();
```

### 枚举/类

内置枚举/类说明

#### `CameraDataFormat`

预览监听返回的数据类型。
目前只支持 `BGR`、`RGBA`、`NV21`

#### `CameraPreviewFormat`

预览分辨率使用的格式类型，根据分辨率列表返回值确定
目前支持`MJPEG`、`YUY2`

#### `IntRange`

使用`getSupportParameters`时，当前返回值为范围值时使用，返回最大值和最小值
`min`:最小值
`max`:最大值

#### `DisplayTransformState`

对应当前`surface`窗口变化支持的类型

* `TRANSFORM_IDENTITY`:无变化
* `TRANSFORM_MIRROR_HORIZONTAL`:水平镜像
* `TRANSFORM_MIRROR_VERTICAL`:垂直镜像
* `TRANSFORM_ROTATE_90`:旋转90度
* `TRANSFORM_ROTATE_180`:旋转180度
* `TRANSFORM_ROTATE_270`:旋转270度
* `TRANSFORM_FLIP_H_ROTATE_90`:旋转90度+水平镜像
* `TRANSFORM_FLIP_H_ROTATE_180`:旋转180度+水平镜像
* `TRANSFORM_FLIP_H_ROTATE_270`:旋转270度+水平镜像
* `TRANSFORM_FLIP_V_ROTATE_90`:旋转90度+垂直镜像
* `TRANSFORM_FLIP_V_ROTATE_180`:旋转180度+垂直镜像
* `TRANSFORM_FLIP_V_ROTATE_270`:旋转270度+垂直镜像

#### `CameraParameter`

目前支持设置/获取的参数信息

* `AUTO_EXPOSURE`: 自动曝光状态
* `EXPOSURE`: 曝光度值
* `BRIGHTNESS`: 亮度值
* `CONTRAST`: 对比度值
* `GAIN`: 增益值
* `SATURATION`: 饱和度
* `ZOOM`: 缩放大小
* `DISPLAY_TRANSFORM`: 窗口变化支持的类型，见`DisplayTransformState`
* `PREVIEW_SIZE`: 分辨率信息
* `FOCUS`:焦距
* `AUTO_FOCUS`:自动聚焦
* `IRIS`:光圈
* `AUTO_HUE`:自动设置色值
* `HUE`:色值
* `AUTO_WHITE_BALANCE`:自动化白平衡
* `WHITE_BALANCE`:白平衡
* `SCENE_MODE`:场景模式
* `PRIVACY`:隐私模式

#### `CameraSupportParameters`

获取支持的参数范围

* `AUTO_EXPOSURE`:是否支持自动曝光
* `EXPOSURE`:曝光度范围，为null或者 最大值 = 最小值时，表示不支持
* `BRIGHTNESS`:亮度范围，为null或者 最大值 = 最小值时，表示不支持
* `CONTRAST`:对比度范围，为null或者 最大值 = 最小值时，表示不支持
* `GAIN`:增益值范围，为null或者 最大值 = 最小值时，表示不支持
* `SATURATION`:饱和度范围，为null或者 最大值 = 最小值时，表示不支持
* `ZOOM`:缩放范围，为null或者 最大值 = 最小值时，表示不支持
* `PREVIEW_SIZE`:支持的分辨率列表
* `FOCUS`:焦距范围，为null或者 最大值 = 最小值时，表示不支持
* `AUTO_FOCUS`:是否支持自动聚焦
* `IRIS`:光圈范围，为null或者 最大值 = 最小值时，表示不支持
* `AUTO_HUE`:是否支持自动设置色值
* `HUE`:色值范围，为null或者 最大值 = 最小值时，表示不支持
* `AUTO_WHITE_BALANCE`:是否支持自动化白平衡
* `WHITE_BALANCE`:白平衡范围，为null或者 最大值 = 最小值时，表示不支持
* `SCENE_MODE`:场景模式范围，为null或者 最大值 = 最小值时，表示不支持
* `PRIVACY`:是否支持隐私模式

#### `CameraSize`

分辨率信息

* `width`: 宽度
* `height`:高度
* `format`:对应的分辨率，详见`CameraPreviewFormat`

### 例:

```kotlin

class CameraActivity : AppCompatActivity() {
	private lateinit var mBinding: ActivityCameraBinding
	
	private var mCameraDevice: ICameraDevice? = null
	private var currentRotation = 0 //旋转角度
	
	private val permissionCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (!it) {
			Toast.makeText(this, "请检查摄像头权限", Toast.LENGTH_SHORT).show()
			return@registerForActivityResult
		}
		openCamera()
	}
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		mBinding = DataBindingUtil.setContentView(this, R.layout.activty_camera)
		mBinding.setLifecycleOwner(this)
		initEvent()
		//检查权限，如果没有，则需要申请，申请完成后再开启预览
		if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissionCall.launch(android.Manifest.permission.CAMERA)
			return
		}
		//使用post，保证当前的surfaceView中创建surface完成
		mBinding.surfaceView.post {
			openCamera()
		}
	}
	
	override fun initEvent() {
		mBinding.tvDisplay.singleClick {
			currentRotation++
			if (currentRotation > 11) {
				currentRotation = 0
			}
			mCameraDevice?.setParameter(CameraParameter.DISPLAY_TRANSFORM, DisplayTransformState.orientationToState(currentRotation))
		}
	}
	
	override fun onStart() {
		super.onStart()
		mCameraDevice?.startPreview()
	}
	
	override fun onStop() {
		super.onStop()
		mCameraDevice?.stopPreview()
	}
	
	override fun onDestroy() {
		super.onDestroy()
		mCameraDevice?.close()
		mCameraDevice = null
	}
	
	private fun openCamera() {
		//获取uvc摄像头列表
		lifecycleScope.launch(Dispatchers.IO) {
			val usbDevices = CameraUvcManager.getCameraDevices()
			if (usbDevices.isNullOrEmpty()) return@launch
			val cameraDevice = runCatching { CameraUvcManager.openCameraSync(usbDevices[0]) }.getOrNull()
			if (cameraDevice == null) {//报错说明打开失败
				return@launch
			}
			//赋值当前的对象
			initParameter(cameraDevice)
			//设置预览控件
			cameraDevice.setDisplaySurface(mBinding.surfaceView)
			//设置监听
			cameraDevice.setPreviewListener { width: Int, height: Int, frame: ByteBuffer ->
				Log.d("CameraActivity", "${width}*${height} : ${frame.capacity()}")
			}
			
			cameraDevice.startPreview()
			this@CameraActivity.mCameraDevice = cameraDevice
		}
	}
	
	private fun initParameter(cameraDevice: ICameraDevice) {
		Log.d("CameraActivity", "曝光度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.EXPOSURE))}")
		Log.d("CameraActivity", "亮度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.BRIGHTNESS))}")
		Log.d("CameraActivity", "对比度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.CONTRAST))}")
		Log.d("CameraActivity", "增益值列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.GAIN))}")
		Log.d("CameraActivity", "饱和度列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.SATURATION))}")
		Log.d("CameraActivity", "分辨率列表:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.PREVIEW_SIZE))}")
		
		Log.d("CameraActivity", "当前自动曝光状态:${cameraDevice.getParameter(CameraParameter.AUTO_EXPOSURE)}")
		Log.d("CameraActivity", "当前曝光度:${cameraDevice.getParameter(CameraParameter.EXPOSURE)}")
		Log.d("CameraActivity", "当前亮度:${cameraDevice.getParameter(CameraParameter.BRIGHTNESS)}")
		Log.d("CameraActivity", "当前增益值:${cameraDevice.getParameter(CameraParameter.GAIN)}")
		Log.d("CameraActivity", "当前对比度:${cameraDevice.getParameter(CameraParameter.CONTRAST)}")
		Log.d("CameraActivity", "当前饱和度:${cameraDevice.getParameter(CameraParameter.SATURATION)}")
		
		
		Log.d("CameraActivity", "自动对焦支持:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_FOCUS))}")
		Log.d("CameraActivity", "焦距范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.FOCUS))}")
		Log.d("CameraActivity", "光圈范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.IRIS))}")
		Log.d("CameraActivity", "自动色值:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_HUE))}")
		Log.d("CameraActivity", "色值范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.HUE))}")
		Log.d("CameraActivity", "自动白平衡:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.AUTO_WHITE_BALANCE))}")
		Log.d("CameraActivity", "白平衡范围:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.WHITE_BALANCE))}")
		Log.d("CameraActivity", "场景模式:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.SCENE_MODE))}")
		Log.d("CameraActivity", "隐私模式支持:${GsonHelper.getHelper().modeToJson(cameraDevice.getSupportedParameter(CameraSupportParameters.PRIVACY))}")
		
		
		Log.d("CameraActivity", "自动对焦状态:${cameraDevice.getParameter(CameraParameter.AUTO_FOCUS)}")
		Log.d("CameraActivity", "焦距:${cameraDevice.getParameter(CameraParameter.FOCUS)}")
		Log.d("CameraActivity", "光圈:${cameraDevice.getParameter(CameraParameter.IRIS)}")
		Log.d("CameraActivity", "自动色值状态:${cameraDevice.getParameter(CameraParameter.AUTO_HUE)}")
		Log.d("CameraActivity", "色值:${cameraDevice.getParameter(CameraParameter.HUE)}")
		Log.d("CameraActivity", "自动白平衡状态:${cameraDevice.getParameter(CameraParameter.AUTO_WHITE_BALANCE)}")
		Log.d("CameraActivity", "白平衡:${cameraDevice.getParameter(CameraParameter.WHITE_BALANCE)}")
		Log.d("CameraActivity", "场景模式:${cameraDevice.getParameter(CameraParameter.SCENE_MODE)}")
		Log.d("CameraActivity", "隐私模式状态:${cameraDevice.getParameter(CameraParameter.PRIVACY)}")
	}
}
```

