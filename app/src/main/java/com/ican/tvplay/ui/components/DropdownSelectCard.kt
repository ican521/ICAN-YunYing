package com.ican.tvplay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ican.tvplay.ui.theme.DarkSurfaceElevated
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ======================== KernelSU / miuix OverlayDropdownPreference 1:1 复刻 ========================
// 定位、宽度、动画、变暗、条目样式均照抄 miuix 库 ListPopupLayout / ListPopupColumn /
// DropdownImpl / dropdownPositionProvider 的实现，仅将主题色映射到本项目。

/** miuix ListPopupDefaults.FractionAnimationSpec：缩放/裁剪展开进度弹簧 */
private val FractionAnimationSpec = spring(dampingRatio = 0.82f, stiffness = 362.5f, visibilityThreshold = 0.0001f)

/** miuix 动画节奏：内容渐显 200ms / 渐隐 150ms；变暗渐显 300ms / 渐隐 150ms */
private const val ALPHA_ENTER_MS = 200
private const val ALPHA_EXIT_MS = 150
private const val DIM_ENTER_MS = 300
private const val DIM_EXIT_MS = 150

/** miuix ListPopupDefaults：弹窗宽度范围 [200dp, 288dp]、最小高度 50dp、圆角 16dp、垂直边距 8dp */
private val PopupMinWidth = 200.dp
private val PopupMaxWidth = 288.dp
private val PopupMinHeight = 50.dp
private val PopupCornerRadius = 16.dp
private val PopupVerticalMargin = 8.dp

/** miuix DropdownDefaults：行水平内边距 20dp、首末行 20dp、中间行 12dp、对勾 20dp、文字最大宽 216dp */
private val RowHorizontalPadding = 20.dp
private val RowFirstLastVerticalPadding = 20.dp
private val RowMiddleVerticalPadding = 12.dp
private val CheckIconSize = 20.dp
private val CheckIconStartPadding = 12.dp
private val MaxItemTextWidth = 216.dp

/** miuix windowDimming：浅色黑 30%、深色黑 60% */
private val WindowDimmingLight = Color.Black.copy(alpha = 0.3f)
private val WindowDimmingDark = Color.Black.copy(alpha = 0.6f)

/**
 * KernelSU 风格下拉选择卡（全局通用）。
 * 整卡可点击，右侧显示当前值 + 上下箭头；点击后全屏渐暗 + 选项弹窗在卡片下方展开。
 */
