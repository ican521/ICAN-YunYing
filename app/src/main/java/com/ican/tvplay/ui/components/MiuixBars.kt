package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ican.tvplay.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 二级页顶栏返回按钮：圆形底 + miuix Back 图标。
 * 圆底样式与首页顶栏「链接解析 / 历史」按钮完全一致（48dp 圆形、surfaceContainerHigh 底色、
 * 同款按压缩放），保证全局顶栏按钮形态统一。
 *
 * @param showBackground 为 false 时不绘制圆形底色，仅保留箭头图标（追番更新页等要求纯箭头）
 * @param iconSize 箭头图标尺寸，默认 24dp；搜索页等与搜索栏并排的场景可传更小值收敛视觉
 */
/**
 * 箭头图标的光学修正比例（相对 iconSize 的横向位移）。
 * miuix ChevronBackward 的几何包围盒在 24dp 视口里是严格居中的（bbox 中心 603.6 = 视口中心 603.6，
 * 视口 1207.2），但箭头尖端在左、两条笔画向右张开，墨量重心实测偏右约 0.25dp/24dp，
 * 放进圆底里看着略偏右。按 iconSize 的 2% 往左挪，把视觉重心压回圆心。
 */
private const val BACK_ICON_OPTICAL_FRACTION = 0.02f

@Composable
fun MiuixBackButton(
    onClick: () -> Unit,
    showBackground: Boolean = true,
    iconSize: Dp = 24.dp,
    // 箭头颜色覆盖：默认 onSurfaceVariantSummary；与搜索栏并排的场景传搜索图标同款
    // onSurfaceContainerHigh（miuix InputField 前导搜索图标就是用这个色）
    tint: Color? = null,
) {
    val opticalDx = iconSize * BACK_ICON_OPTICAL_FRACTION
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Spacing.circleButton)
            .tvCardEffect(onClick = onClick, shape = CircleShape, focusedScale = 1.1f, glow = false)
            .clip(CircleShape)
            .then(
                if (showBackground) {
                    Modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh)
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = MiuixIcons.Regular.ChevronBackward,
            contentDescription = "返回",
            tint = tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .size(iconSize)
                .offset(x = -opticalDx),
        )
    }
}

/**
 * miuix 原生胶囊搜索输入框（全局通用）。
 * 照搬 KernelSU 搜索栏形态：全圆角胶囊、左侧搜索图标、输入后右侧出现清除钮、IME 搜索键提交。
 * 背景色用 miuix surfaceContainerHigh（KernelSU 搜索栏同款底色），其余样式全部 miuix 默认。
 *
 * @param expanded 搜索页固定传 true（常驻展开态，无折叠逻辑）；首页假胶囊传 false + enabled=false。
 */
@Composable
fun MiuixSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    // 默认收起态：miuix InputField 展开态每次重组都自动聚焦并弹键盘（擦掉小窗热恢复
    // 时搜索页重组 → 键盘把整页顶动）；收起态不聚焦，点击展开才弹键盘（对齐 fongmi）
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
