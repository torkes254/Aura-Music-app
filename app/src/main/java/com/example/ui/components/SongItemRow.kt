package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Song
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItemRow(
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    onRemoveFromPlaylistClick: (() -> Unit)? = null,
    onFixCoverClick: (() -> Unit)? = null,
    hasCustomVideo: Boolean = false,
    onSetBackgroundVideoClick: (() -> Unit)? = null,
    onRemoveBackgroundVideoClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onStartSelection: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    isPlaylistView: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect?.invoke()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (isSelectionMode) {
                        onToggleSelect?.invoke()
                    } else {
                        onStartSelection?.invoke()
                    }
                }
            )
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    isCurrentSong -> DarkSurfaceContainerHigh
                    else -> DarkSurfaceContainer
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("song_row_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect?.invoke() },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("checkbox_${song.id}")
            )
        }
        // Leading thumbnail / icon or active playing indicator
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isCurrentSong) MaterialTheme.colorScheme.primaryContainer else DarkSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (artworkPath != null && File(artworkPath).exists()) {
                AsyncImage(
                    model = artworkPath,
                    contentDescription = "Cover Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Music",
                    tint = if (isCurrentSong) MaterialTheme.colorScheme.primary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isCurrentSong && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingEqualizerBars()
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentSong) MaterialTheme.colorScheme.primary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration text
        Text(
            text = song.formattedDuration,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        // Options dropdown menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("song_menu_${song.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (!isPlaylistView && onRemoveFromPlaylistClick == null) {
                    DropdownMenuItem(
                        text = { Text("Select") },
                        onClick = {
                            menuExpanded = false
                            onStartSelection?.invoke()
                        },
                        modifier = Modifier.testTag("select_song_item_${song.id}")
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylistClick()
                    },
                    modifier = Modifier.testTag("add_to_playlist_item")
                )
                if (isPlaylistView || onRemoveFromPlaylistClick != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromPlaylistClick?.invoke()
                        },
                        modifier = Modifier.testTag("remove_from_playlist_item")
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick?.invoke()
                        },
                        modifier = Modifier.testTag("delete_song_item_${song.id}")
                    )
                }
                DropdownMenuItem(
                    text = { Text("Fix cover") },
                    onClick = {
                        menuExpanded = false
                        onFixCoverClick?.invoke()
                    },
                    modifier = Modifier.testTag("fix_cover_item_${song.id}")
                )
                DropdownMenuItem(
                    text = { Text(if (hasCustomVideo) "Change background video" else "Set background video") },
                    onClick = {
                        menuExpanded = false
                        onSetBackgroundVideoClick?.invoke()
                    },
                    modifier = Modifier.testTag("set_video_item_${song.id}")
                )
                if (hasCustomVideo && onRemoveBackgroundVideoClick != null) {
                    DropdownMenuItem(
                        text = { Text("Remove background video") },
                        onClick = {
                            menuExpanded = false
                            onRemoveBackgroundVideoClick.invoke()
                        },
                        modifier = Modifier.testTag("remove_video_item_${song.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun PlayingEqualizerBars() {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(380),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = Modifier.size(18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar1)
                .background(AccentVioletLight, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar2)
                .background(AccentVioletLight, RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar3)
                .background(AccentVioletLight, RoundedCornerShape(1.dp))
        )
    }
}

private val AccentVioletLight = Color(0xFFA78BFA)
