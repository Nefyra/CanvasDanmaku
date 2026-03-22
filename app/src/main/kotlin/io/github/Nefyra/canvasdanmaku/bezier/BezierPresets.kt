package io.github.Nefyra.canvasdanmaku.bezier

/**
 * 预设贝塞尔曲线样式
 */
object BezierPresets {
    /**
     * 线性：无平滑效果
     * 适合需要精确响应的场景
     */
    val LINEAR =
        BezierFrameRateController(
            controlX1 = 0f,
            controlY1 = 0f,
            controlX2 = 1f,
            controlY2 = 1f,
        )

    /**
     * 缓入缓出：先慢后快再慢
     * 最自然，适合大多数场景
     */
    val EASE_IN_OUT =
        BezierFrameRateController(
            controlX1 = 0.42f,
            controlY1 = 0f,
            controlX2 = 0.58f,
            controlY2 = 1f,
        )

    /**
     * 缓出：快速响应，缓慢减速
     * 适合弹幕数量快速减少的场景
     */
    val EASE_OUT =
        BezierFrameRateController(
            controlX1 = 0f,
            controlY1 = 0f,
            controlX2 = 0.58f,
            controlY2 = 1f,
        )

    /**
     * 缓入：缓慢加速，快速停止
     * 适合弹幕数量快速增加的场景
     */
    val EASE_IN =
        BezierFrameRateController(
            controlX1 = 0.42f,
            controlY1 = 0f,
            controlX2 = 1f,
            controlY2 = 1f,
        )

    /**
     * 弹性效果
     * 适合游戏感较强的场景
     */
    val OVERSHOOT =
        BezierFrameRateController(
            controlX1 = 0.68f,
            controlY1 = -0.55f,
            controlX2 = 0.27f,
            controlY2 = 1.55f,
        )

    /**
     * 极平滑：非常缓慢的过渡
     * 适合对流畅度要求极高的场景
     */
    val EXTRA_SMOOTH =
        BezierFrameRateController(
            controlX1 = 0.2f,
            controlY1 = 0.8f,
            controlX2 = 0.8f,
            controlY2 = 0.2f,
        )

    /**
     * 快速响应：优先保证响应速度
     * 适合弹幕数量变化频繁的场景
     */
    val FAST_RESPONSE =
        BezierFrameRateController(
            controlX1 = 0.3f,
            controlY1 = 0f,
            controlX2 = 0.9f,
            controlY2 = 0.5f,
        )
}
