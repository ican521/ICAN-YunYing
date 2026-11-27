package com.ican.tvplay.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.ican.tvplay.player.core.Players
import java.util.Locale

/**
 * 全屏视频信息面板（顶栏 info 按钮展开，fongmi 调试面板的观感）：
 * 半透明黑底 + 等宽小字，只列片源的编码参数。
 * 标题/集名、网速、播放内核这些界面上本来就看得到的字段不重复显示。
 */
@Composable
fun VideoInfoPanel(
    info: Players.VideoInfo,
    /** 当前是否用 SurfaceView 渲染：HDR 片源只有 Surface 输出才可能真按 HDR 显示 */
    renderSurface: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = buildList {
        if (info.hasSize) add("分辨率" to "${info.width} × ${info.height}")
        // 画质：用大白话说清「片源是不是 HDR」+「现在实际怎么显示」，不出现 PQ/BT.2020 这类参数
        info.hdrSource?.let { hdr ->
            add(
                "画质" to when {
                    !hdr -> "普通画面"
                    info.hdrScreen == true && renderSurface -> "HDR"
                    else -> "HDR 片源（当前按普通画面显示）"
                },
            )
        }
        if (info.frameRate > 0f) add("帧率" to String.format(Locale.US, "%.3f fps", info.frameRate))
        val codec = buildString {
            info.videoMime?.substringAfterLast('/')?.uppercase(Locale.US)?.let { append(it) }
            info.videoCodecs?.let {
                if (isNotEmpty()) append("  ")
                append(it)
            }
        }
        if (codec.isNotBlank()) add("编码" to codec)
        if (info.bitDepth > 0) add("位深" to "${info.bitDepth} bit")
        val color = listOfNotNull(info.colorSpace, info.colorTransfer, info.colorRange)
        if (color.isNotEmpty()) add("色彩" to color.joinToString(" · "))
        if (info.videoBitrate > 0) add("码率" to formatBitrate(info.videoBitrate))
        // 解码器/丢帧只有 EXO 有数据；MPV 内核拿不到就不显示这两行
        info.decoderName?.let { decoder ->
            add("解码器" to "$decoder（${if (info.hardwareDecode) "硬解" else "软解"}）")
            add("丢帧" to info.droppedFrames.toString())
        }
        val audio = buildString {
            val mime = info.audioMime?.substringAfterLast('/')?.uppercase(Locale.US)
            val codecText = mime ?: info.audioCodecs
            if (!codecText.isNullOrBlank()) append(codecText)
            if (info.audioSampleRate > 0) {
                if (isNotEmpty()) append(" · ")
                append(String.format(Locale.US, "%.1f kHz", info.audioSampleRate / 1000f))
            }
            if (info.audioChannels > 0) {
                if (isNotEmpty()) append(" · ")
                append("${info.audioChannels} ch")
            }
            if (info.audioBitrate > 0) {
                if (isNotEmpty()) append(" · ")
                append(formatBitrate(info.audioBitrate))
            }
        }
        if (audio.isNotBlank()) add("音频" to audio)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "视频信息",
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        rows.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 标签是中文、值是数字/英文，两者字体 fallback 的行高不同，
                // 顶部对齐会差一两像素；统一按基线对齐
                Text(
                    text = label,
                    modifier = Modifier.width(48.dp).alignByBaseline(),
                    color = Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = value,
                    modifier = Modifier.alignByBaseline(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (info.unavailable || rows.isEmpty()) {
            Text(
                text = "当前内核读不到媒体参数",
                color = Color(0xB3FFFFFF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun formatBitrate(bps: Int): String = if (bps >= 1_000_000) {
    String.format(Locale.US, "%.2f Mbps", bps / 1_000_000f)
} else {
    String.format(Locale.US, "%.0f kbps", bps / 1000f)
}
