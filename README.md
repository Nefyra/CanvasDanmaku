# CanvasDanmaku - 高性能 Android 弹幕库

CanvasDanmaku 是一个轻量级、高性能的 Android 弹幕库，支持滚动弹幕、顶部/底部固定弹幕以及带有复杂动画的高级弹幕。它通过位图缓存和批量绘制技术实现流畅的渲染，并提供动态帧率控制以平衡性能与功耗。

## 特性

- 支持三种基础弹幕类型：**滚动弹幕**、**顶部部弹幕**、**底部弹幕**
- 支持**高级弹幕**：可自定义移动路径、旋转、缩放、透明度渐变，使用 `Matrix` 或欧拉角变换
- 内置位图缓存机制，大幅降低绘制开销
- 批量绘制优化，减少 CPU/GPU 负担
- 动态帧率调节，根据弹幕数量自适应调整刷新率
- 轨道管理：自动分配轨道，支持“海量模式”（massive mode）
- 自发送弹幕标识（绿色边框）
- 完全可自定义的外观：字体大小、颜色、描边宽度/颜色等
- 安全区域设置，避免弹幕遮挡重要内容

## 快速开始

### 1. 添加依赖

将 `canvasdanmaku` 模块的源码复制到你的项目中，或将其作为子模块引入。  

### 2. 在布局文件中添加 DanmakuView

```xml
<io.github.Nefyra.canvasdanmaku.DanmakuView
    android:id="@+id/danmakuView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 3. 在 Activity/Fragment 中初始化并配置

```kotlin
val danmakuView = findViewById<DanmakuView>(R.id.danmakuView)

// 自定义配置
danmakuView.option = DanmakuOption(
    fontSize = 18f,                 // 字体大小 (sp)
    textColor = Color.WHITE,        // 文字颜色 (ARGB)
    strokeWidth = 2f,               // 描边宽度 (px)
    strokeColor = Color.BLACK,      // 描边颜色 (ARGB)
    durationMillis = 8000,          // 滚动弹幕持续时间 (ms)
    trackArea = 0.8f,               // 轨道区域占视图高度比例 (0-1)
    safeArea = true,                // 是否开启安全区域
    useBitmapCache = true,          // 是否启用位图缓存
    enableDynamicFrameRate = true,  // 是否启用动态帧率
    minFrameRate = 24,              // 最小帧率
    maxFrameRate = 60               // 最大帧率
)
```

## 添加弹幕

### 滚动弹幕

滚动弹幕从右向左移动，到达左边缘后消失。

```kotlin
danmakuView.addDanmaku(
    text = "这是一条滚动弹幕",
    color = Color.YELLOW,
    selfSend = false,       // 是否为自己发送的弹幕（绿色边框）
    hasStroke = true,       // 是否绘制文字描边
    type = DanmakuType.SCROLL
)
```

### 顶部弹幕

顶部弹幕会固定在屏幕顶部中央，持续显示指定时间后自动消失。

```kotlin
danmakuView.addDanmaku(
    text = "顶部通知",
    type = DanmakuType.TOP
)
```

### 底部弹幕

底部弹幕会固定在屏幕底部中央，用法与顶部弹幕相同。

```kotlin
danmakuView.addDanmaku(
    text = "底部公告",
    type = DanmakuType.BOTTOM
)
```

### 高级弹幕

高级弹幕支持复杂的动画效果，例如平移、旋转、透明度变化等。你需要构建一个 `SpecialDanmakuParams` 对象来描述动画参数。

```kotlin
// 创建平移 Tween：从 (0,0) 移动到 (0.5,0.5)（相对屏幕尺寸）
val translateX = SpecialDanmakuParams.Tween(0f, 0.5f)
val translateY = SpecialDanmakuParams.Tween(0f, 0.5f)

// 创建透明度 Tween（可选）
val alphaTween = SpecialDanmakuParams.Tween(1f, 0f)

val params = SpecialDanmakuParams(
    duration = 3000L,
    alphaTween = alphaTween,
    translateXTween = translateX,
    translateYTween = translateY,
    translationDuration = 2500L,
    translationStartDelay = 500L,
    rotateZ = 45f,          // Z 轴旋转角度（度数）
    matrix = null,          // 或使用自定义 Matrix
    easingType = AccelerateDecelerateInterpolator()
)

