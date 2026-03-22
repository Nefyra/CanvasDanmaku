package io.github.Nefyra.canvasdanmaku

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin

object DanmakuRasterizer {
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.LEFT
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    private val selfSendPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.GREEN
            strokeWidth = 2f
        }

    private var batchCanvas: Canvas? = null
    private var batchBitmap: Bitmap? = null
    private var batchWidth = 0
    private var batchHeight = 0

    /**
     * 将弹幕文本光栅化为 Bitmap 缓存
     */
    fun rasterizeDanmaku(
        text: String,
        color: Int,
        textSize: Float,
        strokeWidth: Float,
        strokeColor: Int,
        selfSend: Boolean = false,
        hasStroke: Boolean = true,
        devicePixelRatio: Float = 1f,
    ): Bitmap? {
        textPaint.textSize = textSize
        textPaint.color = color

        val textWidth = textPaint.measureText(text)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        var totalWidth = (textWidth + strokeWidth + (if (selfSend) 4f else 0f)).toInt()
        var totalHeight = (textHeight + strokeWidth).toInt()

        if (totalWidth <= 0 || totalHeight <= 0) return null

        val scaledWidth = (totalWidth * devicePixelRatio).toInt()
        val scaledHeight = (totalHeight * devicePixelRatio).toInt()

        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (devicePixelRatio != 1f) {
            canvas.scale(devicePixelRatio, devicePixelRatio)
        }

        val xOffset = (strokeWidth / 2f) + (if (selfSend) 2f else 0f)
        val yOffset = strokeWidth / 2f - fontMetrics.ascent

        if (hasStroke && strokeWidth > 0) {
            strokePaint.strokeWidth = strokeWidth
            strokePaint.color = strokeColor
            canvas.drawText(text, xOffset, yOffset, strokePaint)
        }

        canvas.drawText(text, xOffset, yOffset, textPaint)

        if (selfSend) {
            selfSendPaint.strokeWidth = strokeWidth
            canvas.drawRect(
                0f,
                0f,
                totalWidth.toFloat(),
                totalHeight.toFloat(),
                selfSendPaint,
            )
        }

        return bitmap
    }

    fun startBatchRecording(
        estimatedWidth: Int,
        estimatedHeight: Int,
    ) {
        batchBitmap?.recycle()

        batchWidth = estimatedWidth.coerceAtLeast(1)
        batchHeight = estimatedHeight.coerceAtLeast(1)

        val newBitmap = Bitmap.createBitmap(batchWidth, batchHeight, Bitmap.Config.ARGB_8888)
        batchBitmap = newBitmap
        batchCanvas = Canvas(newBitmap)
    }

    fun addToBatch(
        text: String,
        color: Int,
        textSize: Float,
        strokeWidth: Float,
        strokeColor: Int,
        x: Float,
        y: Float,
        selfSend: Boolean = false,
        hasStroke: Boolean = true,
    ) {
        val canvas = batchCanvas ?: return

        textPaint.textSize = textSize
        textPaint.color = color

        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val yOffset = -fontMetrics.ascent

        if (hasStroke && strokeWidth > 0) {
            strokePaint.strokeWidth = strokeWidth
            strokePaint.color = strokeColor
            canvas.drawText(text, x, y + yOffset, strokePaint)
        }

        canvas.drawText(text, x, y + yOffset, textPaint)

        if (selfSend) {
            val textWidth = textPaint.measureText(text)
            selfSendPaint.strokeWidth = strokeWidth
            canvas.drawRect(
                x - 2f,
                y,
                x + textWidth + 2f,
                y + textHeight,
                selfSendPaint,
            )
        }
    }

    fun endBatchRecording(): Bitmap? {
        val bitmap = batchBitmap
        batchCanvas = null
        batchBitmap = null
        return bitmap
    }

    fun updateSelfSendPaint(strokeWidth: Float) {
        selfSendPaint.strokeWidth = strokeWidth
    }

    fun measureText(
        text: String,
        textSize: Float,
        strokeWidth: Float = 0f,
    ): TextMetrics {
        textPaint.textSize = textSize
        val textWidth = textPaint.measureText(text)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        return TextMetrics(
            width = textWidth + strokeWidth,
            height = textHeight + strokeWidth,
            textWidth = textWidth,
            textHeight = textHeight,
            ascent = fontMetrics.ascent,
            descent = fontMetrics.descent,
        )
    }

    fun rasterizeSpecialDanmaku(
        text: String,
        color: Int,
        textSize: Float,
        strokeWidth: Float,
        strokeColor: Int,
        selfSend: Boolean = false,
        hasStroke: Boolean = true,
        devicePixelRatio: Float = 1f,
        specialParams: SpecialDanmakuParams,
    ): Bitmap? {
        textPaint.textSize = textSize
        textPaint.color = color

        val textWidth = textPaint.measureText(text)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val totalWidth = textWidth + strokeWidth
        val totalHeight = textHeight + strokeWidth

        val rect = calculateRotatedBounds(totalWidth, totalHeight, specialParams.rotateZ, specialParams.matrix)
        specialParams.rect = rect

        val width = (rect.width() * devicePixelRatio).toInt().coerceAtLeast(1)
        val height = (rect.height() * devicePixelRatio).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (devicePixelRatio != 1f) canvas.scale(devicePixelRatio, devicePixelRatio)
        canvas.translate(-rect.left, -rect.top)

        if (specialParams.matrix != null) {
            canvas.concat(specialParams.matrix)
        } else if (specialParams.rotateZ != 0f) {
            canvas.rotate(Math.toDegrees(specialParams.rotateZ.toDouble()).toFloat())
        }

        val xOffset = strokeWidth / 2f
        val yOffset = strokeWidth / 2f - fontMetrics.ascent

        if (hasStroke && strokeWidth > 0) {
            strokePaint.strokeWidth = strokeWidth
            strokePaint.color = strokeColor
            canvas.drawText(text, xOffset, yOffset, strokePaint)
        }
        canvas.drawText(text, xOffset, yOffset, textPaint)

        if (selfSend) {
            selfSendPaint.strokeWidth = strokeWidth
            canvas.drawRect(
                xOffset - 2f,
                yOffset - fontMetrics.ascent,
                xOffset + textWidth + 2f,
                yOffset + textHeight - fontMetrics.ascent,
                selfSendPaint,
            )
        }
        return bitmap
    }

    private fun calculateRotatedBounds(
        w: Float,
        h: Float,
        rotateZ: Float,
        matrix: Matrix?,
    ): RectF {
        val points = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        if (matrix != null) {
            matrix.mapPoints(points)
        } else if (rotateZ != 0f) {
            val cos = cos(rotateZ.toDouble()).toFloat()
            val sin = sin(rotateZ.toDouble()).toFloat()
            for (i in points.indices step 2) {
                val x = points[i]
                val y = points[i + 1]
                points[i] = x * cos - y * sin
                points[i + 1] = x * sin + y * cos
            }
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in points.indices step 2) {
            val x = points[i]
            val y = points[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return RectF(minX, minY, maxX, maxY)
    }

    data class TextMetrics(
        val width: Float,
        val height: Float,
        val textWidth: Float,
        val textHeight: Float,
        val ascent: Float,
        val descent: Float,
    )
}
