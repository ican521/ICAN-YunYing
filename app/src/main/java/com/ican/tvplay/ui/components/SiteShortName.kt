package com.ican.tvplay.ui.components

/**
 * 站源短名：只显示分隔符前的名称，截完为空则回退 [fallback]（通常传 site.key）。
 * 分隔符兼容常见竖线变体：┃（制表双线，TVBox 配置最常用）、｜（全角）、|（半角）、丨、︱。
 */
fun siteShortName(name: String, fallback: String = ""): String {
    val idx = charArrayOf('┃', '｜', '|', '丨', '︱').minOf { name.indexOf(it) }
    val cut = if (idx >= 0) name.substring(0, idx) else name
    return cut.trim().ifBlank { fallback }
}
