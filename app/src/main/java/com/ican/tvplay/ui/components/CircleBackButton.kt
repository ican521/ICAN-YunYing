package com.ican.tvplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.ican.tvplay.ui.theme.Spacing

/** 二级页面左上角统一返回按钮：48dp 圆形、surfaceVariant 底色、焦点缩放+按压效果 */
@Composable
fun CircleBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CircleIconButton(
        icon = AppIcons.Back,
        contentDescription = "返回",
        onClick = onClick,
        modifier = modifier,
    )
}

/** 通用 48dp 圆形图标按钮：与 CircleBackButton 同尺寸同样式（收藏/删除等顶栏圆钮复用） */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Spacing.circleButton)
            .tvCardEffect(
                onClick = onClick,
                shape = CircleShape,
                focusedScale = 1.1f,
                glow = false,
            )
            .clip(CircleShape)
            .background(containerColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
