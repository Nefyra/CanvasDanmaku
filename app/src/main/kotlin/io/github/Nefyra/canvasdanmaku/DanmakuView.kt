package io.github.Nefyra.canvasdanmaku

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import io.github.Nefyra.canvasdanmaku.bezier.BezierFrameRateController
import kotlin.math.floor

class DanmakuView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        // 滚动弹幕相关
        private val scrollDanmaku = mutableListOf<DanmakuItem>()
        private val trackDanmakus = mutableListOf<MutableList<DanmakuItem>>()
        private val trackYPositions = mutableListOf<Float>()

        // 静态弹幕相关
        private val topDanmakus = mutableListOf<DanmakuItem>()
        private val bottomDanmakus = mutableListOf<DanmakuItem>()
        private val topTrackOccupancy = mutableListOf<Boolean>()
        private val bottomTrackOccupancy = mutableListOf<Boolean>()

        // 高级弹幕相关
        private val specialDanmakus = mutableListOf<DanmakuItem>()

        // 静态弹幕清理处理器
        private val staticCleanupHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var staticCleanupRunnable: Runnable? = null

        private var trackCount = 0
        private var trackHeight = 0f
        private var viewWidth = 0f
        private var viewHeight = 0f

        private var animator: ValueAnimator? = null
        private var lastFrameTime = 0L
        private var running = true

        private var lastVisibleCount = 0
        private var lastFrameTimestamp = 0L
        private var frameSkipCount = 0
        private var totalVisibleDanmaku = 0

        private var batchPainter: DanmakuBatchPainter? = null

        private var frameRateController: BezierFrameRateController? = null

        var option = DanmakuOption()
            set(value) {
                val oldHideTop = field.hideTop
                val oldHideBottom = field.hideBottom
                val oldHideSpecial = field.hideSpecial
                field = value
                DanmakuRasterizer.updateSelfSendPaint(value.strokeWidth)
                clearBitmapCache()
                recalcTracks()

                if (value.enableDynamicFrameRate) {
                    frameRateController =
                        BezierFrameRateController(
                            minFps = value.minFrameRate,
                            maxFps = value.maxFrameRate,
                            controlX1 = value.bezierX1,
                            controlY1 = value.bezierY1,
                            controlX2 = value.bezierX2,
                            controlY2 = value.bezierY2,
                        )
                } else {
                    frameRateController = null
                }

                if (!oldHideTop && value.hideTop) {
                    topDanmakus.forEach { it.dispose() }
                    topDanmakus.clear()
                    for (i in 0 until topTrackOccupancy.size) {
                        topTrackOccupancy[i] = false
                    }
                    cancelStaticCleanup()
                }
                if (!oldHideBottom && value.hideBottom) {
                    bottomDanmakus.forEach { it.dispose() }
                    bottomDanmakus.clear()
                    for (i in 0 until bottomTrackOccupancy.size) {
                        bottomTrackOccupancy[i] = false
                    }
                    cancelStaticCleanup()
                }
                if (!oldHideSpecial && value.hideSpecial) {
                    specialDanmakus.forEach { it.dispose() }
                    specialDanmakus.clear()
                }

                invalidate()
            }

        init {
            option = DanmakuOption()

            if (option.enableDynamicFrameRate) {
                frameRateController =
                    BezierFrameRateController(
                        minFps = option.minFrameRate,
                        maxFps = option.maxFrameRate,
                        controlX1 = option.bezierX1,
                        controlY1 = option.bezierY1,
                        controlX2 = option.bezierX2,
                        controlY2 = option.bezierY2,
                    )
            }
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            viewWidth = w.toFloat()
            viewHeight = h.toFloat()
            recalcTracks()
            updateStaticDanmakuPositions()
            batchPainter?.invalidate()
        }

        private fun recalcTracks() {
            if (viewWidth == 0f || viewHeight == 0f) return

            val metrics =
                DanmakuRasterizer.measureText(
                    text = "测",
                    textSize = option.fontSize,
                    strokeWidth = option.strokeWidth,
                )
            trackHeight = metrics.height * option.lineHeight

            var trackAreaHeight = viewHeight * option.trackArea
            if (option.safeArea && option.trackArea == 1.0f) {
                trackAreaHeight -= trackHeight
            }

            trackCount = floor(trackAreaHeight / trackHeight).toInt().coerceAtLeast(1)

            trackYPositions.clear()
            for (i in 0 until trackCount) {
                trackYPositions.add(i * trackHeight)
            }

            trackDanmakus.clear()
            repeat(trackCount) { trackDanmakus.add(mutableListOf()) }

            val iterator = scrollDanmaku.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.trackIndex in 0 until trackCount) {
                    item.y = trackYPositions[item.trackIndex]
                    trackDanmakus[item.trackIndex].add(item)
                } else {
                    iterator.remove()
                    item.dispose()
                }
            }

            initStaticTrackOccupancy()
            updateStaticDanmakuPositions()
        }

        private fun initStaticTrackOccupancy() {
            topTrackOccupancy.clear()
            bottomTrackOccupancy.clear()
            repeat(trackCount) {
                topTrackOccupancy.add(false)
                bottomTrackOccupancy.add(false)
            }

            topDanmakus.forEach { item ->
                if (item.trackIndex in 0 until trackCount) {
                    topTrackOccupancy[item.trackIndex] = true
                }
            }
            bottomDanmakus.forEach { item ->
                if (item.trackIndex in 0 until trackCount) {
                    bottomTrackOccupancy[item.trackIndex] = true
                }
            }
        }

        private fun updateStaticDanmakuPositions() {
            topDanmakus.forEach { item ->
                if (item.trackIndex in 0 until trackCount) {
                    item.x = (viewWidth - item.width) / 2
                    item.y = item.trackIndex * trackHeight
                }
            }
            bottomDanmakus.forEach { item ->
                if (item.trackIndex in 0 until trackCount) {
                    item.x = (viewWidth - item.width) / 2
                    item.y = viewHeight - (item.trackIndex + 1) * trackHeight
                }
            }
        }

        fun addDanmaku(
            text: String,
            color: Int = option.textColor,
            selfSend: Boolean = false,
            hasStroke: Boolean = true,
            type: DanmakuType = DanmakuType.SCROLL,
        ) {
            when (type) {
                DanmakuType.SCROLL -> addScrollDanmaku(text, color, selfSend, hasStroke)
                DanmakuType.TOP -> addTopDanmaku(text, color, selfSend, hasStroke)
                DanmakuType.BOTTOM -> addBottomDanmaku(text, color, selfSend, hasStroke)
                DanmakuType.SPECIAL -> addSpecialDanmaku(text, color, selfSend, hasStroke, null)
            }
        }

        fun addSpecialDanmaku(
            text: String,
            color: Int = option.textColor,
            selfSend: Boolean = false,
            hasStroke: Boolean = true,
            specialParams: SpecialDanmakuParams?,
        ) {
            if (option.hideSpecial) return

            val metrics =
                DanmakuRasterizer.measureText(
                    text = text,
                    textSize = option.fontSize,
                    strokeWidth = option.strokeWidth,
                )

            val params =
                specialParams ?: SpecialDanmakuParams(
                    duration = option.durationMillis,
                    translateXTween = SpecialDanmakuParams.Tween(0f, 1f),
                    translateYTween = SpecialDanmakuParams.Tween(0f, 0f),
                    translationDuration = option.durationMillis,
                )

            val item =
                DanmakuItem(
                    text = text,
                    x = 0f,
                    y = 0f,
                    color = color,
                    textSize = option.fontSize,
                    width = metrics.width,
                    height = metrics.height,
                    addTime = System.currentTimeMillis(),
                    duration = params.duration,
                    selfSend = selfSend,
                    hasStroke = hasStroke,
                    type = DanmakuType.SPECIAL,
                    specialParams = params,
                )

            if (option.useBitmapCache) {
                val bitmap =
                    DanmakuRasterizer.rasterizeSpecialDanmaku(
                        text = text,
                        color = color,
                        textSize = option.fontSize,
                        strokeWidth = option.strokeWidth,
                        strokeColor = option.strokeColor,
                        selfSend = selfSend,
                        hasStroke = hasStroke,
                        devicePixelRatio = resources.displayMetrics.density,
                        specialParams = params,
                    )
                params.cachedBitmap = bitmap
            }

            specialDanmakus.add(item)

            if (animator?.isRunning != true && (scrollDanmaku.isNotEmpty() || specialDanmakus.isNotEmpty())) {
                startAnimation()
            }

            invalidate()
        }

        private fun addScrollDanmaku(
            text: String,
            color: Int,
            selfSend: Boolean,
            hasStroke: Boolean,
        ) {
            val metrics =
                DanmakuRasterizer.measureText(
                    text = text,
                    textSize = option.fontSize,
                    strokeWidth = option.strokeWidth,
                )

            val item =
                DanmakuItem(
                    text = text,
                    x = viewWidth,
                    y = 0f,
                    color = color,
                    textSize = option.fontSize,
                    width = metrics.width + (if (selfSend) 4f else 0f),
                    height = metrics.height,
                    addTime = System.currentTimeMillis(),
                    duration = option.durationMillis,
                    selfSend = selfSend,
                    hasStroke = hasStroke,
                    type = DanmakuType.SCROLL,
                )

            if (option.useBitmapCache) {
                item.cachedBitmap =
                    DanmakuRasterizer.rasterizeDanmaku(
                        text = text,
                        color = color,
                        textSize = option.fontSize,
                        strokeWidth = option.strokeWidth,
                        strokeColor = option.strokeColor,
                        selfSend = selfSend,
                        hasStroke = hasStroke,
                    )
            }

            item.speed = (viewWidth + item.width) / item.duration.toFloat()

            val trackIndex = findAvailableTrack(item)

            if (trackIndex == -1) {
                if (option.massiveMode) {
                    val randomTrack = (0 until trackCount).random()
                    addToTrack(item, randomTrack)
                }
                return
            }

            addToTrack(item, trackIndex)
        }

        private fun addTopDanmaku(
            text: String,
            color: Int,
            selfSend: Boolean,
            hasStroke: Boolean,
        ) {
            if (option.hideTop) return

            val metrics =
                DanmakuRasterizer.measureText(
                    text = text,
                    textSize = option.fontSize,
                    strokeWidth = option.strokeWidth,
                )

            val item =
                DanmakuItem(
                    text = text,
                    x = 0f,
                    y = 0f,
                    color = color,
                    textSize = option.fontSize,
                    width = metrics.width + (if (selfSend) 4f else 0f),
                    height = metrics.height,
                    addTime = System.currentTimeMillis(),
                    duration = option.staticDurationMillis,
                    selfSend = selfSend,
                    hasStroke = hasStroke,
                    type = DanmakuType.TOP,
                    drawTick = System.currentTimeMillis(),
                )

            if (option.useBitmapCache) {
                item.cachedBitmap =
                    DanmakuRasterizer.rasterizeDanmaku(
                        text = text,
                        color = color,
                        textSize = option.fontSize,
                        strokeWidth = option.strokeWidth,
                        strokeColor = option.strokeColor,
                        selfSend = selfSend,
                        hasStroke = hasStroke,
                    )
            }

            var trackIndex = -1
            for (i in 0 until trackCount) {
                if (!topTrackOccupancy[i]) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex == -1 && option.massiveMode) {
                trackIndex = (0 until trackCount).random()
            }

            if (trackIndex != -1) {
                item.trackIndex = trackIndex
                item.x = (viewWidth - item.width) / 2
                item.y = trackIndex * trackHeight
                topTrackOccupancy[trackIndex] = true
                topDanmakus.add(item)
                scheduleStaticCleanup()
                invalidate()
            }
        }

        private fun addBottomDanmaku(
            text: String,
            color: Int,
            selfSend: Boolean,
            hasStroke: Boolean,
        ) {
            if (option.hideBottom) return

            val metrics =
                DanmakuRasterizer.measureText(
                    text = text,
                    textSize = option.fontSize,
                    strokeWidth = option.strokeWidth,
                )

            val item =
                DanmakuItem(
                    text = text,
                    x = 0f,
                    y = 0f,
                    color = color,
                    textSize = option.fontSize,
                    width = metrics.width + (if (selfSend) 4f else 0f),
                    height = metrics.height,
                    addTime = System.currentTimeMillis(),
                    duration = option.staticDurationMillis,
                    selfSend = selfSend,
                    hasStroke = hasStroke,
                    type = DanmakuType.BOTTOM,
                    drawTick = System.currentTimeMillis(),
                )

            if (option.useBitmapCache) {
                item.cachedBitmap =
                    DanmakuRasterizer.rasterizeDanmaku(
                        text = text,
                        color = color,
                        textSize = option.fontSize,
                        strokeWidth = option.strokeWidth,
                        strokeColor = option.strokeColor,
                        selfSend = selfSend,
                        hasStroke = hasStroke,
                    )
            }

            var trackIndex = -1
            for (i in 0 until trackCount) {
                if (!bottomTrackOccupancy[i]) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex == -1 && option.massiveMode) {
                trackIndex = (0 until trackCount).random()
            }

            if (trackIndex != -1) {
                item.trackIndex = trackIndex
                item.x = (viewWidth - item.width) / 2
                item.y = viewHeight - (trackIndex + 1) * trackHeight
                bottomTrackOccupancy[trackIndex] = true
                bottomDanmakus.add(item)
                scheduleStaticCleanup()
                invalidate()
            }
        }

        private fun findAvailableTrack(newItem: DanmakuItem): Int {
            for (trackIndex in 0 until trackCount) {
                if (canAddToTrack(newItem, trackIndex)) {
                    return trackIndex
                }
            }
            return -1
        }

        private fun canAddToTrack(
            newItem: DanmakuItem,
            trackIndex: Int,
        ): Boolean {
            val trackItems = trackDanmakus[trackIndex]

            for (existing in trackItems) {
                if (existing.y == trackYPositions[trackIndex]) {
                    val existingEndPosition = existing.x + existing.width

                    if (viewWidth - existingEndPosition < 0) {
                        return false
                    }

                    if (existing.width < newItem.width) {
                        val remainingProgress = 1 - ((viewWidth - existing.x) / (existing.width + viewWidth))
                        val dangerousProgress = viewWidth / (viewWidth + newItem.width)

                        if (remainingProgress > dangerousProgress) {
                            return false
                        }
                    }
                }
            }

            return true
        }

        private fun addToTrack(
            item: DanmakuItem,
            trackIndex: Int,
        ) {
            item.trackIndex = trackIndex
            item.y = trackYPositions[trackIndex]
            item.x = viewWidth
            item.isVisible = true

            trackDanmakus[trackIndex].add(item)
            scrollDanmaku.add(item)

            if (animator?.isRunning != true && (scrollDanmaku.isNotEmpty() || specialDanmakus.isNotEmpty())) {
                startAnimation()
            }
        }

        private fun startAnimation() {
            if (animator != null) return

            animator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = Long.MAX_VALUE
                    interpolator = LinearInterpolator()
                    addUpdateListener { updatePosition() }
                    start()
                }
            lastFrameTime = System.currentTimeMillis()
            lastFrameTimestamp = lastFrameTime
        }

        private fun updatePosition() {
            if (!running) return

            val now = System.currentTimeMillis()
            val delta = (now - lastFrameTime).coerceAtMost(50)
            lastFrameTime = now

            val visibleScrollCount = scrollDanmaku.count { it.isVisible }
            val visibleSpecialCount = specialDanmakus.size

            if (visibleScrollCount == 0 && visibleSpecialCount == 0) {
                stopAnimationIfNeeded()
                return
            }

            val targetFrameInterval =
                if (option.enableDynamicFrameRate && frameRateController != null) {
                    frameRateController!!.calculateFrameInterval(visibleScrollCount + visibleSpecialCount)
                } else {
                    16L
                }

            val timeSinceLastFrame = now - lastFrameTimestamp
            if (timeSinceLastFrame < targetFrameInterval) {
                frameSkipCount++
                return
            }
            lastFrameTimestamp = now

            // 更新滚动弹幕位置
            var needInvalidate = false
            val toRemove = mutableListOf<DanmakuItem>()

            for (item in scrollDanmaku) {
                if (!item.isVisible) continue

                val step = item.speed * delta
                item.x -= step

                val wasVisible = item.isVisible
                item.isVisible = item.x + item.width > 0 && item.x < viewWidth

                if (item.x + item.width < 0) {
                    toRemove.add(item)
                    needInvalidate = true
                } else if (wasVisible != item.isVisible) {
                    needInvalidate = true
                } else if (item.isVisible) {
                    needInvalidate = true
                }
            }

            if (toRemove.isNotEmpty()) {
                for (item in toRemove) {
                    if (item.trackIndex in trackDanmakus.indices) {
                        trackDanmakus[item.trackIndex].remove(item)
                    }
                    scrollDanmaku.remove(item)
                    item.dispose()
                }
                needInvalidate = true
            }

            // 更新高级弹幕动画
            val specialToRemove = mutableListOf<DanmakuItem>()
            for (item in specialDanmakus) {
                val params = item.specialParams ?: continue
                val elapsed = now - item.addTime

                if (elapsed >= params.duration) {
                    specialToRemove.add(item)
                    needInvalidate = true
                    continue
                }

                val progress = elapsed.toFloat() / params.duration
                val easedProgress = params.easingType.getInterpolation(progress)

                val alpha =
                    if (params.alphaTween != null) {
                        val a = params.alphaTween.start + (params.alphaTween.end - params.alphaTween.start) * easedProgress
                        (a.coerceIn(0f, 1f) * 255).toInt()
                    } else {
                        255
                    }
                item.animCurrentAlpha = alpha / 255f

                val translationProgress =
                    if (elapsed >= params.translationStartDelay) {
                        val t = (elapsed - params.translationStartDelay).toFloat() / params.translationDuration
                        params.easingType.getInterpolation(t.coerceIn(0f, 1f))
                    } else {
                        0f
                    }

                val xPercent =
                    params.translateXTween.start +
                        (params.translateXTween.end - params.translateXTween.start) * translationProgress
                val yPercent =
                    params.translateYTween.start +
                        (params.translateYTween.end - params.translateYTween.start) * translationProgress

                item.animCurrentX = xPercent * viewWidth
                item.animCurrentY = yPercent * viewHeight

                needInvalidate = true
            }

            specialToRemove.forEach { removeSpecialDanmaku(it) }

            if (needInvalidate) {
                batchPainter?.invalidate()
                invalidate()
            }

            if (scrollDanmaku.none { it.isVisible } && specialDanmakus.isEmpty()) {
                stopAnimationIfNeeded()
            }
        }

        private fun removeTopDanmaku(item: DanmakuItem) {
            if (item.trackIndex in 0 until trackCount) {
                topTrackOccupancy[item.trackIndex] = false
            }
            topDanmakus.remove(item)
            item.dispose()
            invalidate()
        }

        private fun removeBottomDanmaku(item: DanmakuItem) {
            if (item.trackIndex in 0 until trackCount) {
                bottomTrackOccupancy[item.trackIndex] = false
            }
            bottomDanmakus.remove(item)
            item.dispose()
            invalidate()
        }

        private fun removeSpecialDanmaku(item: DanmakuItem) {
            specialDanmakus.remove(item)
            item.dispose()
            invalidate()
        }

        private fun stopAnimationIfNeeded() {
            if (scrollDanmaku.none { it.isVisible } && specialDanmakus.isEmpty()) {
                animator?.cancel()
                animator = null
            }
        }

        private fun scheduleStaticCleanup() {
            cancelStaticCleanup()

            staticCleanupRunnable =
                Runnable {
                    val now = System.currentTimeMillis()

                    val expiredTop =
                        topDanmakus
                            .filter {
                                now - it.drawTick >= it.duration
                            }.toList()
                    expiredTop.forEach { removeTopDanmaku(it) }

                    val expiredBottom =
                        bottomDanmakus
                            .filter {
                                now - it.drawTick >= it.duration
                            }.toList()
                    expiredBottom.forEach { removeBottomDanmaku(it) }

                    if (topDanmakus.isNotEmpty() || bottomDanmakus.isNotEmpty()) {
                        scheduleStaticCleanup()
                    }
                }

            val allStaticDanmakus = topDanmakus + bottomDanmakus
            val now = System.currentTimeMillis()
            val minRemainingTime =
                allStaticDanmakus.minOfOrNull {
                    val elapsed = now - it.drawTick
                    (it.duration - elapsed).coerceAtLeast(0)
                }

            if (minRemainingTime != null && minRemainingTime > 0) {
                staticCleanupHandler.postDelayed(staticCleanupRunnable!!, minRemainingTime)
            } else if (minRemainingTime == 0L) {
                staticCleanupHandler.post(staticCleanupRunnable!!)
            }
        }

        private fun cancelStaticCleanup() {
            staticCleanupRunnable?.let { staticCleanupHandler.removeCallbacks(it) }
            staticCleanupRunnable = null
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val visibleScroll = scrollDanmaku.filter { it.isVisible }
            if (visibleScroll.isNotEmpty()) {
                val useBatch = option.useBitmapCache && visibleScroll.size > 5

                if (useBatch && option.useBatchPainter) {
                    if (batchPainter == null) {
                        batchPainter =
                            DanmakuBatchPainter(
                                viewWidth = viewWidth,
                                viewHeight = viewHeight,
                                option = option,
                            )
                    }
                    batchPainter?.paint(canvas, visibleScroll)
                } else {
                    for (item in visibleScroll) {
                        drawDanmakuWithCache(canvas, item)
                    }
                }
            }

            for (item in topDanmakus) {
                drawDanmakuWithCache(canvas, item)
            }

            for (item in bottomDanmakus) {
                drawDanmakuWithCache(canvas, item)
            }

            for (item in specialDanmakus) {
                drawSpecialDanmaku(canvas, item)
            }
        }

        private fun drawDanmakuWithCache(
            canvas: Canvas,
            item: DanmakuItem,
        ) {
            val bitmap = item.cachedBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, item.x, item.y, null)
            } else {
                drawDanmakuDirect(canvas, item)
            }
        }

        private fun drawSpecialDanmaku(
            canvas: Canvas,
            item: DanmakuItem,
        ) {
            val params = item.specialParams ?: return
            val bitmap = params.cachedBitmap ?: return
            if (bitmap.isRecycled) return

            val rect = params.rect
            val drawX = item.animCurrentX - rect.left
            val drawY = item.animCurrentY - rect.top

            val paint =
                Paint().apply {
                    alpha = (item.animCurrentAlpha * 255).toInt()
                    isAntiAlias = true
                }

            canvas.drawBitmap(bitmap, drawX, drawY, paint)
        }

        private fun drawDanmakuDirect(
            canvas: Canvas,
            item: DanmakuItem,
        ) {
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

            if (item.hasStroke && option.strokeWidth > 0) {
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

        private fun clearBitmapCache() {
            scrollDanmaku.forEach { it.dispose() }
            topDanmakus.forEach { it.dispose() }
            bottomDanmakus.forEach { it.dispose() }
            specialDanmakus.forEach { it.dispose() }
            batchPainter?.dispose()
            batchPainter = null
        }

        fun pause() {
            running = false
        }

        fun resume() {
            if (!running) {
                running = true
                lastFrameTime = System.currentTimeMillis()
                lastFrameTimestamp = lastFrameTime
                if ((scrollDanmaku.isNotEmpty() && scrollDanmaku.any { it.isVisible }) || specialDanmakus.isNotEmpty()) {
                    if (animator?.isRunning != true) {
                        startAnimation()
                    }
                }
            }
        }

        fun clear() {
            clearBitmapCache()
            scrollDanmaku.clear()
            trackDanmakus.forEach { it.clear() }
            topDanmakus.clear()
            bottomDanmakus.clear()
            specialDanmakus.clear()
            batchPainter = null
            frameRateController?.reset()
            animator?.cancel()
            animator = null
            cancelStaticCleanup()
            for (i in 0 until topTrackOccupancy.size) {
                topTrackOccupancy[i] = false
                bottomTrackOccupancy[i] = false
            }
            invalidate()
        }

        fun isRunning(): Boolean = running

        fun setBezierStyle(controller: BezierFrameRateController) {
            if (option.enableDynamicFrameRate) {
                frameRateController = controller
            }
        }

        fun setCustomBezier(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
        ) {
            if (option.enableDynamicFrameRate) {
                frameRateController =
                    BezierFrameRateController(
                        minFps = option.minFrameRate,
                        maxFps = option.maxFrameRate,
                        controlX1 = x1,
                        controlY1 = y1,
                        controlX2 = x2,
                        controlY2 = y2,
                    )
            }
        }

        fun getCurrentFps(): Float? = frameRateController?.getCurrentFps()

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator?.cancel()
            animator = null
            cancelStaticCleanup()
            clearBitmapCache()
            scrollDanmaku.clear()
            trackDanmakus.forEach { it.clear() }
            topDanmakus.clear()
            bottomDanmakus.clear()
            specialDanmakus.clear()
            frameRateController?.reset()
        }
    }
