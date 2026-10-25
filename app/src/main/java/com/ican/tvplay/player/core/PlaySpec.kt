package com.ican.tvplay.player.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.nio.charset.StandardCharsets
import java.util.UUID

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

    fun from(spec: PlaySpec): MediaItem = from(spec, mpv = false)

    /** MPV 的 sub-add 对 file:/// 兼容性差：字幕统一给 libass 可读的本机绝对路径 */
    fun fromMpv(spec: PlaySpec): MediaItem = from(spec, mpv = true)

    private fun from(spec: PlaySpec, mpv: Boolean): MediaItem {
        return MediaItem.Builder()
            .setUri(Uri.parse(spec.url))
            // MPV 不要强制 m3u8：MpvNetworkOptions 会据此设置 demuxer-lavf-format=hls，
            // 该选项残留会把后续 sub-add 的外挂 ASS 也强制按 HLS 打开（mpv.log: Found 'hls' forced）。
            .setMimeType(if (mpv) null else spec.format)
            .setSubtitleConfigurations(spec.subs.mapIndexed { index, sub -> buildSubConfig(sub, index, spec.subs.size, mpv) })
            .build()
    }

    private fun buildSubConfig(sub: SubItem, index: Int, total: Int, mpv: Boolean): MediaItem.SubtitleConfiguration {
        val uri = subtitleUri(sub.url, mpv)
        val mime = sub.mimeType ?: guessSubtitleMime(uri.path)
        // 仿 fongmi Sub.getFlag()：raw flag=0 也要回 DEFAULT；MPV sub-add 需要 default 才会真正选中显示，
        // 否则只会 cached/auto 挂上去但不显示。
        val flag = when {
            index == 0 -> C.SELECTION_FLAG_DEFAULT
            else -> C.SELECTION_FLAG_AUTOSELECT
        }
        return MediaItem.SubtitleConfiguration.Builder(uri)
            // fongmi 同款稳定 id：保证外挂字幕轨在重建/切内核后可被同一个 override 选中
            .setId("external:" + UUID.nameUUIDFromBytes(sub.url.toByteArray(StandardCharsets.UTF_8)))
            .setLabel(sub.name)
            .setMimeType(mime)
            .setLanguage(sub.lang)
            .setSelectionFlags(flag)
            .build()
    }

    /** 构建单个外挂字幕 SubtitleConfiguration（用于 MPV player.addSubtitle 动态添加） */
    fun buildSingleSubConfig(sub: SubItem): MediaItem.SubtitleConfiguration {
        return buildSubConfig(sub, 0, 1, mpv = false)
    }

    /** MPV 动态添加字幕同样用本机绝对路径，避免 file:/// 被 libass/mpv 拒绝 */
    fun buildMpvSubConfig(sub: SubItem): MediaItem.SubtitleConfiguration {
        return buildSubConfig(sub, 0, 1, mpv = true)
    }

    private fun subtitleUri(url: String, mpv: Boolean): Uri {
        val uri = Uri.parse(url)
        if (!mpv) return uri
        if (!uri.scheme.equals("file", ignoreCase = true)) return uri
        val path = uri.path
        return if (path.isNullOrBlank()) uri else Uri.parse(path)
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
