package com.ican.tvplay.player.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

/** 外挂字幕（仿 fongmi bean/Sub 精简版） */
data class SubItem(
    val name: String,
    val url: String,
    val mimeType: String? = null,
    val lang: String? = null,
)

/**
 * 播放规格（仿 fongmi player/media/PlaySpec 精简版）：
 * 一次播放所需的全部输入：url + 请求头 + 外挂字幕 + 容器格式提示。
 */
data class PlaySpec(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subs: List<SubItem> = emptyList(),
    /** 容器格式提示（错误重试时按 fongmi ExoUtil.getMimeType 逻辑设置） */
    val format: String? = null,
)

/** 仿 fongmi MediaItemFactory：由 PlaySpec 构建 MediaItem（避开 fork 私有 API setAdblock 等） */
object MediaItemFactory {

    /** fongmi 默认 UA（与 VodApiClient 保持一致） */
    const val DEFAULT_UA = "okhttp/4.12.0"

    /** headers 无 User-Agent 时补默认 UA（仿 PlaySpec.checkUa） */
    fun checkUa(headers: Map<String, String>): Map<String, String> {
        if (headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) return headers
        return headers + ("User-Agent" to DEFAULT_UA)
    }

    fun from(spec: PlaySpec): MediaItem {
        return MediaItem.Builder()
            .setUri(Uri.parse(spec.url))
            .setMimeType(spec.format)
            .setSubtitleConfigurations(spec.subs.map(::buildSubConfig))
            .build()
    }

    private fun buildSubConfig(sub: SubItem): MediaItem.SubtitleConfiguration {
        val uri = Uri.parse(sub.url)
        val mime = sub.mimeType ?: guessSubtitleMime(uri.path)
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setLabel(sub.name)
            .setMimeType(mime)
            .setLanguage(sub.lang)
            .setSelectionFlags(C.SELECTION_FLAG_AUTOSELECT)
            .build()
    }

    /** 按扩展名猜字幕格式（仿 fongmi TrackUtil.getSubtitleMimeType） */
    fun guessSubtitleMime(path: String?): String {
        val p = path?.lowercase().orEmpty()
        return when {
            p.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            p.endsWith(".ass") || p.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            p.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            p.endsWith(".ttml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
