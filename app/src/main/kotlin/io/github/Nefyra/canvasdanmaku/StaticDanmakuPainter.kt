package io.github.Nefyra.canvasdanmaku

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper

class StaticDanmakuPainter(
    private val onInvalidate: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val topDanmakus = mutableListOf<DanmakuItem>()
    private val bottomDanmakus = mutableListOf<DanmakuItem>()
    private val pendingRemoval = mutableListOf<DanmakuItem>()
    private var cleanupRunnable: Runnable? = null

    fun addTop(item: DanmakuItem) {
        topDanmakus.add(item)
        scheduleCleanup()
        onInvalidate()
    }

    fun addBottom(item: DanmakuItem) {
        bottomDanmakus.add(item)
        scheduleCleanup()
        onInvalidate()
    }

    fun removeTop(item: DanmakuItem) {
        topDanmakus.remove(item)
        item.dispose()
        onInvalidate()
    }

    fun removeBottom(item: DanmakuItem) {
        bottomDanmakus.remove(item)
        item.dispose()
        onInvalidate()
    }

    fun getTopDanmakus(): List<DanmakuItem> = topDanmakus

    fun getBottomDanmakus(): List<DanmakuItem> = bottomDanmakus

    fun draw(
        canvas: Canvas,
        drawFunction: (Canvas, DanmakuItem) -> Unit,
    ) {
        topDanmakus.forEach { drawFunction(canvas, it) }
        bottomDanmakus.forEach { drawFunction(canvas, it) }
    }

    fun clear() {
        topDanmakus.forEach { it.dispose() }
        bottomDanmakus.forEach { it.dispose() }
        topDanmakus.clear()
        bottomDanmakus.clear()
        cancelCleanup()
    }

    private fun scheduleCleanup() {
        cancelCleanup()
        cleanupRunnable =
            Runnable {
                val now = System.currentTimeMillis()

                val expiredTop = topDanmakus.filter { now - it.drawTick >= it.duration }
                expiredTop.forEach { removeTop(it) }

                val expiredBottom = bottomDanmakus.filter { now - it.drawTick >= it.duration }
                expiredBottom.forEach { removeBottom(it) }

                if (topDanmakus.isNotEmpty() || bottomDanmakus.isNotEmpty()) {
                    scheduleCleanup()
                }
            }
        val minRemainingTime =
            (topDanmakus + bottomDanmakus).minOfOrNull {
                val elapsed = System.currentTimeMillis() - it.drawTick
                (it.duration - elapsed).coerceAtLeast(0)
            } ?: return
        handler.postDelayed(cleanupRunnable!!, minRemainingTime.coerceAtLeast(1))
    }

    private fun cancelCleanup() {
        cleanupRunnable?.let { handler.removeCallbacks(it) }
        cleanupRunnable = null
    }
}