danmakuView.addSpecialDanmaku(
    text = "高级弹幕",
    color = Color.CYAN,
    selfSend = false,
    hasStroke = true,
    specialParams = params
)
```

> 注意：高级弹幕的坐标是相对于屏幕宽高的百分比，范围为 `[0,1]`。

## 时间轴弹幕（配合视频播放器）

`TimelineDanmakuController` 用于将弹幕与视频播放时间轴同步。它按秒分组弹幕，根据播放器当前进度自动发送对应时间点的弹幕。

### 基本用法

```kotlin
// 1. 创建控制器并关联 DanmakuView
val timelineController = TimelineDanmakuController(danmakuView)

// 2. 准备时间轴弹幕数据
val danmakus = listOf(
    TimelineDanmaku(5.5, "视频开始5.5秒的弹幕", Color.WHITE, 0),      // 滚动弹幕
    TimelineDanmaku(10.0, "10秒的顶部公告", Color.YELLOW, 1),          // 顶部固定
    TimelineDanmaku(15.2, "15秒的底部提示", Color.CYAN, 2),            // 底部固定
    TimelineDanmaku(30.8, "30秒的高级弹幕", Color.MAGENTA, 0)          // 滚动弹幕
)

// 3. 加载弹幕数据（自动按秒分组）
timelineController.loadDanmakus(danmakus)

// 4. 在播放器进度回调中调用
videoPlayer.setOnProgressListener { currentMillis ->
    val currentSecond = (currentMillis / 1000).toInt()
    timelineController.sendBySecondIndex(currentSecond)
}
```

### 批量发送未发送的弹幕

当用户拖动进度条时，需要发送跳过的所有弹幕：

```kotlin
videoPlayer.setOnSeekCompleteListener { seekPosition ->
    val targetSecond = (seekPosition / 1000).toInt()
    // 发送从起始位置到目标位置的所有未发送弹幕
    timelineController.sendUpToSecond(targetSecond)
}
```

### 重置与清空

```kotlin
// 重置发送标记（不清除弹幕数据）
timelineController.reset()

// 清空所有弹幕数据和发送标记
timelineController.clear()

// 重新加载新视频的弹幕
timelineController.loadDanmakus(newDanmakus)
```

### 查询状态

```kotlin
// 获取已发送的秒数
val sentCount = timelineController.getSentSecondCount()

// 获取包含弹幕的总秒数
val totalSeconds = timelineController.getTotalSecondsWithDanmaku()

// 获取指定秒内的弹幕数量
val count = timelineController.getDanmakuCountInSecond(10)

// 检查指定秒是否已发送
val isSent = timelineController.isSecondSent(10)
```

### TimelineDanmaku 数据模型

| 参数 | 类型 | 说明 |
|------|------|------|
| `timeSeconds` | Double | 弹幕出现时间（秒），支持小数点后多位，如 `5.5`、`10.25` |
| `text` | String | 弹幕文本内容 |
| `color` | Int | 弹幕颜色（ARGB 格式） |
| `type` | Int | 弹幕类型：`0`-滚动，`1`-顶部固定，`2`-底部固定 |

### 与播放器集成的完整示例

```kotlin
class PlayerActivity : AppCompatActivity() {

    private lateinit var danmakuView: DanmakuView
    private lateinit var timelineController: TimelineDanmakuController
    private var isSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        danmakuView = findViewById(R.id.danmakuView)
        
        // 配置弹幕样式
        danmakuView.option = DanmakuOption(
            fontSize = 18f,
            trackArea = 0.7f,
            useBitmapCache = true
        )
        
        // 初始化时间轴控制器
        timelineController = TimelineDanmakuController(danmakuView)
        
        // 加载弹幕数据（可从本地文件或服务端获取）
        loadDanmakusFromSource()
        
