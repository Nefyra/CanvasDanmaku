package io.github.Nefyra.canvasdanmaku

import android.graphics.Matrix
import android.graphics.RectF
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator

data class SpecialDanmakuParams(
    val duration: Long,
    val alphaTween: Tween<Float>? = null,
    val translateXTween: Tween<Float>,
    val translateYTween: Tween<Float>,
    val translationDuration: Long,
    val translationStartDelay: Long = 0,
    val rotateZ: Float = 0f,
    val matrix: Matrix? = null,
    val easingType: Interpolator = LinearInterpolator(),
    var rect: RectF = RectF(),
    var cachedBitmap: android.graphics.Bitmap? = null,
) {
    data class Tween<T>(
        val start: T,
        val end: T,
    )
}
