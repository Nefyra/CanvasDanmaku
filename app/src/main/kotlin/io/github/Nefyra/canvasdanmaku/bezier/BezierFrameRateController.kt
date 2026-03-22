package io.github.Nefyra.canvasdanmaku.bezier

import android.view.animation.PathInterpolator

/**
 * 基于三次贝塞尔曲线的平滑帧率控制器
 * 实现弹幕数量变化时的丝滑帧率过渡
 */
class BezierFrameRateController(
    private val minFps: Int = 24, // 最低帧率（弹幕最少时）
    private val maxFps: Int = 60, // 最高帧率（弹幕最多时）
    private val minDanmakuCount: Int = 5, // 最少弹幕阈值（低于此值使用最低帧率）
    private val maxDanmakuCount: Int = 50, // 最多弹幕阈值（高于此值使用最高帧率）
    // 默认使用 ease-in-out 曲线：缓慢加速 → 匀速 → 缓慢减速
    private val controlX1: Float = 0.42f,
    private val controlY1: Float = 0f,
    private val controlX2: Float = 0.58f,
    private val controlY2: Float = 1f,
) {
    private val bezierInterpolator = PathInterpolator(controlX1, controlY1, controlX2, controlY2)

    private var currentFrameInterval = (1000f / maxFps).toLong()

    private var lastUpdateTime = 0L

    private var maxChangePerFrame = 5L

    private val countHistory = mutableListOf<Int>()
    private val historySize = 3

    fun calculateFrameInterval(visibleCount: Int): Long {
        val smoothedCount = smoothInput(visibleCount)

        val normalized = normalizeCount(smoothedCount)

        val fpsFactor = bezierInterpolator.getInterpolation(normalized)

        val targetFps = minFps + (maxFps - minFps) * fpsFactor
        val targetFrameInterval = (1000f / targetFps).toLong()

        val now = System.currentTimeMillis()
        if (lastUpdateTime > 0) {
            val timeSinceLastUpdate = now - lastUpdateTime
            val dynamicMaxChange = (maxChangePerFrame * (timeSinceLastUpdate / 16f).coerceAtLeast(1f)).toLong()
            currentFrameInterval =
                when {
                    targetFrameInterval > currentFrameInterval + dynamicMaxChange -> {
                        currentFrameInterval + dynamicMaxChange
                    }

                    targetFrameInterval < currentFrameInterval - dynamicMaxChange -> {
                        currentFrameInterval - dynamicMaxChange
                    }

                    else -> {
                        targetFrameInterval
                    }
                }
        } else {
            currentFrameInterval = targetFrameInterval
        }

        currentFrameInterval =
            currentFrameInterval.coerceIn(
                (1000f / maxFps).toLong(),
                (1000f / minFps).toLong(),
            )

        lastUpdateTime = now
        return currentFrameInterval
    }

    private fun smoothInput(count: Int): Int {
        countHistory.add(count)
        while (countHistory.size > historySize) {
            countHistory.removeAt(0)
        }
        return countHistory.average().toInt()
    }

    private fun normalizeCount(count: Int): Float =
        when {
            count <= minDanmakuCount -> 0f
            count >= maxDanmakuCount -> 1f
            else -> (count - minDanmakuCount).toFloat() / (maxDanmakuCount - minDanmakuCount)
        }

    fun reset() {
        currentFrameInterval = (1000f / maxFps).toLong()
        lastUpdateTime = 0L
        countHistory.clear()
    }

    fun getCurrentFrameInterval(): Long = currentFrameInterval

    fun getCurrentFps(): Float = 1000f / currentFrameInterval

    fun setMaxChangePerFrame(maxChange: Long) {
        maxChangePerFrame = maxChange
    }
}