@Composable
fun DropdownSelectCard(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var show by remember { mutableStateOf(false) }
    var mounted by remember { mutableStateOf(false) }
    val isDark = LocalDarkTheme.current
    val shape = RoundedCornerShape(Spacing.corner)
    // 锚点（整卡）在窗口中的坐标：取父容器布局坐标，不受焦点缩放影响（miuix 同款做法）
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }

    val fraction = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }

    LaunchedEffect(show) {
        if (show) {
            mounted = true
            fraction.snapTo(0f)
            alpha.snapTo(0f)
            dim.snapTo(0f)
            launch { fraction.animateTo(1f, FractionAnimationSpec) }
            launch { alpha.animateTo(1f, tween(ALPHA_ENTER_MS)) }
            launch { dim.animateTo(1f, tween(DIM_ENTER_MS)) }
        } else if (mounted) {
            launch { fraction.animateTo(0f, FractionAnimationSpec) }
            launch { dim.animateTo(0f, tween(DIM_EXIT_MS)) }
            // 渐隐为主时钟：结束后卸载弹窗（miuix 同款）
            alpha.animateTo(0f, tween(ALPHA_EXIT_MS))
            fraction.snapTo(0f)
            alpha.snapTo(0f)
            dim.snapTo(0f)
            mounted = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInWindow()
                anchorBounds = IntRect(
                    pos.x.roundToInt(),
                    pos.y.roundToInt(),
                    (pos.x + coordinates.size.width).roundToInt(),
                    (pos.y + coordinates.size.height).roundToInt(),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tvCardEffect(
                    onClick = { show = true },
                    shape = shape,
                    focusedScale = 1.02f,
                    glow = false,
                )
                .background(MaterialTheme.colorScheme.surface, shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.titleContentGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = options.getOrElse(selectedIndex) { "" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = AppIcons.Unfold,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (mounted) {
        MiuixStyleDropdown(
            visibleFraction = { fraction.value },
            contentAlpha = { alpha.value },
            dimAlpha = { dim.value },
            anchorBounds = anchorBounds,
            options = options,
            selectedIndex = selectedIndex,
            isDark = isDark,
            onDismiss = { show = false },
            onSelect = { index ->
                if (index != selectedIndex) onSelect(index)
                show = false
            },
        )
    }
}

/**
 * miuix OverlayListPopup 等价实现：Dialog 全屏层 + 渐暗层 + 按锚点定位的选项弹窗。
 * 定位逻辑与 dropdownPositionProvider 一致：End 对齐锚点右缘、默认显示在锚点下方 8dp，
 * 下方空间不足时回退到上方，再不足时垂直居中，最后按安全窗口区收拢。
 */
@Composable
private fun MiuixStyleDropdown(
    visibleFraction: () -> Float,
    contentAlpha: () -> Float,
    dimAlpha: () -> Float,
    anchorBounds: IntRect,
    options: List<String>,
    selectedIndex: Int,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInfo = LocalWindowInfo.current
    val containerSize = windowInfo.containerSize

    // 安全窗口区（排除状态栏/导航栏/刘海），miuix rememberListPopupLayoutInfo 同款
    val windowBounds = IntRect(
        left = WindowInsets.displayCutout.getLeft(density, layoutDirection),
        top = WindowInsets.statusBars.getTop(density),
        right = containerSize.width - WindowInsets.displayCutout.getRight(density, layoutDirection),
        bottom = containerSize.height - WindowInsets.navigationBars.getBottom(density),
    )
    val marginPx = with(density) { PopupVerticalMargin.roundToPx() }
    val margin = IntRect(0, marginPx, 0, marginPx)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier.requiredSize(
                with(density) {
                    androidx.compose.ui.unit.DpSize(
                        containerSize.width.toFloat().toDp(),
                        containerSize.height.toFloat().toDp(),
                    )
                },
            ),
        ) {
            // 全屏渐暗层（点击任意处关闭）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dimAlpha() }
                    .background(if (isDark) WindowDimmingDark else WindowDimmingLight)
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    },
            )

            // 弹窗：测量 → 定位 → 放置（miuix ListPopupLayout 同款自定义布局）
            var popupSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = 0,
                                maxWidth = constraints.maxWidth,
                                minHeight = 0,
                                maxHeight = (windowBounds.height - margin.top - margin.bottom)
                                    .coerceAtLeast(PopupMinHeight.roundToPx()),
                            ),
                        )
                        val measured = IntSize(placeable.width, placeable.height)
                        val offset = calculateDropdownPosition(anchorBounds, windowBounds, measured, margin)
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(offset)
                        }
                    },
            ) {
                DropdownPopupContent(
                    options = options,
                    selectedIndex = selectedIndex,
                    isDark = isDark,
                    anchorBounds = anchorBounds,
                    windowBounds = windowBounds,
                    popupSize = popupSize,
                    visibleFraction = visibleFraction,
                    contentAlpha = contentAlpha,
                    onSizeChanged = { popupSize = it },
                    onSelect = onSelect,
                )
            }
        }
    }
}

/** miuix dropdownPositionProvider（Align.End）：右缘对齐锚点、下方优先、上方/居中回退、安全区收拢 */
private fun calculateDropdownPosition(
    anchorBounds: IntRect,
    windowBounds: IntRect,
    popupSize: IntSize,
    margin: IntRect,
): IntOffset {
    val offsetX = anchorBounds.right - popupSize.width - margin.right
    val offsetY = when {
        windowBounds.bottom - anchorBounds.bottom > popupSize.height -> {
            // 下方空间充足
            anchorBounds.bottom + margin.bottom
        }
        anchorBounds.top - windowBounds.top > popupSize.height -> {
            // 上方空间充足
            anchorBounds.top - popupSize.height - margin.top
        }
        else -> {
            // 垂直居中
            anchorBounds.top + anchorBounds.height / 2 - popupSize.height / 2
        }
    }
    return IntOffset(
        x = offsetX.coerceIn(
            windowBounds.left,
            (windowBounds.right - popupSize.width - margin.right).coerceAtLeast(windowBounds.left),
        ),
        y = offsetY.coerceIn(
            (windowBounds.top + margin.top).coerceAtMost(windowBounds.bottom - popupSize.height - margin.bottom),
            windowBounds.bottom - popupSize.height - margin.bottom,
        ),
    )
}

/**
 * miuix ListPopupColumn + ListPopupContent 等价实现：
 * 宽度 = 最宽条目固有宽度 clamp 到 [200dp, 288dp]；缩放 + 方向性裁剪展开 + 渐显动画。
 */
