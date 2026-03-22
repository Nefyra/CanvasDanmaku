package io.github.Nefyra.canvasdanmaku

import android.graphics.*
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import kotlin.math.*

data class SpecialDanmakuContentItem(
    val text: String,
    val color: Int,
    val duration: Long,
    val fontSize: Float,
    val hasStroke: Boolean = false,
    val alphaTween: Tween<Float>? = null,
    val translateXTween: Tween<Float>,
    val translateYTween: Tween<Float>,
    val translationDuration: Int,
    val translationStartDelay: Int = 0,
    val rotateZ: Float = 0f,
    val matrix: Matrix? = null,
    val easingType: Interpolator = LinearInterpolator(),
    var rect: RectF = RectF(),
) {
    companion object {
        const val MAX_RASTERIZE_SIZE = 8192.0f

        fun fromList(
            color: Int,
            fontSize: Float,
            list: List<Any>,
            videoWidth: Float = 1920f,
            videoHeight: Float = 1080f,
            disableGradient: Boolean = false,
        ): SpecialDanmakuContentItem {
            val (startX, endX) = toRelativePosition(list[0], list[7], videoWidth)
            val (startY, endY) = toRelativePosition(list[1], list[8], videoHeight)

            val alphaString = (list[2] as String).split("-")
            val startA = alphaString[0].toFloatOrNull() ?: 1f
            val endA = alphaString[1].toFloatOrNull() ?: 1f

            val alphaTween =
                if (!disableGradient && startA != endA) {
                    Tween(startA, endA)
                } else {
                    null
                }

            val finalColor =
                if (alphaTween == null) {
                    ColorUtils.setAlphaComponent(color, ((startA + endA) / 2 * 255).toInt())
                } else {
                    color
                }

            val duration = (parseDouble(list[3]) * 1000).toLong()
            val text = (list[4] as String).trimEnd()

            var rotateZ = degreeToRadian(parseInt(list[5]))
            val rotateY = parseInt(list[6])

            val matrix =
                if (rotateY != 0) {
                    Matrix().apply {
                        if (rotateZ != 0f) {
                            postRotate(rotateZ)
                            rotateZ = 0f
                        }
                        val yRotationMatrix =
                            Matrix().apply {
                                val cos = cos(rotateY.toDouble()).toFloat()
                                val sin = sin(rotateY.toDouble()).toFloat()
                                setValues(
                                    floatArrayOf(
                                        cos,
                                        0f,
                                        sin,
                                        0f,
                                        0f,
                                        1f,
                                        0f,
                                        0f,
                                        -sin,
                                        0f,
                                        cos,
                                        0f,
                                        0f,
                                        0f,
                                        0f,
                                        1f,
                                    ),
                                )
                            }
                        postConcat(yRotationMatrix)
                    }
                } else {
                    null
                }

            val translateXTween = makeTween(startX, endX)
            val translateYTween = makeTween(startY, endY)
            val translationDuration = parseInt(list[9])
            val translationStartDelay = parseInt(list[10])
            val hasStroke = list[11] == 1

            val easingType =
                if (list[13] == 1) {
                    android.view.animation.AccelerateDecelerateInterpolator()
                } else {
                    LinearInterpolator()
                }

            return SpecialDanmakuContentItem(
                text = text,
                color = finalColor,
                duration = duration,
                fontSize = fontSize,
                hasStroke = hasStroke,
                alphaTween = alphaTween,
                translateXTween = translateXTween,
                translateYTween = translateYTween,
                translationDuration = translationDuration,
                translationStartDelay = translationStartDelay,
                rotateZ = rotateZ,
                matrix = matrix,
                easingType = easingType,
            )
        }

        private fun toRelativePosition(
            rawStart: Any,
            rawEnd: Any,
            videoSize: Float,
        ): Pair<Float, Float> {
            fun toRadix(
                value: Float?,
                raw: Any,
            ): Float =
                when {
                    value == null -> 0f
                    value > 1f || (raw is String && !raw.contains('.')) -> value / videoSize
                    else -> value
                }

            fun convert(digit: Any): Float? =
                when (digit) {
                    is Int -> digit.toFloat()
                    is Float -> if (digit.isFinite()) digit else null
                    is String -> digit.toFloatOrNull()
                    else -> null
                }

            var start = convert(rawStart)
            var end = convert(rawEnd)

            start = start ?: end ?: 0f
            end = end ?: start

            return Pair(toRadix(start, rawStart), toRadix(end, rawEnd))
        }

        private fun parseInt(digit: Any): Int =
            when (digit) {
                is Int -> digit
                is Float -> digit.toInt()
                is String -> digit.toIntOrNull() ?: 0
                else -> 0
            }

        private fun parseDouble(digit: Any): Double =
            when (digit) {
                is Int -> digit.toDouble()
                is Float -> digit.toDouble()
                is String -> digit.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

        @Suppress("UNCHECKED_CAST")
        private fun <T> makeTween(
            start: T,
            end: T,
        ): Tween<T> =
            if (start == end) {
                ConstantTween(start) as Tween<T>
            } else {
                Tween(start, end)
            }

        private fun degreeToRadian(degree: Int): Float = degree * (Math.PI / 180.0).toFloat()
    }
}

data class Tween<T>(
    val begin: T,
    val end: T,
)

class ConstantTween<T>(
    val value: T,
) {
    fun transform(progress: Float): T = value
}

object ColorUtils {
    fun setAlphaComponent(
        color: Int,
        alpha: Int,
    ): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
