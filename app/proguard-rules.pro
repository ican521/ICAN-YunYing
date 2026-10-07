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
