package com.ican.tvplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import com.ican.tvplay.ui.nav.TopDestination
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 底部悬浮胶囊导航：真实液态玻璃（Haze，Android 12+ RenderEffect，
 * 低版本自动降级为半透明磨砂），深浅色主题自适应。
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassCapsuleBar(
    items: List<TopDestination>,
    currentRoute: String?,
    onNavigate: (TopDestination) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassTint = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.62f)

    Row(
        modifier = modifier
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = GlassStyle.regular.then {
                    shape(RoundedCornerShape(34.dp))
                    tint(glassTint)
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { destination ->
            CapsuleTab(
                destination = destination,
                selected = destination.route == currentRoute,
                onClick = { onNavigate(destination) },
            )
        }
    }
}

@Composable
private fun CapsuleTab(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pill = RoundedCornerShape(30.dp)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(220),
        label = "tabContainer",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                shape = pill,
                focusedScale = 1.06f,
                glow = false,
            )
            .background(containerColor, pill)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
        ) {
            Text(
                text = destination.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}
