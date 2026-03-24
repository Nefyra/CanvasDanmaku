package com.example.danmaku

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.Nefyra.canvasdanmaku.DanmakuOption
import io.github.Nefyra.canvasdanmaku.DanmakuView
import io.github.Nefyra.canvasdanmaku.SpecialDanmakuParams
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var danmakuView: DanmakuView
    private lateinit var fpsTextView: TextView
    private lateinit var danmakuCountTextView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var frameCount = 0
    private var lastFpsUpdateTime = 0L
    private var currentFps = 0

    private var autoAddEnabled = false
    private var addInterval = 100L // 默认100ms一条
    private var currentMode = TestMode.MIXED
    private var customDanmakuList = mutableListOf<String>()

    enum class TestMode {
        REPEATED, // 重复弹幕
        UNIQUE, // 唯一弹幕
        MIXED, // 混合模式
        STRESS, // 压力测试
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        danmakuView = findViewById(R.id.danmaku_view)
        fpsTextView = findViewById(R.id.tv_fps)
        danmakuCountTextView = findViewById(R.id.tv_danmaku_count)

        setupDanmakuView()
        setupControls()
        startFpsMonitor()
    }

    private fun setupDanmakuView() {
        danmakuView.option =
            DanmakuOption(
                fontSize = 18f,
                durationMillis = 8000,
                trackArea = 0.9f,
                safeArea = true,
                massiveMode = false, // 禁用大量模式
                lineHeight = 1.3f,
                useBitmapCache = true, // 启用 Bitmap 缓存
                useBatchPainter = true, // 启用批量绘制
            )

        repeat(20) { i ->
            customDanmakuList.add("热门弹幕 ${i + 1}") // 重复使用的弹幕
        }
        repeat(10) { i ->
            customDanmakuList.add("🔥🔥 火 ${i + 1} 🔥🔥")
        }
        repeat(5) { i ->
            customDanmakuList.add("这是一条比较长的测试弹幕内容 ${i + 1}，用于测试长文本渲染")
        }
    }

    private fun setupControls() {
        findViewById<Button>(R.id.btn_mode_repeated).setOnClickListener {
            currentMode = TestMode.REPEATED
            stopAutoAdd()
            startAutoAdd()
            showToast("重复弹幕模式 - 测试 Bitmap 缓存")
        }

        findViewById<Button>(R.id.btn_mode_unique).setOnClickListener {
            currentMode = TestMode.UNIQUE
            stopAutoAdd()
            startAutoAdd()
            showToast("唯一弹幕模式 - 测试光栅化性能")
        }

        findViewById<Button>(R.id.btn_mode_mixed).setOnClickListener {
            currentMode = TestMode.MIXED
            stopAutoAdd()
            startAutoAdd()
            showToast("混合模式")
        }

        findViewById<Button>(R.id.btn_mode_stress).setOnClickListener {
            currentMode = TestMode.STRESS
            stopAutoAdd()
            showToast("压力测试 - 添加100条弹幕")
            for (i in 0 until 100) {
                handler.postDelayed({
                    addTestDanmaku()
                }, i * 20L)
            }
        }

        val speedSeekBar = findViewById<SeekBar>(R.id.seekbar_speed)
        val speedText = findViewById<TextView>(R.id.tv_speed_value)

        speedSeekBar.max = 9

        speedSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    addInterval =
                        when (progress) {
                            0 -> 1000L

                            // 1条/秒
                            1 -> 500L

                            // 2条/秒
                            2 -> 200L

                            // 5条/秒
                            3 -> 100L

                            // 10条/秒
                            4 -> 50L

                            // 20条/秒
                            5 -> 33L

                            // 30条/秒
                            6 -> 20L

                            // 50条/秒
                            7 -> 10L

                            // 100条/秒
                            8 -> 5L

                            // 200条/秒
                            9 -> 1L

                            // 1000条/秒
                            else -> 100L
                        }

                    speedText.text =
                        when (progress) {
                            0 -> "极慢 (1条/秒)"
                            1 -> "慢速 (2条/秒)"
                            2 -> "中速 (5条/秒)"
                            3 -> "快速 (10条/秒)"
                            4 -> "高速 (20条/秒)"
                            5 -> "超高速 (30条/秒)"
                            6 -> "疯狂 (50条/秒)"
                            7 -> "极速 (100条/秒)"
                            8 -> "狂暴 (200条/秒)"
                            9 -> "极限 (1000条/秒) ⚡"
                            else -> "自定义"
                        }

                    if (autoAddEnabled) {
                        stopAutoAdd()
                        startAutoAdd()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        speedSeekBar.progress = 3

        val batchPainterToggle = findViewById<Button>(R.id.btn_toggle_batch)
        batchPainterToggle.text = "批量绘制: ${if (danmakuView.option.useBatchPainter) "开" else "关"}"

        batchPainterToggle.setOnClickListener {
            val newOption = danmakuView.option.copy(useBatchPainter = !danmakuView.option.useBatchPainter)
            danmakuView.option = newOption
            batchPainterToggle.text = "批量绘制: ${if (newOption.useBatchPainter) "开" else "关"}"
            showToast("批量绘制: ${if (newOption.useBatchPainter) "启用" else "禁用"}")
        }

        val bitmapCacheToggle = findViewById<Button>(R.id.btn_toggle_cache)
        bitmapCacheToggle.text = "Bitmap缓存: ${if (danmakuView.option.useBitmapCache) "开" else "关"}"

        bitmapCacheToggle.setOnClickListener {
            val newOption = danmakuView.option.copy(useBitmapCache = !danmakuView.option.useBitmapCache)
            danmakuView.option = newOption
            bitmapCacheToggle.text = "Bitmap缓存: ${if (newOption.useBitmapCache) "开" else "关"}"
            showToast("Bitmap缓存: ${if (newOption.useBitmapCache) "启用" else "禁用"}")
        }

        // 添加大量重复弹幕
        findViewById<Button>(R.id.btn_add_repeated).setOnClickListener {
            showToast("添加50条重复弹幕（测试缓存）")
            for (i in 0 until 50) {
                val text = customDanmakuList[i % customDanmakuList.size]
                danmakuView.addDanmaku(text)
            }
        }

        // 添加唯一弹幕
        findViewById<Button>(R.id.btn_add_unique).setOnClickListener {
            showToast("添加50条唯一弹幕（测试光栅化）")
            for (i in 0 until 50) {
                danmakuView.addDanmaku("唯一弹幕 ${System.currentTimeMillis()} $i")
            }
        }

        findViewById<Button>(R.id.btn_add_top).setOnClickListener {
            repeat(10) { i ->
                addTopDanmaku("顶部弹幕 $i")
            }
            showToast("添加10条顶部弹幕")
        }

        findViewById<Button>(R.id.btn_add_bottom).setOnClickListener {
            repeat(10) { i ->
                addBottomDanmaku("底部弹幕 $i")
            }
            showToast("添加10条底部弹幕")
        }

        findViewById<Button>(R.id.btn_add_special).setOnClickListener {
            showRandomSpecialDanmaku()
            showToast("添加高级弹幕")
        }

        // 清空
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            danmakuView.clear()
            showToast("已清空")
        }

        // 暂停/恢复
        findViewById<Button>(R.id.btn_toggle).setOnClickListener {
            if (danmakuView.isRunning()) {
                danmakuView.pause()
                showToast("暂停")
            } else {
                danmakuView.resume()
                showToast("恢复")
            }
        }

        // 开始自动添加
        findViewById<Button>(R.id.btn_start_auto).setOnClickListener {
            startAutoAdd()
        }

        // 停止自动添加
        findViewById<Button>(R.id.btn_stop_auto).setOnClickListener {
            stopAutoAdd()
            showToast("已停止自动添加")
        }

        // 字体大小调整
        val fontSeekBar = findViewById<SeekBar>(R.id.seekbar_font_size)
        val fontSizeText = findViewById<TextView>(R.id.tv_font_size_value)
        fontSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val fontSize = (10 + progress / 2).toFloat()
                    fontSizeText.text = "${fontSize.toInt()}"
                    if (fromUser) {
                        danmakuView.option = danmakuView.option.copy(fontSize = fontSize)
                        showToast("字体大小: ${fontSize.toInt()}")
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        // 持续时间调整
        val durationSeekBar = findViewById<SeekBar>(R.id.seekbar_duration)
        val durationText = findViewById<TextView>(R.id.tv_duration_value)
        durationSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val duration = progress.toLong()
                    durationText.text = "${duration / 1000}s"
                    if (fromUser) {
                        danmakuView.option = danmakuView.option.copy(durationMillis = duration)
                        showToast("持续时间: ${duration / 1000}s")
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        // 轨道区域调整
        val trackSeekBar = findViewById<SeekBar>(R.id.seekbar_track_area)
        val trackText = findViewById<TextView>(R.id.tv_track_area_value)
        trackSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val trackArea = progress / 100f
                    trackText.text = String.format("%.2f", trackArea)
                    if (fromUser) {
                        danmakuView.option = danmakuView.option.copy(trackArea = trackArea)
                        showToast("轨道区域: ${(trackArea * 100).toInt()}%")
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        // 行高调整
        val lineSeekBar = findViewById<SeekBar>(R.id.seekbar_line_height)
        val lineText = findViewById<TextView>(R.id.tv_line_height_value)
        lineSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val lineHeight = 0.8f + progress / 20f
                    lineText.text = String.format("%.1f", lineHeight)
                    if (fromUser) {
                        danmakuView.option = danmakuView.option.copy(lineHeight = lineHeight)
                        showToast("行高: $lineHeight")
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        // 安全区域开关
        val safeAreaBtn = findViewById<Button>(R.id.btn_toggle_safe_area)
        safeAreaBtn.setOnClickListener {
            val newSafeArea = !danmakuView.option.safeArea
            danmakuView.option = danmakuView.option.copy(safeArea = newSafeArea)
            safeAreaBtn.text = if (newSafeArea) "开启" else "关闭"
            showToast("安全区域: ${if (newSafeArea) "开启" else "关闭"}")
        }

        // 大量模式开关
        val massiveBtn = findViewById<Button>(R.id.btn_toggle_massive)
        massiveBtn.setOnClickListener {
            val newMassive = !danmakuView.option.massiveMode
            danmakuView.option = danmakuView.option.copy(massiveMode = newMassive)
            massiveBtn.text = if (newMassive) "开启" else "关闭"
            showToast("大量模式: ${if (newMassive) "开启" else "关闭"} (轨道满时仍添加)")
        }

        // 隐藏顶部弹幕
        val hideTopBtn = findViewById<Button>(R.id.btn_toggle_hide_top)
        hideTopBtn.setOnClickListener {
            val newHide = !danmakuView.option.hideTop
            danmakuView.option = danmakuView.option.copy(hideTop = newHide)
            hideTopBtn.text = if (newHide) "显示顶部" else "隐藏顶部"
            showToast(if (newHide) "已隐藏顶部弹幕" else "已显示顶部弹幕")
        }

        // 隐藏底部弹幕
        val hideBottomBtn = findViewById<Button>(R.id.btn_toggle_hide_bottom)
        hideBottomBtn.setOnClickListener {
            val newHide = !danmakuView.option.hideBottom
            danmakuView.option = danmakuView.option.copy(hideBottom = newHide)
            hideBottomBtn.text = if (newHide) "显示底部" else "隐藏底部"
            showToast(if (newHide) "已隐藏底部弹幕" else "已显示底部弹幕")
        }

        // 隐藏高级弹幕
        val hideSpecialBtn = findViewById<Button>(R.id.btn_toggle_hide_special)
        hideSpecialBtn.setOnClickListener {
            val newHide = !danmakuView.option.hideSpecial
            danmakuView.option = danmakuView.option.copy(hideSpecial = newHide)
            hideSpecialBtn.text = if (newHide) "显示高级" else "隐藏高级"
            showToast(if (newHide) "已隐藏高级弹幕" else "已显示高级弹幕")
        }
    }

    private fun showRandomSpecialDanmaku() {
        val randomType = (0..7).random()

        when (randomType) {
            // 1. 从左到右水平移动
            0 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 4000,
                        translateXTween = SpecialDanmakuParams.Tween(0f, 1f),
                        translateYTween = SpecialDanmakuParams.Tween(0.5f, 0.5f),
                        translationDuration = 3500,
                        translationStartDelay = 0,
                        easingType = android.view.animation.LinearInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("➡️ 水平移动", Color.CYAN, false, true, params)
            }

            // 2. 从上到下垂直移动 + 淡入
            1 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 3500,
                        translateXTween = SpecialDanmakuParams.Tween(0.5f, 0.5f),
                        translateYTween = SpecialDanmakuParams.Tween(0f, 0.9f),
                        translationDuration = 3000,
                        alphaTween = SpecialDanmakuParams.Tween(0f, 1f),
                        easingType = android.view.animation.AccelerateInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("⬇️ 垂直下落", Color.GREEN, false, true, params)
            }

            // 3. 对角线移动 + 淡出
            2 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 4000,
                        translateXTween = SpecialDanmakuParams.Tween(0f, 1f),
                        translateYTween = SpecialDanmakuParams.Tween(0f, 1f),
                        translationDuration = 3500,
                        alphaTween = SpecialDanmakuParams.Tween(1f, 0f),
                        easingType = android.view.animation.DecelerateInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("↘️ 对角线移动", Color.MAGENTA, false, true, params)
            }

            // 4. 旋转效果
            3 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 3000,
                        translateXTween = SpecialDanmakuParams.Tween(0.2f, 0.8f),
                        translateYTween = SpecialDanmakuParams.Tween(0.5f, 0.5f),
                        translationDuration = 2800,
                        rotateZ = 360f, // 旋转360度
                        easingType = android.view.animation.LinearInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("🔄 旋转弹幕", Color.parseColor("#FF9800"), false, true, params)
            }

            // 5. 延迟启动效果
            4 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 5000,
                        translateXTween = SpecialDanmakuParams.Tween(0f, 1f),
                        translateYTween = SpecialDanmakuParams.Tween(0.2f, 0.2f),
                        translationDuration = 3000,
                        translationStartDelay = 2000,
                        easingType = android.view.animation.BounceInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("⏰ 延迟启动", Color.parseColor("#9C27B0"), false, true, params)
            }

            // 6. 从右到左反向移动 + 透明度渐变
            5 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 4000,
                        translateXTween = SpecialDanmakuParams.Tween(1f, 0f), // 从右到左
                        translateYTween = SpecialDanmakuParams.Tween(0.3f, 0.3f),
                        translationDuration = 3600,
                        alphaTween = SpecialDanmakuParams.Tween(1f, 0.3f), // 半透明淡出
                        easingType = android.view.animation.AccelerateDecelerateInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("⬅️ 反向移动", Color.parseColor("#00BCD4"), false, true, params)
            }

            // 7. 复杂路径 + 回弹效果
            6 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 5000,
                        translateXTween = SpecialDanmakuParams.Tween(0f, 0.8f),
                        translateYTween = SpecialDanmakuParams.Tween(0.2f, 0.8f),
                        translationDuration = 4500,
                        alphaTween = SpecialDanmakuParams.Tween(0.5f, 1f),
                        translationStartDelay = 300,
                        easingType = android.view.animation.AnticipateOvershootInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("🎯 复杂路径", Color.parseColor("#E91E63"), false, true, params)
            }

            7 -> {
                val params =
                    SpecialDanmakuParams(
                        duration = 6000,
                        alphaTween = SpecialDanmakuParams.Tween(0f, 1f),
                        translateXTween = SpecialDanmakuParams.Tween(0f, 0.9f),
                        translateYTween = SpecialDanmakuParams.Tween(0.2f, 0.8f),
                        translationDuration = 5000,
                        translationStartDelay = 500,
                        rotateZ = 180f, // 旋转180度
                        easingType = android.view.animation.AccelerateDecelerateInterpolator(),
                    )
                danmakuView.addSpecialDanmaku("🌈 完整特效", Color.parseColor("#FF6B6B"), true, true, params)
            }
        }
    }

    private data class SpecialStyle(
        val name: String,
        val emoji: String,
        val color: Int,
        val translateXStart: Float,
        val translateXEnd: Float,
        val translateYStart: Float,
        val translateYEnd: Float,
        val rotateZ: Float = 0f,
        val alphaStart: Float? = null,
        val alphaEnd: Float? = null,
        val startDelay: Long = 0,
        val duration: Long = 4000,
        val translationDuration: Long = 3500,
        val easing: android.view.animation.Interpolator = android.view.animation.LinearInterpolator(),
    )

    private val specialStyles =
        listOf(
            SpecialStyle(
                name = "流星划过",
                emoji = "☄️",
                color = Color.WHITE,
                translateXStart = 0f,
                translateXEnd = 1f,
                translateYStart = 0.2f,
                translateYEnd = 0.2f,
                alphaStart = 1f,
                alphaEnd = 0f,
                duration = 3000,
                translationDuration = 2800,
            ),
            SpecialStyle(
                name = "跳跃弹幕",
                emoji = "🐰",
                color = Color.parseColor("#FFC107"),
                translateXStart = 0.3f,
                translateXEnd = 0.7f,
                translateYStart = 0.4f,
                translateYEnd = 0.6f,
                rotateZ = 30f,
                easing = android.view.animation.BounceInterpolator(),
            ),
            SpecialStyle(
                name = "渐入弹幕",
                emoji = "✨",
                color = Color.parseColor("#4CAF50"),
                translateXStart = 0.2f,
                translateXEnd = 0.8f,
                translateYStart = 0.5f,
                translateYEnd = 0.5f,
                alphaStart = 0f,
                alphaEnd = 1f,
                duration = 5000,
                translationDuration = 4500,
            ),
            SpecialStyle(
                name = "旋转入场",
                emoji = "🌀",
                color = Color.parseColor("#2196F3"),
                translateXStart = 0f,
                translateXEnd = 0.9f,
                translateYStart = 0.8f,
                translateYEnd = 0.2f,
                rotateZ = 360f,
                duration = 4500,
            ),
            SpecialStyle(
                name = "慢速飘移",
                emoji = "🎈",
                color = Color.parseColor("#FF4081"),
                translateXStart = 0f,
                translateXEnd = 1f,
                translateYStart = 0.3f,
                translateYEnd = 0.3f,
                startDelay = 1000,
                duration = 6000,
                translationDuration = 5000,
                easing = android.view.animation.DecelerateInterpolator(),
            ),
            SpecialStyle(
                name = "淡出飘散",
                emoji = "🍃",
                color = Color.parseColor("#9C27B0"),
                translateXStart = 0f,
                translateXEnd = 0.7f,
                translateYStart = 0.6f,
                translateYEnd = 0.9f,
                alphaStart = 1f,
                alphaEnd = 0f,
                duration = 4000,
                translationDuration = 3500,
                easing = android.view.animation.AccelerateInterpolator(),
            ),
            SpecialStyle(
                name = "螺旋上升",
                emoji = "🌀",
                color = Color.parseColor("#FF5722"),
                translateXStart = 0.5f,
                translateXEnd = 0.5f,
                translateYStart = 0.8f,
                translateYEnd = 0.2f,
                rotateZ = 180f,
                duration = 4500,
            ),
            SpecialStyle(
                name = "左右摇摆",
                emoji = "🎭",
                color = Color.parseColor("#00BCD4"),
                translateXStart = 0.1f,
                translateXEnd = 0.9f,
                translateYStart = 0.5f,
                translateYEnd = 0.5f,
                duration = 5000,
                translationDuration = 4500,
                easing = android.view.animation.AccelerateDecelerateInterpolator(),
            ),
        )

    private fun showRandomSpecialDanmakuWithStyles() {
        val style = specialStyles.random()

        val alphaTween =
            if (style.alphaStart != null && style.alphaEnd != null) {
                SpecialDanmakuParams.Tween(style.alphaStart, style.alphaEnd)
            } else {
                null
            }

        val params =
            SpecialDanmakuParams(
                duration = style.duration,
                alphaTween = alphaTween,
                translateXTween = SpecialDanmakuParams.Tween(style.translateXStart, style.translateXEnd),
                translateYTween = SpecialDanmakuParams.Tween(style.translateYStart, style.translateYEnd),
                translationDuration = style.translationDuration,
                translationStartDelay = style.startDelay,
                rotateZ = style.rotateZ,
                easingType = style.easing,
            )

        danmakuView.addSpecialDanmaku(
            text = "${style.emoji} ${style.name}弹幕 ${style.emoji}",
            color = style.color,
            selfSend = (0..1).random() == 0,
            hasStroke = true,
            specialParams = params,
        )
    }

    private fun addRandomSpecialDanmaku() {
        when ((0..1).random()) {
            0 -> showRandomSpecialDanmaku()
            1 -> showRandomSpecialDanmakuWithStyles()
        }
    }

    private var autoAddRunnable: Runnable? = null

    private fun startAutoAdd() {
        stopAutoAdd()
        autoAddEnabled = true
        autoAddRunnable =
            object : Runnable {
                override fun run() {
                    if (autoAddEnabled) {
                        addTestDanmaku()
                        handler.postDelayed(this, addInterval)
                    }
                }
            }
        handler.post(autoAddRunnable!!)
        showToast("自动添加已启动 - 间隔 ${addInterval}ms")
    }

    private fun stopAutoAdd() {
        autoAddEnabled = false
        autoAddRunnable?.let { handler.removeCallbacks(it) }
        autoAddRunnable = null
    }

    private fun addTestDanmaku() {
        val (text, color, selfSend) =
            when (currentMode) {
                TestMode.REPEATED -> {
                    val text = customDanmakuList[Random.nextInt(customDanmakuList.size)]
                    Triple(text, Color.WHITE, Random.nextBoolean())
                }

                TestMode.UNIQUE -> {
                    val timestamp = System.currentTimeMillis()
                    val text =
                        buildString {
                            append("弹幕")
                            append(timestamp % 1000)
                            append("_")
                            append(Random.nextInt(1000))
                        }
                    Triple(text, getRandomColor(), false)
                }

                TestMode.MIXED -> {
                    val useRepeated = Random.nextInt(100) < 70
                    val text =
                        if (useRepeated) {
                            customDanmakuList[Random.nextInt(customDanmakuList.size)]
                        } else {
                            "新弹幕 ${System.currentTimeMillis() % 1000}"
                        }
                    Triple(text, getRandomColor(), Random.nextBoolean() && useRepeated)
                }

                TestMode.STRESS -> {
                    val text = "压力测试 ${Random.nextInt(1000)}"
                    Triple(text, getRandomColor(), false)
                }
            }

        danmakuView.addDanmaku(text, color, selfSend)
    }

    private fun getRandomColor(): Int =
        Color.rgb(
            Random.nextInt(100, 255),
            Random.nextInt(100, 255),
            Random.nextInt(100, 255),
        )

    private fun startFpsMonitor() {
        lastFpsUpdateTime = System.currentTimeMillis()
        handler.post(
            object : Runnable {
                override fun run() {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastFpsUpdateTime
                    if (elapsed >= 1000) {
                        currentFps = frameCount
                        frameCount = 0
                        lastFpsUpdateTime = now

                        runOnUiThread {
                            fpsTextView.text = "FPS: $currentFps"

                            val hint =
                                when {
                                    currentFps < 30 -> " ⚠️"
                                    currentFps > 55 -> " ✅"
                                    else -> ""
                                }
                            danmakuCountTextView.text = "弹幕数: --$hint"
                        }
                    }
                    frameCount++
                    handler.postDelayed(this, 16)
                }
            },
        )
    }

    private fun addTopDanmaku(text: String) {
        danmakuView.addDanmaku(text, getRandomColor(), false, true, io.github.Nefyra.canvasdanmaku.DanmakuType.TOP)
    }

    private fun addBottomDanmaku(text: String) {
        danmakuView.addDanmaku(text, getRandomColor(), false, true, io.github.Nefyra.canvasdanmaku.DanmakuType.BOTTOM)
    }

    private fun getDanmakuCount(): Int = 0

    private fun showToast(message: String) {
        android.widget.Toast
            .makeText(this, message, android.widget.Toast.LENGTH_SHORT)
            .show()
    }

    override fun onPause() {
        super.onPause()
        stopAutoAdd()
        danmakuView.pause()
    }

    override fun onResume() {
        super.onResume()
        danmakuView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoAdd()
        handler.removeCallbacksAndMessages(null)
    }
}