@Composable
private fun DropdownPopupContent(
    options: List<String>,
    selectedIndex: Int,
    isDark: Boolean,
    anchorBounds: IntRect,
    windowBounds: IntRect,
    popupSize: IntSize,
    visibleFraction: () -> Float,
    contentAlpha: () -> Float,
    onSizeChanged: (IntSize) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val popupShape = RoundedCornerShape(PopupCornerRadius)
    val popupBackground = if (isDark) DarkSurfaceElevated else MaterialTheme.colorScheme.surface

    // 派生展开方向与对齐侧（决定缩放锚点与裁剪方向，miuix popupLayoutPosition 同款）
    val density = LocalDensity.current
    val marginPx = with(density) { PopupVerticalMargin.roundToPx() }
    val margin = IntRect(0, marginPx, 0, marginPx)
    val showBelow: Boolean
    val isRightAligned: Boolean
    if (popupSize == IntSize.Zero) {
        showBelow = true
        isRightAligned = true
    } else {
        val offset = calculateDropdownPosition(anchorBounds, windowBounds, popupSize, margin)
        val popupCenterY = offset.y + popupSize.height / 2
        val anchorCenterY = anchorBounds.top + anchorBounds.height / 2
        showBelow = popupCenterY >= anchorCenterY
        val distLeft = kotlin.math.abs(offset.x - anchorBounds.left)
        val distRight = kotlin.math.abs(offset.x + popupSize.width - anchorBounds.right)
        isRightAligned = distRight <= distLeft
    }
    val localTransformOrigin = TransformOrigin(
        pivotFractionX = if (isRightAligned) 1f else 0f,
        pivotFractionY = if (showBelow) 0f else 1f,
    )

    Layout(
        content = {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val verticalPadding =
                    if (index == 0 || index == options.size - 1) RowFirstLastVerticalPadding
                    else RowMiddleVerticalPadding
                Row(
                    modifier = Modifier
                        .tvCardEffect(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSelect(index)
                            },
                            shape = popupShape,
                            focusedScale = 1f,
                            glow = false,
                        )
                        .padding(horizontal = RowHorizontalPadding)
                        .padding(top = verticalPadding, bottom = verticalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = MaxItemTextWidth),
                    )
                    // 尾部对勾：未选中项保持透明占位，保证所有行右缘对齐（miuix 同款）
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .padding(start = CheckIconStartPadding)
                            .size(CheckIconSize),
                    )
                }
            }
        },
        modifier = Modifier
            .onGloballyPositioned { onSizeChanged(it.size) }
            .graphicsLayer {
                val f = visibleFraction().coerceIn(0f, 1f)
                val scale = 0.15f + 0.85f * f
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha()
                transformOrigin = localTransformOrigin
            }
            // 方向性裁剪展开：下方弹出时自顶向下展开（miuix popupClipReveal 同款）
            .drawWithCache {
                val path = Path()
                val showBelowLocal = showBelow
                onDrawWithContent {
                    val progress = visibleFraction().coerceIn(0f, 1f)
                    if (progress <= 0f) return@onDrawWithContent
                    val height = size.height
                    val visibleHeight = height * progress
                    if (visibleHeight <= 0f) return@onDrawWithContent
                    val clipStart = if (showBelowLocal) 0f else height * (1f - progress)
                    path.rewind()
                    path.addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = Rect(0f, 0f, size.width, visibleHeight),
                            topLeft = androidx.compose.ui.geometry.CornerRadius(PopupCornerRadius.toPx()),
                            topRight = androidx.compose.ui.geometry.CornerRadius(PopupCornerRadius.toPx()),
                            bottomRight = androidx.compose.ui.geometry.CornerRadius(PopupCornerRadius.toPx()),
                            bottomLeft = androidx.compose.ui.geometry.CornerRadius(PopupCornerRadius.toPx()),
                        ),
                    )
                    if (clipStart == 0f) {
                        clipPath(path) { this@onDrawWithContent.drawContent() }
                    } else {
                        translate(top = clipStart) {
                            clipPath(path) {
                                translate(top = -clipStart) { this@onDrawWithContent.drawContent() }
                            }
                        }
                    }
                }
            }
            .background(popupBackground, popupShape),
    ) { measurables, constraints ->
        // 宽度 = 最宽条目固有宽度 clamp 到 [200dp, 288dp]（miuix ListPopupColumn 同款）
        val minPx = PopupMinWidth.roundToPx()
        val maxPx = PopupMaxWidth.roundToPx()
        var maxIntrinsic = 0
        for (measurable in measurables) {
            val w = measurable.maxIntrinsicWidth(constraints.maxHeight)
            if (w > maxIntrinsic) maxIntrinsic = w
        }
        val listWidth = maxIntrinsic.coerceIn(minPx, maxPx.coerceAtMost(constraints.maxWidth))
        val childConstraints = constraints.copy(minWidth = listWidth, maxWidth = listWidth, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val totalHeight = placeables.sumOf { it.height }
        layout(listWidth, totalHeight) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}
