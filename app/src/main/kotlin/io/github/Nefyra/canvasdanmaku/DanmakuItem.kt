package io.github.Nefyra.canvasdanmaku

import android.graphics.Bitmap
import android.graphics.Paint

enum class DanmakuType {
    SCROLL,
    TOP,
    BOTTOM,
    SPECIAL,
}

data class DanmakuItem(
    val text: String,
    var x: Float,
    var y: Float,
    val color: Int,
    var textSize: Float,
    var width: Float = 0f,
    var height: Float = 0f,
    var isExpired: Boolean = false,
    var trackIndex: Int = -1,
    var addTime: Long = System.currentTimeMillis(),
    var duration: Long = 10000L,
    var speed: Float = 0f,
    var cachedBitmap: Bitmap? = null,
    var selfSend: Boolean = false,
    var hasStroke: Boolean = true,
    var isVisible: Boolean = true,
    val type: DanmakuType = DanmakuType.SCROLL,
    var drawTick: Long = 0L,
    var specialParams: SpecialDanmakuParams? = null,
    var animCurrentX: Float = 0f,
    var animCurrentY: Float = 0f,
    var animCurrentAlpha: Float = 1f,
) {
    fun measure(paint: Paint) {
        paint.textSize = textSize
        width = paint.measureText(text)
        val fontMetrics = paint.fontMetrics
        height = fontMetrics.descent - fontMetrics.ascent
    }

    fun dispose() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        specialParams?.cachedBitmap?.recycle()
        specialParams?.cachedBitmap = null
    }

    fun needRemove(needRemove: Boolean): Boolean {
        if (needRemove) {
            dispose()
        }
        return needRemove
    }

    fun isInView(viewWidth: Float): Boolean = x + width > 0 && x < viewWidth
}
