# UsbCamera SDK 接入文档

> Android USB UVC 摄像头开发套件 · 包名 `com.rain.uvc` · 当前版本 `1.0.0`
>
> 依赖坐标：`io.github.yfograin:uvc:1.0.0`（Maven Central）

---

## 目录

- [一、SDK 简介](#一sdk-简介)
- [二、接入准备](#二接入准备)
    - [2.1 引入依赖（Maven 仓库）](#21-引入依赖maven-仓库)
    - [2.2 混淆配置](#22-混淆配置)
    - [2.3 权限声明](#23-权限声明)
    - [2.4 设备要求](#24-设备要求)
- [三、快速上手](#三快速上手)
- [四、设备管理](#四设备管理)
    - [4.1 设备发现](#41-设备发现)
    - [4.2 打开设备](#42-打开设备)
    - [4.3 关闭设备](#43-关闭设备)
    - [4.4 热插拔监听](#44-热插拔监听)
- [五、视频预览](#五视频预览)
    - [5.1 设置预览画面](#51-设置预览画面)
    - [5.2 设置分辨率](#52-设置分辨率)
    - [5.3 开始/停止预览](#53-开始停止预览)
    - [5.4 预览帧数据回调](#54-预览帧数据回调)
    - [5.5 硬件按钮监听](#55-硬件按钮监听)
- [六、拍照](#六拍照)
- [七、视频录制](#七视频录制)
    - [7.1 录制生命周期](#71-录制生命周期)
    - [7.2 完整示例](#72-完整示例)
    - [7.3 API 详解](#73-api-详解)
- [八、参数控制](#八参数控制)
    - [8.1 查询支持的参数范围](#81-查询支持的参数范围)
    - [8.2 获取/设置参数](#82-获取设置参数)
    - [8.3 参数列表](#83-参数列表)
- [九、枚举类型说明](#九枚举类型说明)
    - [UvcPreviewFormat - 预览解码格式](#uvcpreviewformat---预览解码格式)
    - [UvcDataFormat - 帧回调数据格式](#uvcdataformat---帧回调数据格式)
- [十、数据模型](#十数据模型)
- [十一、错误处理](#十一错误处理)
- [十二、注意事项与 FAQ](#十二注意事项与-faq)
- [十三、版本说明](#十三版本说明)

---

## 一、SDK 简介

UsbCamera SDK 是一套面向 Android 平台的 USB UVC 摄像头控制开发套件，提供从设备发现、预览、拍照、录像到参数调节的完整能力，帮助开发者快速接入 USB 外接摄像头。

### 核心能力

- **设备管理** — UVC 设备枚举、USB 权限自动申请、设备打开/关闭、热插拔监听
- **视频预览** — 支持 Surface / TextureView / SurfaceView 三种方式渲染预览画面
- **预览数据回调** — 可获取实时帧数据（支持 NV21、RGBA、RGB 等多种格式），用于算法分析
- **拍照** — 一键抓取当前帧
- **视频录制** — 支持 H.264 视频编码 + AAC 音频编码，输出 MP4 文件
- **参数控制** — 亮度、对比度、曝光、白平衡、缩放、对焦、镜像、旋转等 17+ 项参数的查询与设置

### 技术指标

| 项目 | 说明 |
|------|------|
| 当前版本 | `1.0.0` |
| 依赖坐标 | `io.github.yfograin:uvc:1.0.0` |
| 最低版本 | Android 6.0（API 23） |
| 支持架构 | `arm64-v8a`、`armeabi-v7a` |
| 开发语言 | Kotlin / Java 均可调用 |
| 依赖要求 | Kotlin Coroutines（协程） |

> 完整的版本记录、版本号规则与升级建议见 [十三、版本说明](#十三版本说明)。

---

## 二、接入准备

### 2.1 引入依赖（Maven 仓库）

SDK 已发布至 **Maven Central**，无需手动下载 aar 文件，直接通过 Gradle 坐标引入即可。

| 项目 | 值 |
|------|-----|
| groupId | `io.github.yfograin` |
| artifactId | `uvc` |
| 当前版本 | `1.0.0` |
| 托管仓库 | Maven Central |
| 完整坐标 | `io.github.yfograin:uvc:1.0.0` |

#### 第一步：配置仓库地址

Maven Central 为标准仓库，多数工程已默认包含。若你的工程启用了集中式仓库管理（`FAIL_ON_PROJECT_REPOS`），请确认 `settings.gradle` 中含有 `mavenCentral()`：

```groovy
// settings.gradle（Groovy DSL）
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

```kotlin
// settings.gradle.kts（Kotlin DSL）
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

> **国内网络加速：** 如拉取缓慢，可在 `mavenCentral()` 之前加入镜像仓库：
>
> ```groovy
> maven { url = "https://maven.aliyun.com/repository/public" }
> ```

#### 第二步：添加依赖

**方式一：Groovy DSL**

```groovy
// app/build.gradle
dependencies {
    implementation 'io.github.yfograin:uvc:1.0.0'
}
```

**方式二：Kotlin DSL**

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.yfograin:uvc:1.0.0")
}
```

**方式三：Version Catalog（推荐，便于多模块统一版本）**

```toml
# gradle/libs.versions.toml
[versions]
uvc = "1.0.0"

[libraries]
uvc = { module = "io.github.yfograin:uvc", version.ref = "uvc" }
```

```groovy
// app/build.gradle
dependencies {
    implementation libs.uvc
}
```

> **协程依赖（重要）：** SDK 内部以 `implementation` 方式引入协程，**不会**向接入方暴露编译期 API。由于 `openCamera()`、`stopRecord()` 是 suspend 函数，请在 App 中自行声明协程依赖：
>
> ```groovy
> implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2'
> ```

#### 第三步：ABI 配置

SDK 内置 native 动态库，仅提供 `arm64-v8a` 与 `armeabi-v7a` 两个架构。如需控制安装包体积，可在 App 侧显式声明：

```groovy
// app/build.gradle
android {
    defaultConfig {
        ndk {
            abiFilters "arm64-v8a", "armeabi-v7a"
        }
    }
}
```

> **注意：** 不提供 `x86` / `x86_64` 版本，无法在标准 Android 模拟器上运行，请使用支持 USB Host 的真机调试。

#### 备选方案：源码依赖

如需调试或二次开发 SDK 源码，也可将模块以源码方式引入：

```groovy
// settings.gradle
include ':libusbcamera'

// app/build.gradle
dependencies {
    implementation project(':libusbcamera')
}
```

### 2.2 混淆配置

**接入方无需任何额外配置。**

SDK 已通过 `consumerProguardFiles` 将混淆规则内置在 AAR 中，Gradle 会在构建 App 时自动合并生效。即使开启 `minifyEnabled true`，也不需要手动往 App 的 `proguard-rules.pro` 添加任何规则。

SDK 的 native 层采用 JNI 静态注册（按类名 + 方法名查找），且预览帧、按钮事件由 native 层通过方法名反向回调 Java 层。随包下发的规则已完整覆盖这些不可混淆的关键面：

```proguard
# JNI 桥接类：native 方法名不可混淆，否则 UnsatisfiedLinkError
-keep class com.rain.uvc.bridge.UvcNativeBridge {
    native <methods>;
}

# 数据模型与预览格式
-keep class com.rain.uvc.mode.** { *; }
-keep class com.rain.uvc.parameters.UvcPreviewFormat { *; }
-keep class com.rain.uvc.parameters.UvcPreviewFormat* { *; }

# native 反向回调的接口
-keep interface com.rain.uvc.listener.IFrameListener
-keep interface com.rain.uvc.listener.IButtonListener

-keepnames class * implements com.rain.uvc.listener.IFrameListener
-keepnames class * implements com.rain.uvc.listener.IButtonListener

# 保留回调方法名供 JNI GetMethodID 查找
-keepclassmembers class * implements com.rain.uvc.listener.IFrameListener {
    public void onFrame(java.nio.ByteBuffer, int, int);
}

-keepclassmembers class * implements com.rain.uvc.listener.IButtonListener {
    public void buttonClick(int, int);
}
```

> **说明：** 上述规则仅供了解，**无需复制到你的工程中**。规则会随 AAR 自动下发，你实现的 `IFrameListener` / `IButtonListener` 子类也在保留范围内。
>
> 如使用**源码依赖**方式接入，规则同样通过模块的 `consumerProguardFiles` 生效。

### 2.3 权限声明

在你的 `AndroidManifest.xml` 中添加以下权限（SDK 已自动合并基础权限）：

```xml
<!-- 必需权限 -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- 录制音频时需要 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**权限说明：**

| 权限 | 是否必需 | 说明 |
|------|---------|------|
| `CAMERA` | **必需** | 需在运行时动态申请，打开摄像头前必须已授权 |
| `RECORD_AUDIO` | 可选 | 仅在录制视频且需要录音时使用 |
| USB 设备访问权限 | 自动 | 由 SDK 内部自动弹窗申请，无需手动处理 |

### 2.4 设备要求

- Android 设备需支持 **USB Host** 模式
- 外接摄像头需符合 **UVC（USB Video Class）** 协议标准

---

## 三、快速上手

以下是一个最简完整流程示例（Kotlin）：

```kotlin
class CameraActivity : AppCompatActivity() {

    private var camera: NativeUsbDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        // 运行时申请 CAMERA 权限（略）
    }

    /** 第一步：发现并打开设备 */
    fun openDevice() {
        lifecycleScope.launch {
            // 1. 发现 UVC 摄像头
            val devices = CameraControlHelper.loadUvcDevices(this@CameraActivity)
            val usbDevice = devices?.firstOrNull() ?: return@launch

            // 2. 打开设备（内部自动申请 USB 权限）
            val result = CameraControlHelper.openCamera(
                this@CameraActivity, usbDevice, timeout = 10000
            )
            camera = result.getOrNull() ?: run {
                Log.e("Camera", "打开失败: ${result.exceptionOrNull()?.message}")
                return@launch
            }

            // 3. 设置预览画面
            camera?.setDisplaySurface(findViewById<TextureView>(R.id.textureView))

            // 4. 设置分辨率（先查询支持的分辨率再设置）
            camera?.setPreviewSize(1280, 720, UvcPreviewFormat.MJPEG)

            // 5. 开始预览
            camera?.startPreview()
        }
    }

    /** 关闭设备 */
    fun closeDevice() {
        camera?.stopPreview()
        camera?.close()
        camera = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeDevice()
    }
}
```

**Java 调用说明：** `CameraControlHelper` 的静态方法均标注了 `@JvmStatic`，可直接通过 `CameraControlHelper.loadUvcDevices(context)` 调用。注意 `openCamera` 是挂起函数（suspend），Java 中需通过协程桥接调用。

---

## 四、设备管理

### 4.1 设备发现

#### 扫描 UVC 设备

```kotlin
val devices: List<UsbDevice>? = CameraControlHelper.loadUvcDevices(context)
```

扫描当前已连接的所有 UVC 摄像头设备。返回 `null` 表示 UsbManager 不可用，返回空列表表示未检测到设备。

#### 扫描 V4L2 设备

```kotlin
val paths: Array<String>? = CameraControlHelper.loadV4L2Devices()
```

获取系统中可用的 V4L2 视频设备路径（如 `/dev/video0`）。

#### 检查设备是否为 UVC 摄像头

```kotlin
val isUvc: Boolean = CameraControlHelper.checkDeviceUvc(usbDevice)
```

判断给定的 USB 设备是否是 UVC 摄像头类型。

#### 根据厂商 ID / 产品 ID 查找设备

```kotlin
val device: UsbDevice? = CameraControlHelper.getDeviceForId(context, vendorId, productId)
```

当你已知目标设备的 vendorId 和 productId 时，可直接定位设备。

### 4.2 打开设备

#### 方式一：通过 UsbDevice 打开（推荐）

```kotlin
// suspend 函数，需在协程中调用
val result: Result<NativeUsbDevice> = CameraControlHelper.openCamera(
    context = context,
    usbDevice = usbDevice,
    timeout = 10000L   // USB 权限申请超时时间，默认 10 秒
)
```

调用后 SDK 会自动弹出 USB 权限授权对话框。用户点击允许后，返回 `NativeUsbDevice` 实例。

```kotlin
// 处理结果
result.onSuccess { camera ->
    // 打开成功，camera 即为设备操作对象
}.onFailure { error ->
    // 打开失败，error.message 包含错误原因
}
```

#### 方式二：通过 V4L2 路径打开

```kotlin
// suspend 函数，需在协程中调用
val result: Result<NativeUsbDevice> = CameraControlHelper.openCamera(
    context = context,
    videoPath = "/dev/video0"
)
```

> **重要：** `openCamera` 是 **suspend 挂起函数**，必须在协程作用域中调用（如 `lifecycleScope.launch { }`）。

### 4.3 关闭设备

```kotlin
camera.close()
```

关闭摄像头并释放所有资源（停止预览、释放录制引擎、关闭 USB 连接、解注册广播）。调用后该实例不可复用。

### 4.4 热插拔监听

```kotlin
camera.setDetachedCloseListener {
    // USB 摄像头被物理拔出
    // SDK 会自动关闭设备并触发此回调
    // 建议在此更新 UI 状态
    runOnUiThread {
        Toast.makeText(this, "摄像头已断开", Toast.LENGTH_SHORT).show()
    }
}
```

当 USB 摄像头被拔出时，SDK 会自动执行关闭操作，随后触发此回调通知业务层。传入 `null` 可移除监听。

---

## 五、视频预览

### 5.1 设置预览画面

SDK 支持三种方式设置预览目标，选择其一即可：

```kotlin
// 方式一：TextureView（推荐，支持动画变换）
camera.setDisplaySurface(textureView)

// 方式二：SurfaceView（性能更好）
camera.setDisplaySurface(surfaceView)

// 方式三：直接传入 Surface 对象
camera.setDisplaySurface(surface)
```

> **提示：** 预览过程中可以随时切换 Surface，SDK 会自动停止再恢复预览。

### 5.2 设置分辨率

```kotlin
// 先查询设备支持的分辨率
val supportSizes: Array<UvcCameraSupportSize>? =
    camera.getSupportParameters(UvcSupportParameter.PREVIEW_SIZE)

// 打印支持的分辨率
supportSizes?.forEach { item ->
    Log.d("Camera", "格式: ${item.format}, 分辨率列表:")
    item.sizes.forEach { size ->
        Log.d("Camera", "  ${size.width} x ${size.height}")
    }
}

// 设置分辨率和解码格式
val success: Boolean = camera.setPreviewSize(1280, 720, UvcPreviewFormat.MJPEG)
```

**参数说明：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `width` | `Int` | 预览宽度（像素） |
| `height` | `Int` | 预览高度（像素） |
| `format` | `UvcPreviewFormat` | 解码格式，常用 `MJPEG` 或 `YUY2` |

> **注意：** 必须在 `startPreview()` 之前调用。设置的分辨率必须是设备支持的值。

### 5.3 开始/停止预览

```kotlin
// 开始预览
val started: Boolean = camera.startPreview()

// 停止预览
val stopped: Boolean = camera.stopPreview()
```

### 5.4 预览帧数据回调

如果需要获取实时帧数据（例如用于算法分析、人脸识别等），可设置帧数据监听：

```kotlin
// 设置帧回调，指定回调数据格式
camera.setPreviewListener(object : IFrameListener {
    override fun onFrame(frame: ByteBuffer, width: Int, height: Int) {
        // frame: 帧数据（DirectByteBuffer，指向 native 内存）
        // 在此回调中处理或拷贝数据
        // 注意：不要持有 frame 引用，回调返回后 buffer 可能被复用
    }
}, UvcDataFormat.NV21)  // 可选：NV21、RGBA、RGB、BGR、YUY2、MJPEG

// 移除帧回调
camera.setPreviewListener(null)
```

> **注意：** `frame` 是指向 native 内存的 DirectByteBuffer，回调返回后可能被底层复用。如需保留数据，请在回调内完成拷贝。

### 5.5 硬件按钮监听

部分 USB 摄像头带有物理按钮，可通过以下方式监听：

```kotlin
camera.setButtonListener(object : IButtonListener {
    override fun buttonClick(type: Int, state: Int) {
        Log.d("Camera", "按钮事件: type=$type, state=$state")
    }
})
```

---

## 六、拍照

```kotlin
// 预览中调用，抓取当前帧
val photoData: ByteArray? = camera.takePicture()

if (photoData != null) {
    // 将帧数据保存为图片文件
    val file = File(getExternalFilesDir(null), "photo_${System.currentTimeMillis()}.jpg")
    file.writeBytes(photoData)
    Log.d("Camera", "拍照成功: ${file.absolutePath}")
}
```

> 必须在预览状态下调用，返回当前帧的原始数据。

---

## 七、视频录制

### 7.1 录制生命周期

录制功能需要严格按照以下顺序调用：

1. 调用 `prepareRecord(fps)` 初始化录制管线 **（必须在 startPreview 之前）**
2. 调用 `startPreview()` 开始预览
3. 调用 `startRecord()` 开始录制
4. 调用 `stopRecord()` 停止录制并获取文件路径
5. 可多次重复 3-4 步进行多段录制
6. 调用 `releaseRecord()` 释放录制资源

### 7.2 完整示例

```kotlin
// ====== 1. 初始化录制（在 startPreview 之前）======
val prepared = camera.prepareRecord(fps = 30)
if (!prepared) {
    Log.e("Camera", "录制初始化失败")
    return
}

// ====== 2. 开始预览 ======
camera.startPreview()

// ====== 3. 开始录制 ======
val recording = camera.startRecord(
    context = this,
    outputPath = null,       // null 则自动生成路径
    useAudio = true,         // 是否录制音频（需 RECORD_AUDIO 权限）
    fps = 30                 // 录制帧率
)

// ====== 4. 停止录制 ======
lifecycleScope.launch {
    val filePath: String? = camera.stopRecord()  // suspend 函数
    if (filePath != null) {
        Log.d("Camera", "录制完成: $filePath")
    }
}

// ====== 5. 释放录制资源 ======
camera.releaseRecord()
```

### 7.3 API 详解

#### `prepareRecord(fps: Int): Boolean`

初始化录制管线，将录制用的 Surface 绑定到 native 渲染管线。此方法决定了 GPU 渲染路径（FBO 通路），一旦调用后在整个预览周期内固定不变。

| 参数 | 类型 | 说明 |
|------|------|------|
| `fps` | `Int` | 目标录制帧率 |

> **必须在 `startPreview()` 之前调用**，预览中或录制中调用会直接返回 `false`。

#### `startRecord(context, outputPath?, useAudio, fps): Boolean`

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `context` | `Context` | - | Android Context |
| `outputPath` | `String?` | `null` | 输出文件路径。为 null 时自动保存到 `{filesDir}/Video/Video_{时间戳}.mp4` |
| `useAudio` | `Boolean` | `false` | 是否录制音频 |
| `fps` | `Int` | `30` | 录制帧率 |

必须在预览状态下调用。返回 `true` 表示录制已启动。

#### `suspend stopRecord(): String?`

停止录制并等待文件写入完成。返回录制文件的绝对路径，如果当前未在录制则返回 `null`。

> **suspend 函数**，需在协程中调用。

#### `releaseRecord()`

释放所有录制资源（包括绑定到 native 管线的 Surface）。调用后如需重新录制，需重新走 `prepareRecord` -> `startPreview` 流程。

---

## 八、参数控制

SDK 提供统一的参数查询与设置接口，支持 17+ 项 UVC 标准参数。

### 8.1 查询支持的参数范围

```kotlin
// 查询亮度的可调范围
val range: IntRange? = camera.getSupportParameters(UvcSupportParameter.BRIGHTNESS)
if (range != null) {
    Log.d("Camera", "亮度范围: ${range.first} ~ ${range.last}")
}

// 查询是否支持自动曝光
val hasAutoExposure: Boolean? =
    camera.getSupportParameters(UvcSupportParameter.AUTO_EXPOSURE)

// 查询支持的分辨率
val sizes: Array<UvcCameraSupportSize>? =
    camera.getSupportParameters(UvcSupportParameter.PREVIEW_SIZE)
```

### 8.2 获取/设置参数

```kotlin
// 获取当前亮度
val brightness: Int? = camera.getParameter(UvcCameraParameter.BRIGHTNESS)

// 设置亮度为 60
camera.setParameter(UvcCameraParameter.BRIGHTNESS, 60)

// 开启自动曝光
camera.setParameter(UvcCameraParameter.AUTO_EXPOSURE, true)

// 设置预览旋转 90 度
camera.setParameter(UvcCameraParameter.ORIENTATION, 90)

// 开启镜像
camera.setParameter(UvcCameraParameter.MIRROR, true)

// 获取当前分辨率
val currentSize: UvcCameraSize? = camera.getParameter(UvcCameraParameter.PREVIEW_SIZE)
```

### 8.3 参数列表

| 参数 Key | 值类型 | 查询范围类型 | 说明 |
|----------|--------|-------------|------|
| `BRIGHTNESS` | `Int` | `IntRange` | 亮度 |
| `CONTRAST` | `Int` | `IntRange` | 对比度 |
| `SATURATION` | `Int` | `IntRange` | 饱和度 |
| `HUE` | `Int` | `IntRange` | 色调 |
| `GAIN` | `Int` | `IntRange` | 增益 |
| `EXPOSURE` | `Int` | `IntRange` | 曝光值 |
| `ZOOM` | `Int` | `IntRange` | 缩放级别 |
| `FOCUS` | `Int` | `IntRange` | 焦距 |
| `IRIS` | `Int` | `IntRange` | 光圈 |
| `WHITE_BALANCE` | `Int` | `IntRange` | 白平衡色温 |
| `AUTO_EXPOSURE` | `Boolean` | `Boolean` | 自动曝光开关 |
| `AUTO_FOCUS` | `Boolean` | `Boolean` | 自动对焦开关 |
| `AUTO_HUE` | `Boolean` | `Boolean` | 自动色调开关 |
| `AUTO_WHITE_BALANCE` | `Boolean` | `Boolean` | 自动白平衡开关 |
| `PRIVACY` | `Boolean` | `Boolean` | 隐私模式（遮盖镜头） |
| `ORIENTATION` | `Int` | - | 预览旋转角度（度数） |
| `MIRROR` | `Boolean` | - | 水平镜像 |
| `PREVIEW_SIZE` | `UvcCameraSize` | `Array<UvcCameraSupportSize>` | 预览分辨率 |

> **提示：** 不是所有 USB 摄像头都支持全部参数。调用 `getSupportParameters()` 返回 `null` 表示该参数不被当前设备支持。设置不支持的参数会返回 `false`。

---

## 九、枚举类型说明

### UvcPreviewFormat - 预览解码格式

用于 `setPreviewSize()` 的 `format` 参数，指定摄像头的采集格式。

| 枚举值 | 编码 | 说明 |
|--------|------|------|
| `UvcPreviewFormat.MJPEG` | 6 | Motion JPEG 压缩格式（推荐，高分辨率首选） |
| `UvcPreviewFormat.YUY2` | 1 | YUY2 未压缩格式（低延迟） |
| `UvcPreviewFormat.NV21` | 2 | NV21 格式 |
| `UvcPreviewFormat.NV12` | 3 | NV12 格式 |
| `UvcPreviewFormat.RGB` | 5 | RGB 格式 |
| `UvcPreviewFormat.BGR` | 0 | BGR 格式 |
| `UvcPreviewFormat.JPEG` | 7 | JPEG 格式 |
| `UvcPreviewFormat.YUV420SP` | 8 | YUV 4:2:0 Semi-Planar |

### UvcDataFormat - 帧回调数据格式

用于 `setPreviewListener()`，指定回调中帧数据的输出格式。

| 枚举值 | 编码 | 说明 |
|--------|------|------|
| `UvcDataFormat.NV21` | 2 | NV21 格式（默认值，Android 标准格式） |
| `UvcDataFormat.RGBA` | 4 | RGBA 格式 |
| `UvcDataFormat.RGB` | 5 | RGB 格式 |
| `UvcDataFormat.BGR` | 0 | BGR 格式（OpenCV 常用） |
| `UvcDataFormat.YUY2` | 1 | YUY2 格式 |
| `UvcDataFormat.MJPEG` | 6 | MJPEG 原始数据 |

---

## 十、数据模型

### UvcCameraSize

```kotlin
// com.rain.uvc.mode.UvcCameraSize
data class UvcCameraSize(
    val width: Int,    // 宽度（像素）
    val height: Int    // 高度（像素）
)
```

表示摄像头分辨率或预览尺寸。

### UvcCameraSupportSize

```kotlin
// com.rain.uvc.mode.UvcCameraSupportSize
data class UvcCameraSupportSize(
    val format: UvcPreviewFormat,       // 格式类型
    val sizes: List<UvcCameraSize>     // 该格式下支持的分辨率列表
)
```

表示某种格式下支持的所有分辨率。通过 `getSupportParameters(UvcSupportParameter.PREVIEW_SIZE)` 获取。

### IFrameListener

```java
// com.rain.uvc.listener.IFrameListener
public interface IFrameListener {
    /**
     * @param frame  帧数据（DirectByteBuffer）
     * @param width  帧宽度
     * @param height 帧高度
     */
    void onFrame(ByteBuffer frame, int width, int height);
}
```

预览帧数据回调接口。

### IButtonListener

```java
// com.rain.uvc.listener.IButtonListener
public interface IButtonListener {
    /**
     * @param type  按钮类型标识
     * @param state 按钮状态（按下/释放）
     */
    void buttonClick(int type, int state);
}
```

USB 摄像头硬件按钮事件回调接口。

---

## 十一、错误处理

`openCamera()` 返回 `Result` 类型，可能的失败原因：

| 异常类型 | 场景 | 建议处理 |
|----------|------|---------|
| `IllegalAccessException` | CAMERA 权限未授予 | 引导用户到设置页授权 |
| `IllegalAccessException` | USB 临时权限申请失败（用户拒绝或超时） | 提示用户重试 |
| `IllegalStateException` | 获取 USB 驱动信息失败 | 检查设备是否正常连接 |
| `IllegalStateException` | 打开 USB 设备驱动失败 | 设备可能被其他应用占用 |
| `IllegalStateException` | USB 设备 native 层打开失败 | 设备不兼容或驱动异常 |

```kotlin
val result = CameraControlHelper.openCamera(context, usbDevice)
result.onFailure { error ->
    when (error) {
        is IllegalAccessException -> {
            // 权限问题
            Log.e("Camera", "权限错误: ${error.message}")
        }
        is IllegalStateException -> {
            // 设备问题
            Log.e("Camera", "设备错误: ${error.message}")
        }
    }
}
```

---

## 十二、注意事项与 FAQ

### 1. 线程与协程

- `openCamera()` 和 `stopRecord()` 是 **suspend 函数**，需要在协程中调用
- 其余方法（如 `startPreview()`、`setParameter()`）可在任意线程调用
- `IFrameListener.onFrame()` 回调在 **native 线程**中执行，如需更新 UI 请切换到主线程

### 2. 录制的调用时序

- `prepareRecord()` **必须**在 `startPreview()` 之前调用，否则 FBO 渲染路径无法正确建立
- 在预览过程中可以多次 `startRecord()` / `stopRecord()` 进行多段录制
- 如果不需要录制功能，无需调用 `prepareRecord()`，预览将使用更轻量的直绘路径

### 3. Surface 生命周期

- 使用 `TextureView` 时，请确保 `surfaceTexture` 已就绪后再调用 `setDisplaySurface()`
- 建议监听 `TextureView.SurfaceTextureListener`，在 `onSurfaceTextureAvailable` 中设置
- SDK 内部会管理 Surface 的释放，开发者无需手动释放

### 4. 帧数据内存安全

- `IFrameListener.onFrame()` 的 `ByteBuffer` 是 DirectBuffer，指向 native 内存
- 回调返回后 buffer **可能被底层复用**，如需保留数据请在回调内完成拷贝
- 避免在回调中执行耗时操作，以免阻塞帧数据管线

### 5. 设备生命周期管理

- 建议在 `Activity.onDestroy()` 或 `Fragment.onDestroyView()` 中调用 `close()` 释放资源
- `close()` 调用后 `NativeUsbDevice` 实例不可复用，需重新 `openCamera()`
- 设备被物理拔出后 SDK 会自动关闭，通过 `setDetachedCloseListener` 获取通知

### 6. 常见问题

| 问题 | 解答 |
|------|------|
| 设置分辨率失败 | 请先通过 `getSupportParameters(UvcSupportParameter.PREVIEW_SIZE)` 查询支持的分辨率，确保设置的值在支持列表中 |
| 设置参数返回 false | 该参数可能不被当前摄像头支持，先用 `getSupportParameters()` 确认是否支持 |
| 预览黑屏 | 检查是否正确设置了 Surface，以及分辨率是否匹配设备支持的值 |
| 录制文件为空 | 确保 `prepareRecord()` 在 `startPreview()` 之前调用 |
| 多个摄像头同时使用 | 每个摄像头对应一个独立的 `NativeUsbDevice` 实例，可同时操作 |
| 依赖拉取不到 | 确认 `settings.gradle` 中包含 `mavenCentral()`，参见 [2.1 引入依赖](#21-引入依赖maven-仓库) |
| 模拟器上启动崩溃 | SDK 仅提供 arm 架构 native 库，请使用真机调试 |
| 混淆后回调不触发 | SDK 已随包下发混淆规则（见 [2.2 混淆配置](#22-混淆配置)）。若仍异常，请检查是否自定义了 `-dontusemixedcaseclassnames` 等全局规则覆盖了 keep，或查看 `build/outputs/mapping/release/configuration.txt` 确认规则已合并 |

---

## 十三、版本说明

### 版本号规则

SDK 遵循语义化版本规范 `主版本.次版本.修订号`：

| 位 | 含义 | 升级影响 |
|----|------|---------|
| 主版本 | 存在不兼容的 API 变更 | 需按迁移说明调整代码 |
| 次版本 | 向后兼容的能力新增 | 可直接升级 |
| 修订号 | 向后兼容的问题修复 | 可直接升级 |

### 版本记录

#### 1.0.0（首个正式版）

- 首次发布至 Maven Central，坐标 `io.github.yfograin:uvc:1.0.0`
- **设备管理**：UVC 设备枚举、V4L2 设备枚举、USB 权限自动申请、设备打开/关闭、热插拔自动回收
- **视频预览**：支持 `Surface` / `TextureView` / `SurfaceView` 三种渲染目标，支持预览中动态切换
- **预览回调**：`IFrameListener` 支持 NV21 / RGBA / RGB / BGR / YUY2 / MJPEG 六种输出格式
- **拍照**：`takePicture()` 抓取当前帧
- **视频录制**：H.264 视频编码 + AAC 音频编码，输出 MP4；支持单次预览内多段录制
- **参数控制**：亮度、对比度、饱和度、色调、增益、曝光、缩放、对焦、光圈、白平衡、隐私模式、旋转、镜像等 18 项参数的查询与设置
- **硬件按钮**：`IButtonListener` 监听摄像头物理按键
- **开箱即用**：内置 consumer ProGuard 规则，开启混淆无需额外配置

### 兼容性说明

| 项目 | 支持范围 |
|------|---------|
| Android 版本 | Android 6.0（API 23）及以上 |
| 编译 SDK | compileSdk 36 |
| CPU 架构 | `arm64-v8a`、`armeabi-v7a` |
| Java 编译级别 | Java 17 |
| Kotlin | 2.2.20（协程 1.10.2） |
| 运行时传递依赖 | `androidx.appcompat`、`kotlinx-coroutines-android`（以 runtime 作用域写入 POM） |

### 升级建议

- 升级前请查阅本节版本记录，确认是否包含不兼容变更
- 建议通过 Version Catalog 统一管理版本号，避免多模块依赖版本冲突
- 主版本升级时，SDK 将在版本记录中同步提供迁移说明
- 不建议在同一工程中同时使用 Maven 依赖与源码依赖，以免 native 库重复打包
