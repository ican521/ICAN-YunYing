package com.ican.tvplay.player.core

import android.net.TrafficStats
import android.os.Process
import java.text.DecimalFormat

/**
 * 实时网速采样（照搬 fongmi com.fongmi.android.tv.utils.Traffic）。
 * 通过 TrafficStats.getUidRxBytes 以 1s 为周期计算瞬时下载速度，
 * 显示单位为 KB/s 或 MB/s。
 */
object Traffic {

    private val format = DecimalFormat("#.0")
    private const val UNIT_KB = " KB/s"
    private const val UNIT_MB = " MB/s"

    private var lastTotalRxBytes = 0L
    private var lastTimeStamp = 0L

    fun setSpeed(): String {
        if (TrafficStats.getUidRxBytes(Process.myUid()) == TrafficStats.UNSUPPORTED.toLong()) return ""
        val speed = getSpeed()
        return speed
    }

    private fun getSpeed(): String {
        val nowTimeStamp = System.currentTimeMillis()
        val nowTotalRxBytes = TrafficStats.getUidRxBytes(Process.myUid()) / 1024
        val speed = (nowTotalRxBytes - lastTotalRxBytes) * 1000 /
            kotlin.math.max(nowTimeStamp - lastTimeStamp, 1)
        lastTimeStamp = nowTimeStamp
        lastTotalRxBytes = nowTotalRxBytes
        return if (speed < 1000) speed.toString() + UNIT_KB
        else format.format(speed / 1024f) + UNIT_MB
    }

    fun reset() {
        lastTotalRxBytes = 0
        lastTimeStamp = 0
    }
}
