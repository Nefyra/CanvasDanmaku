package io.github.Nefyra.canvasdanmaku

import android.graphics.*

class DanmakuBatchPainter(
    private val viewWidth: Float,
    private val viewHeight: Float,
    private val option: DanmakuOption,
    private val devicePixelRatio: Float = 1f,
) {
    private var cachedBitmap: Bitmap? = null
    private var cachedRect: Rect? = null
    private var lastDanmakuHash = 0
    private var lastViewWidth = 0f
    private var lastViewHeight = 0f

    private var isDirty = true

    fun invalidate() {
        isDirty = true
    }

    fun paint(
        canvas: Canvas,
        danmakuItems: List<DanmakuItem>,
    ) {
        if (danmakuItems.isEmpty()) return

        if (lastViewWidth != viewWidth || lastViewHeight != viewHeight) {
            isDirty = true
            lastViewWidth = viewWidth
            lastViewHeight = viewHeight
        }

        val currentHash = calculateDanmakuHash(danmakuItems)

        if (isDirty || cachedBitmap == null || lastDanmakuHash != currentHash) {
            regenerateCache(danmakuItems)
            lastDanmakuHash = currentHash
            isDirty = false
        }

        cachedBitmap?.let { bitmap ->
            cachedRect?.let { rect ->
                canvas.drawBitmap(bitmap, rect.left.toFloat(), rect.top.toFloat(), null)
            } ?: run {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            }
        }
    }

    private fun calculateDanmakuHash(danmakuItems: List<DanmakuItem>): Int {
        var hash = 0
        for (item in danmakuItems) {
            if (item.isVisible) {
                hash = hash * 31 + item.x.toInt()
                hash = hash * 31 + item.y.toInt()
                hash = hash * 31 + item.text.hashCode()
            }
        }
        return hash
    }

    private fun regenerateCache(danmakuItems: List<DanmakuItem>) {
        if (danmakuItems.isEmpty()) return

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var hasVisible = false

        for (item in danmakuItems) {
            if (!item.isVisible) continue
            hasVisible = true
            minX = minOf(minX, item.x)
            minY = minOf(minY, item.y)
            maxX = maxOf(maxX, item.x + item.width)
            maxY = maxOf(maxY, item.y + item.height)
        }

        if (!hasVisible || minX == Float.MAX_VALUE) return

        val padding = 4f
        minX = (minX - padding).coerceAtLeast(0f)
        minY = (minY - padding).coerceAtLeast(0f)
        maxX = (maxX + padding).coerceAtMost(viewWidth)
        maxY = (maxY + padding).coerceAtMost(viewHeight)

        val width = (maxX - minX).toInt()
        val height = (maxY - minY).toInt()

        if (width <= 0 || height <= 0) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val offscreenCanvas = Canvas(bitmap)
        offscreenCanvas.translate(-minX, -minY)

        for (item in danmakuItems) {
            if (item.isVisible) {
                drawDanmakuToCanvas(offscreenCanvas, item)
            }
        }

        cachedBitmap?.recycle()
        cachedBitmap = bitmap
        cachedRect = Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
    }

    private fun drawDanmakuToCanvas(
        canvas: Canvas,
        item: DanmakuItem,
    ) {
        if (item.cachedBitmap != null && !item.cachedBitmap!!.isRecycled) {
            canvas.drawBitmap(item.cachedBitmap!!, item.x, item.y, null)
            return
        }

        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = item.textSize
                color = item.color
                style = Paint.Style.FILL
                textAlign = Paint.Align.LEFT
            }

        val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = option.strokeWidth
                color = option.strokeColor
            }

        val fontMetrics = paint.fontMetrics
        val yOffset = -fontMetrics.ascent

        if (option.strokeWidth > 0 && item.hasStroke) {
            canvas.drawText(item.text, item.x, item.y + yOffset, strokePaint)
        }
        canvas.drawText(item.text, item.x, item.y + yOffset, paint)

        if (item.selfSend) {
            val borderPaint =
                Paint().apply {
                    style = Paint.Style.STROKE
                    color = Color.GREEN
                    strokeWidth = option.strokeWidth
                }
            canvas.drawRect(
                item.x - 2f,
                item.y,
                item.x + item.width - option.strokeWidth,
                item.y + item.height,
                borderPaint,
            )
        }
    }

    fun dispose() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedRect = null
    }
}
