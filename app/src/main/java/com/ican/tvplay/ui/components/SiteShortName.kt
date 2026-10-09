package com.ican.tvplay.ui.components

/**
 * 站源短名：只显示分隔符前的名称，截完为空则回退 [fallback]（通常传 site.key）。
 * 分隔符兼容常见竖线变体（用 Unicode 转义，避免编译编码差异）：
 * ┃ 制表双线 U+2503（TVBox 配置最常用）、｜ 全角 U+FF5C、| 半角 U+007C、丨 U+4E28、︱ U+FE31。
 */
fun siteShortName(name: String, fallback: String = ""): String {
    val idx = name.indexOfAny(charArrayOf('\u2503', '\uFF5C', '|', '\u4E28', '\uFE31'))
    val cut = if (idx >= 0) name.substring(0, idx) else name
    return cut.trim().ifBlank { fallback }
}
