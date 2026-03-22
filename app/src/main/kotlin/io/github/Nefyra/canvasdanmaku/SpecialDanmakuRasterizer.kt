package io.github.Nefyra.canvasdanmaku

import android.graphics.*
import kotlin.math.*

object SpecialDanmakuRasterizer {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.LEFT
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

    fun rasterizeSpecialDanmaku(
        content: SpecialDanmakuContentItem,
        strokeWidth: Float,
        strokeColor: Int,
        devicePixelRatio: Float = 1f,
    ): Bitmap? {
        paint.textSize = content.fontSize
        paint.color = content.color

        val textWidth = paint.measureText(content.text)
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        var totalWidth = textWidth + strokeWidth
        var totalHeight = textHeight + strokeWidth

        val rect =
            calculateRotatedBounds(
                totalWidth,
                totalHeight,
                content.rotateZ,
                content.matrix,
            )
        content.rect = rect

        val finalWidth = rect.width()
        val finalHeight = rect.height()

        if (finalWidth <= 0 || finalHeight <= 0) return null

        var adjustedDpr = devicePixelRatio
        val imgLongestSide = max(finalWidth, finalHeight) * devicePixelRatio
        if (imgLongestSide > SpecialDanmakuContentItem.MAX_RASTERIZE_SIZE) {
            adjustedDpr = SpecialDanmakuContentItem.MAX_RASTERIZE_SIZE / imgLongestSide
        }

        val scaledWidth = (finalWidth * adjustedDpr).toInt()
        val scaledHeight = (finalHeight * adjustedDpr).toInt()

        if (scaledWidth <= 0 || scaledHeight <= 0) return null

        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.scale(adjustedDpr, adjustedDpr)
        canvas.translate(strokeWidth / 2f - rect.left, strokeWidth / 2f - rect.top)

        if (content.matrix != null) {
            canvas.concat(content.matrix)
        } else if (content.rotateZ != 0f) {
            canvas.rotate(content.rotateZ)
        }

        val yOffset = -fontMetrics.ascent

        if (content.hasStroke && strokeWidth > 0) {
            strokePaint.strokeWidth = strokeWidth
            strokePaint.color = strokeColor
            canvas.drawText(content.text, 0f, yOffset, strokePaint)
        }

        canvas.drawText(content.text, 0f, yOffset, paint)

        return bitmap
    }

    private fun calculateRotatedBounds(
        width: Float,
        height: Float,
        rotateZ: Float,
        matrix: Matrix?,
    ): RectF {
        val cosZ: Float
        val sinZ: Float
        val cosY: Float

        if (matrix == null) {
            cosZ = cos(rotateZ.toDouble()).toFloat()
            sinZ = sin(rotateZ.toDouble()).toFloat()
            cosY = 1f
        } else {
            val values = FloatArray(9)
            matrix.getValues(values)
            cosZ = values[Matrix.MSCALE_X].coerceIn(-1f, 1f)
            sinZ = values[Matrix.MSKEW_Y].coerceIn(-1f, 1f)
            cosY = values[Matrix.MPERSP_0].coerceIn(-1f, 1f)
        }

        val wx = width * cosZ * cosY
        val wy = width * sinZ
        val hx = -height * sinZ * cosY
        val hy = height * cosZ

        val minX = minOf(0f, wx, hx, wx + hx)
        val maxX = maxOf(0f, wx, hx, wx + hx)
        val minY = minOf(0f, wy, hy, wy + hy)
        val maxY = maxOf(0f, wy, hy, wy + hy)

        return RectF(minX, minY, maxX, maxY)
    }

    fun updateStrokePaint(
        strokeWidth: Float,
        strokeColor: Int,
    ) {
        strokePaint.strokeWidth = strokeWidth
        strokePaint.color = strokeColor
    }
}