        // 绑定播放器
        setupPlayer()
    }
    
    private fun loadDanmakusFromSource() {
        // 从本地 JSON 或服务端解析弹幕
        val danmakus = parseDanmakuJson(danmakuJson)
        timelineController.loadDanmakus(danmakus)
    }
    
    private fun setupPlayer() {
        player.addOnProgressChangedListener { position, _ ->
            if (!isSeeking) {
                val currentSecond = (position / 1000).toInt()
                timelineController.sendBySecondIndex(currentSecond)
            }
        }
        
        player.addOnSeekCompleteListener {
            isSeeking = false
            val targetSecond = (it.position / 1000).toInt()
            timelineController.sendUpToSecond(targetSecond)
        }
        
        player.addOnSeekStartListener {
            isSeeking = true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timelineController.clear()
        danmakuView.clear()
    }
}
```

### 性能说明

- **按秒分组**：弹幕在加载时按秒分组，避免运行时重复计算
- **防重复发送**：每秒钟的弹幕只发送一次，通过 `sentSeconds` 集合记录
- **精确时间**：支持小数点后多位时间精度，但按整数秒触发（适合大多数播放器场景）

## 配置 DanmakuOption

`DanmakuOption` 用于全局控制弹幕的行为和外观：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `fontSize` | Float | 16f | 字体大小（sp） |
| `textColor` | Int | Color.WHITE | 文字颜色 |
| `strokeWidth` | Float | 1.5f | 描边宽度 |
| `strokeColor` | Int | Color.BLACK | 描边颜色 |
| `durationMillis` | Long | 10000 | 滚动弹幕存活时间（毫秒） |
| `staticDurationMillis` | Long | 5000 | 顶部/底部弹幕存活时间（毫秒） |
| `trackArea` | Float | 1.0f | 轨道区域占视图高度的比例 |
| `safeArea` | Boolean | true | 是否开启安全区域（避免遮挡） |
| `massiveMode` | Boolean | false | 巨量模式：找不到轨道时随机分配 |
| `lineHeight` | Float | 1.2f | 行高倍数 |
| `useBitmapCache` | Boolean | true | 是否启用弹幕位图缓存 |
| `useBatchPainter` | Boolean | true | 是否启用批量绘制优化 |
| `enableDynamicFrameRate` | Boolean | true | 是否启用动态帧率控制 |
| `minFrameRate` | Int | 24 | 动态帧率的最小值 |
| `maxFrameRate` | Int | 60 | 动态帧率的最大值 |
| `bezierX1, bezierY1, bezierX2, bezierY2` | Float | 0.42f, 0f, 0.58f, 1f | 贝塞尔曲线控制点（用于帧率调节曲线） |
| `hideTop` | Boolean | false | 是否隐藏顶部弹幕 |
| `hideBottom` | Boolean | false | 是否隐藏底部弹幕 |
| `hideSpecial` | Boolean | false | 是否隐藏高级弹幕 |

## 性能优化

### 位图缓存

当 `useBitmapCache = true` 时，每个弹幕的文本会被预先渲染成 `Bitmap` 并保存。绘制时直接绘制 Bitmap，避免重复的文本测量和描边绘制，大幅提升性能。

### 批量绘制

当弹幕数量较多（默认超过 5 条）且 `useBatchPainter = true` 时，`DanmakuBatchPainter` 会将所有可见弹幕合并到一张离屏 Bitmap 中，然后一次性绘制到屏幕上，减少 Canvas 绘制调用。

### 动态帧率控制

通过 `BezierFrameRateController` 根据当前可见弹幕数量动态调整帧率。弹幕数量少时降低帧率节省电量，数量多时提高帧率保证流畅。可以通过 `setBezierStyle` 或 `setCustomBezier` 自定义控制曲线。

```kotlin
danmakuView.setCustomBezier(
    0.25f,  // controlX1
    0.1f,   // controlY1
    0.75f,  // controlX2
    0.9f    // controlY2
)
```

## 清理资源

当不再需要弹幕视图时（如 Activity 销毁），务必调用 `clear()` 方法释放资源：

```kotlin
override fun onDestroy() {
    super.onDestroy()
    danmakuView.clear()
}
```

如果手动移除了 DanmakuView，其内部会在 `onDetachedFromWindow` 中自动清理资源。

## 完整示例

以下是一个简单的 Activity 示例，演示了如何添加各种弹幕：

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var danmakuView: DanmakuView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        danmakuView = findViewById(R.id.danmakuView)

        // 自定义配置
        danmakuView.option = DanmakuOption(
            fontSize = 20f,
            textColor = Color.WHITE,
            strokeWidth = 2f,
            strokeColor = Color.BLACK,
            trackArea = 0.7f,
            safeArea = true,
            useBitmapCache = true,
            enableDynamicFrameRate = true
        )

        // 添加滚动弹幕
        danmakuView.addDanmaku("Hello, 弹幕！")

        // 添加自发送弹幕
        danmakuView.addDanmaku("我发的弹幕", color = Color.GREEN, selfSend = true)

        // 添加顶部弹幕
        danmakuView.addDanmaku("置顶公告", type = DanmakuType.TOP)

        // 添加底部弹幕
        danmakuView.addDanmaku("底部消息", type = DanmakuType.BOTTOM)

        // 添加高级弹幕（从右下角飞到左上角）
        val specialParams = SpecialDanmakuParams(
            duration = 4000L,
            translateXTween = SpecialDanmakuParams.Tween(0.8f, 0.2f),
            translateYTween = SpecialDanmakuParams.Tween(0.8f, 0.2f),
            translationDuration = 3500L,
            rotateZ = 360f
        )
        danmakuView.addSpecialDanmaku("高级弹幕", color = Color.MAGENTA, specialParams = specialParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        danmakuView.clear()
    }
}
```

