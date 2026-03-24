package io.github.Nefyra.canvasdanmaku.timeline

import io.github.Nefyra.canvasdanmaku.DanmakuType
import io.github.Nefyra.canvasdanmaku.DanmakuView

/**
 * 时间轴弹幕数据模型
 * @param timeSeconds 弹幕出现时间，单位为秒，支持小数点后两位以上
 * @param text 弹幕文本
 * @param color 弹幕颜色（ARGB 格式）
 * @param type 弹幕类型：0-普通滚动，1-顶部固定，2-底部固定
 */
data class TimelineDanmaku(
    val timeSeconds: Double,
    val text: String,
    val color: Int,
    val type: Int
)

/**
 * 时间轴弹幕控制器
 * 将弹幕按秒分组，根据播放器当前时间索引（整数秒）发送对应秒的所有弹幕
 * @param danmakuView 关联的弹幕视图，用于实际添加弹幕
 */
class TimelineDanmakuController(private val danmakuView: DanmakuView) {

    // 按秒分组的弹幕映射表：秒数 -> 该秒内的弹幕列表
    private val groupedDanmakus = mutableMapOf<Int, MutableList<TimelineDanmaku>>()
    
    // 记录已发送的秒数
    private val sentSeconds = mutableSetOf<Int>()
    
    // 记录所有弹幕的最小和最大时间（秒）
    private var minSecond = Int.MAX_VALUE
    private var maxSecond = Int.MIN_VALUE

    /**
     * 加载弹幕列表
     * 会自动清空已有数据、按秒分组并重置发送标记
     */
    fun loadDanmakus(danmakus: List<TimelineDanmaku>) {
        groupedDanmakus.clear()
        sentSeconds.clear()
        minSecond = Int.MAX_VALUE
        maxSecond = Int.MIN_VALUE
        
        danmakus.forEach { danmaku ->
            val second = danmaku.timeSeconds.toInt()
            
            if (second < minSecond) minSecond = second
            if (second > maxSecond) maxSecond = second
            
            val list = groupedDanmakus.getOrPut(second) { mutableListOf() }
            list.add(danmaku)
        }
        
        groupedDanmakus.values.forEach { list ->
            list.sortBy { it.timeSeconds }
        }
    }

    /**
     * 根据当前播放时间索引发送对应秒的所有弹幕
     */
    fun sendBySecondIndex(currentSecond: Int) {
        if (currentSecond < minSecond || currentSecond > maxSecond) return
        
        if (sentSeconds.contains(currentSecond)) return
        
        val danmakus = groupedDanmakus[currentSecond] ?: return
        
        danmakus.forEach { danmaku ->
            val type = when (danmaku.type) {
                0 -> DanmakuType.SCROLL
                1 -> DanmakuType.TOP
                2 -> DanmakuType.BOTTOM
                else -> DanmakuType.SCROLL
            }
            
            danmakuView.addDanmaku(
                text = danmaku.text,
                color = danmaku.color,
                selfSend = false,
                hasStroke = true,
                type = type
            )
        }
        
        sentSeconds.add(currentSecond)
    }

    /**
     * 批量发送从起始秒到当前秒的所有未发送弹幕
     */
    fun sendUpToSecond(currentSecond: Int) {
        for (second in minSecond..currentSecond) {
            sendBySecondIndex(second)
        }
    }

    /**
     * 重置所有发送标记
     */
    fun reset() {
        sentSeconds.clear()
    }

    /**
     * 清空所有弹幕数据并重置标记
     */
    fun clear() {
        groupedDanmakus.clear()
        sentSeconds.clear()
        minSecond = Int.MAX_VALUE
        maxSecond = Int.MIN_VALUE
    }

    /**
     * 获取已发送的秒数数量
     */
    fun getSentSecondCount(): Int = sentSeconds.size

    /**
     * 获取总秒数（包含弹幕的秒数）
     */
    fun getTotalSecondsWithDanmaku(): Int = groupedDanmakus.size

    /**
     * 获取指定秒的弹幕数量
     */
    fun getDanmakuCountInSecond(second: Int): Int = groupedDanmakus[second]?.size ?: 0

    /**
     * 检查指定秒是否已发送
     */
    fun isSecondSent(second: Int): Boolean = sentSeconds.contains(second)
}