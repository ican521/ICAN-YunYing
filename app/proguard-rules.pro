# 默认混淆规则占位。
# 后续接入自定义视频源 / Spider 时在此补充 keep 规则。

# TVBox spider jar 按固定包名反射引用宿主基类，禁止混淆/裁剪
-keep class com.github.catvod.** { *; }
-dontwarn com.github.catvod.**

# spider jar 内解密类直接引用 gson/okhttp（R8 静态分析不可见），必须保留
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# media3 DefaultMediaSourceFactory 靠反射按 mimeType 加载各流媒体模块 Factory，
# R8 看不到直接引用会裁剪掉，release 必须 keep（缺失会导致 m3u8/mpd 播放崩溃）
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory { *; }
-dontwarn androidx.media3.exoplayer.hls.**
-dontwarn androidx.media3.exoplayer.dash.**
-dontwarn androidx.media3.exoplayer.smoothstreaming.**
