package com.ican.tvplay.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ican.tvplay.data.model.Video
import com.ican.tvplay.data.model.VideoCategory
import com.ican.tvplay.ui.HomeViewModel
import com.ican.tvplay.ui.appViewModel
import com.ican.tvplay.ui.components.AppIcons
import com.ican.tvplay.ui.components.topLevelContentPadding
import com.ican.tvplay.ui.components.tvCardEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
) {
    val viewModel = appViewModel { HomeViewModel(this) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = topLevelContentPadding(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text(
                text = "ICAN云影",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            CategoryChips(
                categories = viewModel.categories,
                selectedId = selectedCategory,
                onSelect = viewModel::selectCategory,
            )
        }

        sections.firstOrNull()?.second?.firstOrNull()?.let { featured ->
            item {
                FeaturedBanner(video = featured, onClick = { onVideoClick(featured) })
            }
        }

        items(sections, key = { it.first.id }) { (category, videos) ->
            VideoSection(
                category = category,
                videos = videos,
                onVideoClick = onVideoClick,
            )
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<VideoCategory>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CategoryChip(text = "全部", selected = selectedId == null, onClick = { onSelect(null) })
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                text = category.name,
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pill = RoundedCornerShape(percent = 50)
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "chipContainer",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .tvCardEffect(
                onClick = onClick,
                shape = pill,
                focusedScale = 1.06f,
                glow = false,
            )
            .background(container, pill)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun FeaturedBanner(
    video: Video,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .tvCardEffect(onClick = onClick, shape = shape),
    ) {
        AsyncImage(
            model = video.cover,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xE6070710), Color(0x33070710), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(20.dp)
                .fillMaxWidth(0.62f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今日推荐",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9FC0FF),
            )
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD6D6E4),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "立即播放",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoSection(
    category: VideoCategory,
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 2.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(videos, key = { it.id }) { video ->
                VideoCardItem(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

@Composable
private fun VideoCardItem(video: Video, onClick: () -> Unit) {
    com.ican.tvplay.ui.components.VideoCard(video = video, onClick = onClick)
}
