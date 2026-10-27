package com.ican.tvplay.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.ican.tvplay.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.utils.MiuixIndication

/**
 * 全局统一黑色 elevation 阴影（单点收口）：卡片/胶囊/圆钮/底栏共用同一实现。
 * 固定纯黑、与主题色脱钩（更换主题色板时阴影不跟随变化）；深色模式不画（纯黑背景上不可见）。
 */
@Composable
fun Modifier.blackElevationShadow(elevation: Dp, shape: Shape): Modifier =
    if (LocalDarkTheme.current) {
        this
    } else {
        this.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black,
            spotColor = Color.Black,
            clip = false,
        )
    }

/**
 * 全局统一的卡片交互效果（手机触屏 + TV 遥控器同一套）：
 *
 * 1. 遥控器/键盘聚焦：弹簧放大 + 主题色发光描边；
 * 2. 触摸按压：MiuixIndication 纯色蒙层（与 KernelSU 同款实现，来自 miuix 库）——
 *    按压 alpha +0.10，folmeSpring(1.0, 0.2) 渐显、folmeSpring(0.95, 0.35) 渐隐，无水波纹；
 * 3. 蒙层颜色取主题文字色（onSurface），浅色模式下为深灰、深色模式下为浅白。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvCardEffect(
    onClick: () -> Unit,
    shape: Shape,
    focusedScale: Float = 1.07f,
    glow: Boolean = true,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    /** 冻结缩放：为 true 时定格在当前缩放值（不归 1、不产生跳变），供下拉弹窗等依赖实时窗口坐标锚点的场景，避免缩放期间锚点漂移 */
    freezeScale: Boolean = false,
    /** 按住保持：为 true 时向 interactionSource 发射 HoldDownInteraction.HoldDown，MiuixIndication 保持按压蒙层（alpha 0.10），
     *  为 false 时发射 Release 渐隐恢复——即 miuix BasicComponent holdDownState 的同款机制（KernelSU OverlayDropdownPreference 用法） */
    holdDownState: Boolean = false,
    /** 触摸按压蒙层开关：false 时点击仍生效但无任何按压/点击视觉反馈。
     *  用于本身带选中态的互斥选择列表（站源/线路/选集等），选中色已是足够的状态反馈 */
    pressEffect: Boolean = true,
    /** 按压蒙层颜色覆盖：null 时取主题 onSurface（浅色深灰、深色浅白，随主题适配）。
     *  视频卡片等海报类组件固定传浅黑色，保证深色模式按压特效与浅色模式完全一致 */
    pressColor: Color? = null,
    /** 非 null 时改用 miuix squircleClip(该半径) 连续曲率角裁剪（API 33+ shader，低版本自动回退
     *  clip(shape) 圆弧），与 miuix Card squircleSurface 轮廓逐像素一致；为 null 时用普通 [shape] 圆弧裁剪 */
    squircleRadius: Dp? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // 照抄 miuix HoldDownObserver：把 holdDownState 镜像到 interactionSource（HoldDown/Release），
    // 松开真实手指后由 HoldDown 接续保持按压色，状态归 false 后才释放渐隐
    var heldDown by remember { mutableStateOf<HoldDownInteraction.HoldDown?>(null) }
    LaunchedEffect(holdDownState, interactionSource) {
        suspend fun release() {
            heldDown?.let { current ->
                interactionSource.emit(HoldDownInteraction.Release(current))
                heldDown = null
            }
        }
        if (holdDownState) {
            release()
            val interaction = HoldDownInteraction.HoldDown()
            heldDown = interaction
            interactionSource.emit(interaction)
        } else {
            release()
        }
    }
    DisposableEffect(interactionSource) {
        onDispose {
            heldDown?.let { current ->
                interactionSource.tryEmit(HoldDownInteraction.Release(current))
            }
            heldDown = null
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tvCardScale",
    )
    // 冻结瞬间记录当前视觉缩放值并定格：焦点随后变化也不响应，解冻时从该值平滑恢复
    var frozenAt by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(freezeScale) {
        frozenAt = if (freezeScale) animatedScale else null
    }

    val accent = MaterialTheme.colorScheme.primary
    val isDark = LocalDarkTheme.current

    // KernelSU 同款按压特效：miuix 库 MiuixIndication，蒙层色默认取主题文字色（深浅色模式自动适配）；
    // pressColor 显式指定时用指定色（视频卡片固定浅黑，深色模式与浅色表现一致）
    val pressIndicationColor = pressColor ?: MaterialTheme.colorScheme.onSurface
    val pressIndication = remember(pressIndicationColor) { MiuixIndication(color = pressIndicationColor) }
    // 深色模式补强：弹窗展开期间暗遮罩（60% 黑）会把 MiuixIndication 的 10% 白蒙层压到不可见，
    // 保持态再叠一层同色 20% 蒙层（仅深色），保证「弹窗存活期卡片持续按压变色」肉眼可见
    val holdOverlayColor = pressIndicationColor
    val showHoldOverlay = holdDownState && isDark

    return this
        // 动画值在 graphicsLayer lambda 内（绘制阶段）读取：缩放动画期间不再每帧触发重组
        .graphicsLayer {
            val currentScale = frozenAt ?: animatedScale
            scaleX = currentScale
            scaleY = currentScale
        }
        .then(
            if (glow) {
                Modifier.blackElevationShadow(if (focused) 18.dp else 6.dp, shape)
            } else {
                Modifier
            },
        )
        .then(
            if (squircleRadius != null) Modifier.squircleClip(squircleRadius) else Modifier.clip(shape),
        )
        .combinedClickable(
            interactionSource = interactionSource,
            indication = if (pressEffect) pressIndication else null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        .drawWithContent {
            drawContent()

            if (showHoldOverlay) {
                drawRect(color = holdOverlayColor, alpha = 0.20f, size = size)
            }

            if (focused) {
                val strokeWidth = 2.dp.toPx()
                drawRect(
                    color = accent.copy(alpha = 0.95f),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
}
