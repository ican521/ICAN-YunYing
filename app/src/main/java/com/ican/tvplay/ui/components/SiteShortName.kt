package com.ican.tvplay.ui.components

/**
 * 站源短名：只显示分隔符前的名称（站源名多用全角「｜」，兼容半角「|」），
 * 截完为空则回退 [fallback]（通常传 site.key）。
 */
fun siteShortName(name: String, fallback: String = ""): String =
    name.substringBefore("｜").substringBefore("|").trim().ifBlank { fallback }
