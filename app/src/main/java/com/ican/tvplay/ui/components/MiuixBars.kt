package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 全局毛玻璃开关（AppRoot 按设置页「全局模糊」开关提供，KernelSU 同款机制） */
val LocalEnableBlur = staticCompositionLocalOf { false }

/** 顶栏毛玻璃独立开关（AppRoot 按设置页「顶栏毛玻璃」开关提供，与全局模糊互不影响） */
val LocalEnableTopBarBlur = staticCompositionLocalOf { true }

/**
 * 顶栏毛玻璃内容源（照搬 KernelSU ui/util/BlurExt.kt）：
 * 毛玻璃关闭或设备不支持 RenderEffect 时返回 null（顶栏退化为不透明 surface 底色）。
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    // 基色用项目 background（深色纯黑），不用 miuix surface（深灰），保证深色下毛玻璃与全局底色一致
    val surfaceColor = MaterialTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 毛玻璃顶栏容器（照搬 KernelSU BlurredBar）：
 * backdrop 可用时对内容做 25f textureBlur + surface 87% 混合，否则原样渲染。
 */
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MaterialTheme.colorScheme.background.copy(alpha = 0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}

/**
 * 二级页顶栏返回按钮：圆形底 + miuix Back 图标。
 * 圆底样式与首页顶栏「链接解析 / 历史」按钮完全一致（48dp 圆形、surfaceContainerHigh 底色、
 * 同款按压缩放），保证全局顶栏按钮形态统一。
 */
@Composable
fun MiuixBackButton(
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Spacing.circleButton)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.1f, glow = false)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * miuix 原生胶囊搜索输入框（全局通用）。
 * 照搬 KernelSU 搜索栏形态：全圆角胶囊、左侧搜索图标、输入后右侧出现清除钮、IME 搜索键提交。
 * 背景色用 miuix surfaceContainerHigh（KernelSU 搜索栏同款底色），其余样式全部 miuix 默认。
 *
 * @param expanded 搜索页由外部管理：点击输入框置 true（正常聚焦弹键盘），
 *   点击外部/提交搜索置 false（收起键盘）。首页假胶囊传 false + enabled=false。
 *   固定传 false 会导致 miuix InputField 内部 100ms 后自动清焦点清文字，
 *   键盘动画与输入状态不一致出现上移过冲。
 */
@Composable
fun MiuixSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    // 展开态由外部管理：点击输入框时 onExpandedChange(true)，外部置 expanded=true 让 InputField
    // 正常聚焦弹键盘；点击外部/提交时置 false 收起键盘。默认 false 供首页假胶囊使用。
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        label = label,
        enabled = enabled,
        color = MiuixTheme.colorScheme.surfaceContainerHigh,
    )
}
