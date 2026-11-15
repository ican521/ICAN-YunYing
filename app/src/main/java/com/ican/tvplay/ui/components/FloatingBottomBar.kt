// Adapted from compose-miuix-ui example (IosLiquidGlassNavigationBar) — Apache 2.0.

package com.ican.tvplay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.ican.tvplay.ui.components.animation.DampedDragAnimation
import com.ican.tvplay.ui.theme.DarkSurface
import com.ican.tvplay.ui.theme.LocalDarkTheme
import com.ican.tvplay.ui.theme.Spacing
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun RowScope.FloatingBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
                onClick {
                    onClick()
                    true
                }
            }
            .onKeyEvent { event ->
                val activationKey = event.key == Key.Enter ||
                        event.key == Key.NumPadEnter || event.key == Key.Spacebar
                if (activationKey) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else false
            }
            .focusable()
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelected: (index: Int) -> Unit,
    backdrop: LayerBackdrop,
    tabsCount: Int,
    isFrostedBlur: Boolean = false,
    content: @Composable RowScope.((Int) -> Unit) -> Unit
) {
    // 用 App 内主题模式的实际深浅色状态，而非系统深浅色（App 内可独立切换浅色/深色）
    val isInDark = LocalDarkTheme.current
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    // 浅色模式关闭毛玻璃时：未选中图标文字用纯黑（对齐 KernelSU 浅色模式）
    val tabContentColor = if (!isInDark && !isFrostedBlur) Color.Black else MiuixTheme.colorScheme.onSurface
    // 深色模式关闭毛玻璃时：胶囊本体用不透明深灰 rgb(36,36,36)；浅色模式关闭毛玻璃时纯白
    val containerColor = if (isInDark) DarkSurface else Color.White
    // 关闭毛玻璃时指示器：浅/深色统一用主题色 15% 透明（浅色叠在白底上约等于原 #E1ECFE 的观感，且跟随色板）
    val plainIndicatorColor = accentColor.copy(alpha = 0.15f)

    // 底部普通毛玻璃（与顶栏同款 textureBlur）：设备支持 RenderEffect 时生效
    val frostedActive = isFrostedBlur && isRenderEffectSupported()

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onSelectedUpdated by rememberUpdatedState(onSelected)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabsCount - 1)
    }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            // 底栏不做按压缩放：点击、长按都保持原尺寸
            pressFeedbackEnabled = false,
            canDrag = { offset ->
                offset.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onSelectedUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        )
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (index !in 0 until tabsCount) return
        if (currentIndex != index) {
            currentIndex = index
            onSelectedUpdated(index)
        }
        dampedDragAnimation.animateToValue(index.toFloat())
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .selectableGroup()
                .graphicsLayer { translationX = panelOffset }
                // 阴影照搬 KernelSU：dropShadow 一圈对称模糊（radius 10dp，黑，浅色 0.1 / 深色 0.2）
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (isInDark) 0.2f else 0.1f,
                    ),
                )
                .then(
                    // 底部毛玻璃：与顶栏 BlurredBar 完全同款 textureBlur（12f 模糊 + 背景色 87% 混合）
                    if (frostedActive) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = pillShape,
                            blurRadius = 12f,
                            colors = BlurColors(
                                blendColors = listOf(
                                    BlendColorEntry(
                                        color = MiuixTheme.colorScheme.surface.copy(alpha = 0.87f),
                                    ),
                                ),
                            ),
                        )
                    } else {
                        Modifier.background(containerColor, pillShape)
                    }
                )
                .then(dampedDragAnimation.modifier)
                .height(Spacing.bottomBarHeight)
                .padding(Spacing.bottomBarInnerPadding)
                // 底层内容按「胶囊以外」的区域裁切，与上层主题色副本（裁到胶囊内）互补，
                // 两块互不重叠，既不会叠色黑边，分界线也跟随胶囊曲边，不会出现竖直直切
                .drawWithContent {
                    if (tabWidthPx <= 0f) {
                        this@drawWithContent.drawContent()
                    } else {
                        val windowStart = if (isLtr) {
                            dampedDragAnimation.value * tabWidthPx
                        } else {
                            size.width - dampedDragAnimation.value * tabWidthPx - tabWidthPx
                        }
                        val pill = Path().apply {
                            addOutline(
                                pillShape.createOutline(
                                    Size(tabWidthPx, size.height),
                                    layoutDirection,
                                    this@drawWithContent,
                                )
                            )
                            translate(Offset(windowStart, 0f))
                        }
                        clipPath(pill, ClipOp.Difference) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                content(::activateTab)
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                Modifier
                    .padding(horizontal = Spacing.bottomBarInnerPadding)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }
                    // 必须裁剪到胶囊范围：指示器内是整条 tab 内容的主题色副本，只露出当前页那一格
                    .clip(pillShape)
                    .height(56.dp)
                    .width(tabWidthDp),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 指示器背景：无任何按压缩放，点击、长按都保持原尺寸
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(pillShape)
                        // 关闭毛玻璃：浅色 #E1ECFE（KernelSU 同款视觉），深色主题色 15%
                        .background(plainIndicatorColor, pillShape),
                )
                CompositionLocalProvider(LocalContentColor provides accentColor) {
                    Row(
                        Modifier
                            .clearAndSetSemantics {}
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                            .height(56.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDragAnimation.value * tabWidthPx
                                translationX = if (isLtr) -progressOffset else progressOffset
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        content = { content(::activateTab) },
                    )
                }
            }
        }
    }
}