## 高级弹幕参数详解

`SpecialDanmakuParams` 中的 `Tween` 用于描述起始值和结束值。所有动画都基于弹幕添加后经过的时间计算：

- `duration`：弹幕总存活时间（毫秒）
- `alphaTween`：透明度渐变，范围 `[0,1]`，`null` 表示不变化
- `translateXTween` / `translateYTween`：X/Y 相对位置变化，范围 `[0,1]`（相对于视图宽高）
- `translationDuration`：位移动画的持续时间（毫秒）
- `translationStartDelay`：位移动画的延迟启动时间（毫秒）
- `rotateZ`：Z 轴旋转角度（度数）
- `matrix`：自定义变换矩阵（如果提供，将忽略 `rotateZ`）
- `easingType`：插值器，控制动画速度曲线

高级弹幕的绘制会先根据旋转和矩阵计算出包围盒，并生成离屏 Bitmap，然后在每帧中根据当前进度计算出位置和透明度进行绘制，保证性能。

## API 参考

### DanmakuView

| 方法 | 说明 |
|------|------|
| `addDanmaku(text, color, selfSend, hasStroke, type)` | 添加普通弹幕 |
| `addSpecialDanmaku(text, color, selfSend, hasStroke, specialParams)` | 添加高级弹幕 |
| `clear()` | 清除所有弹幕并释放资源 |
| `pause()` | 暂停弹幕动画 |
| `resume()` | 恢复弹幕动画 |
| `isRunning()` | 返回动画是否正在运行 |
| `getCurrentFps()` | 获取当前帧率（仅在动态帧率模式下有效） |
| `setCustomBezier(x1, y1, x2, y2)` | 设置自定义帧率控制曲线 |

### DanmakuOption

| 属性 | 说明 |
|------|------|
| `fontSize` | 字体大小 |
| `textColor` | 文字颜色 |
| `strokeWidth` | 描边宽度 |
| `strokeColor` | 描边颜色 |
| `durationMillis` | 滚动弹幕持续时间 |
| `staticDurationMillis` | 静态弹幕持续时间 |
| `trackArea` | 轨道区域占比 |
| `safeArea` | 是否启用安全区域 |
| `massiveMode` | 是否启用巨量模式 |
| `lineHeight` | 行高倍数 |
| `useBitmapCache` | 是否启用位图缓存 |
| `useBatchPainter` | 是否启用批量绘制 |
| `enableDynamicFrameRate` | 是否启用动态帧率 |
| `minFrameRate` / `maxFrameRate` | 动态帧率范围 |
| `hideTop` / `hideBottom` / `hideSpecial` | 是否隐藏特定类型弹幕 |

### SpecialDanmakuParams

| 属性 | 说明 |
|------|------|
| `duration` | 弹幕总存活时间（毫秒） |
| `alphaTween` | 透明度动画参数 |
| `translateXTween` / `translateYTween` | 位置动画参数 |
| `translationDuration` | 位移动画持续时间 |
| `translationStartDelay` | 位移动画延迟启动时间 |
| `rotateZ` | Z 轴旋转角度 |
| `matrix` | 自定义变换矩阵 |
| `easingType` | 插值器类型 |

## 注意事项

1. **轨道管理**：滚动弹幕的轨道数量根据视图高度和 `trackArea` 动态计算。当轨道用尽时，若 `massiveMode = false`，弹幕将被丢弃；若为 `true`，会随机选择一个轨道。
2. **静态弹幕过期**：顶部/底部弹幕会在 `staticDurationMillis` 后自动移除，并释放资源。
3. **高级弹幕**：由于涉及矩阵变换，建议尽量使用 `useBitmapCache` 预生成 Bitmap，避免每帧重新绘制。
4. **动态帧率**：如果设备性能较低，可以适当降低 `maxFrameRate` 来节省电量。
5. **内存管理**：所有弹幕在移除或清除时都会回收其缓存的 Bitmap，避免内存泄漏。
6. **坐标系**：滚动弹幕的 X 坐标从 `viewWidth` 开始向左移动；顶部/底部弹幕 X 坐标居中；高级弹幕的坐标是相对百分比（0~1），最终位置由 `viewWidth * percent` 计算。

## 许可证

本项目使用 MIT 许可证，详情请见 [LICENSE](LICENSE) 文件。

---

更多信息请查看 [GitHub 仓库](https://github.com/Nefyra/CanvasDanmaku)。
```
