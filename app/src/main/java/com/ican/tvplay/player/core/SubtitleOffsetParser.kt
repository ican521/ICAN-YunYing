package com.ican.tvplay.player.core

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleParser

/**
 * 字幕时间偏移（仿 fongmi 5.8.0 的 SubtitleOffsets）：
 * media3 1.11.1 尚未提供播放器级偏移 API，这里在外挂字幕解析阶段平移时间轴。
 * 注意：仅对外挂字幕（SubtitleConfiguration）生效。
 */
@OptIn(UnstableApi::class)
internal class SubtitleOffsetParserFactory(
    private val offsetMsProvider: () -> Long,
) : SubtitleParser.Factory {

    private val delegate = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean {
        val result = delegate.supportsFormat(format)
        if (com.ican.tvplay.BuildConfig.DEBUG) {
            android.util.Log.d("SubtitleOffset", "supportsFormat mime=${format.sampleMimeType} result=$result")
        }
        return result
    }

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        if (com.ican.tvplay.BuildConfig.DEBUG) {
            android.util.Log.d("SubtitleOffset", "create mime=${format.sampleMimeType}")
        }
        return OffsetSubtitleParser(delegate.create(format), offsetMsProvider)
    }
}

@OptIn(UnstableApi::class)
private class OffsetSubtitleParser(
    private val delegate: SubtitleParser,
    private val offsetMsProvider: () -> Long,
) : SubtitleParser {

    override fun parse(
        data: ByteArray,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        delegate.parse(data, outputOptions) { cues ->
            if (com.ican.tvplay.BuildConfig.DEBUG) {
                android.util.Log.d("SubtitleOffset", "parse cues=${cues.cues.size} startUs=${cues.startTimeUs} durUs=${cues.durationUs} dataLen=${data.size}")
            }
            output.accept(shift(cues))
        }
    }

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        delegate.parse(data, offset, length, outputOptions) { cues ->
            if (com.ican.tvplay.BuildConfig.DEBUG) {
                android.util.Log.d("SubtitleOffset", "parse(offset) cues=${cues.cues.size} startUs=${cues.startTimeUs} durUs=${cues.durationUs} dataLen=$length")
            }
            output.accept(shift(cues))
        }
    }

    override fun parseToLegacySubtitle(data: ByteArray, offset: Int, length: Int): Subtitle =
        delegate.parseToLegacySubtitle(data, offset, length)

    override fun reset() = delegate.reset()

    override fun getCueReplacementBehavior(): Int = delegate.getCueReplacementBehavior()

    private fun shift(cues: CuesWithTiming): CuesWithTiming {
        val offsetUs = offsetMsProvider() * 1000L
        if (offsetUs == 0L) return cues
        val start = cues.startTimeUs + offsetUs
        return if (start >= 0L) {
            CuesWithTiming(cues.cues, start, cues.durationUs)
        } else {
            // 提前到 0 之前的字幕从 0 开始显示，时长相应缩短
            CuesWithTiming(cues.cues, 0L, (cues.durationUs + start).coerceAtLeast(0L))
        }
    }
}
