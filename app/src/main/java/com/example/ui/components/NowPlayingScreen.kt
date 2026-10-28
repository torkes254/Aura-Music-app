package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.cover.CoverArtResolver
import com.example.model.Song
import com.example.playback.PlayerState
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.platform.LocalContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerState: PlayerState,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSelectQueueItem: (Song) -> Unit,
    coverArtMap: Map<String, String>,
    videoPath: String? = null,
    showLargeAlbumArtSetting: Boolean = false,
    videoDimming: Float = 0.20f,
    modifier: Modifier = Modifier,
    onSetBackgroundVideoClick: ((Song) -> Unit)? = null,
    onRemoveBackgroundVideoClick: ((Song) -> Unit)? = null
) {
    val song = playerState.currentSong ?: return
    val context = LocalContext.current

    val artPath = remember(song.id, song.album, song.artist, playerState.artworkPath, coverArtMap) {
        playerState.artworkPath ?: CoverArtResolver.getInstance(context).getArtwork(song)
    }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    val currentPosition = if (isUserSeeking) {
        (seekProgress * playerState.durationMs).toLong()
    } else {
        playerState.currentPositionMs
    }

    val sliderValue = if (playerState.durationMs > 0) {
        (currentPosition.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val hasVideo = videoPath != null && File(videoPath).exists()
    var isLargeArtActive by remember(hasVideo, showLargeAlbumArtSetting) {
        mutableStateOf(if (hasVideo) showLargeAlbumArtSetting else true)
    }
    var showQueueSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("now_playing_screen")
    ) {
        // Custom background video (muted, looped, scaled to fill)
        if (hasVideo) {
            BackgroundVideoPlayer(
                videoPath = videoPath!!,
                isMusicPlaying = playerState.isPlaying,
                modifier = Modifier.fillMaxSize()
            )

            // Light dimming across full screen (default 20%, configurable 0-70%)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = videoDimming))
            )

            // Gradient only behind bottom controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.70f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = song.folder,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("open_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    if (onSetBackgroundVideoClick != null) {
                        var showNowPlayingMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showNowPlayingMenu = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("now_playing_menu_button")
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            androidx.compose.material3.DropdownMenu(
                                expanded = showNowPlayingMenu,
                                onDismissRequest = { showNowPlayingMenu = false },
                                modifier = Modifier.background(DarkSurfaceContainerHigh)
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Set background video") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.VideoLibrary,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showNowPlayingMenu = false
                                        onSetBackgroundVideoClick.invoke(song)
                                    },
                                    modifier = Modifier.testTag("now_playing_menu_set_video")
                                )

                                if (videoPath != null && onRemoveBackgroundVideoClick != null) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Remove background video") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showNowPlayingMenu = false
                                            onRemoveBackgroundVideoClick.invoke(song)
                                        },
                                        modifier = Modifier.testTag("now_playing_menu_remove_video")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLargeArtActive) {
                Spacer(modifier = Modifier.weight(0.5f))

                // Large Album Artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurfaceContainerHigh)
                        .align(Alignment.CenterHorizontally)
                        .clickable(enabled = hasVideo) {
                            isLargeArtActive = false
                        }
                        .testTag("large_album_art_card"),
                    contentAlignment = Alignment.Center
                ) {
                    if (artPath != null && File(artPath).exists()) {
                        AsyncImage(
                            model = artPath,
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.5f))

                // Track title & Artist (standard layout below card)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${song.artist} — ${song.album}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // Open Video View: full-bleed visible video with tap area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { isLargeArtActive = true }
                        .testTag("video_middle_tap_area")
                )

                // Track title & Artist with small art thumbnail next to title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceContainerHigh)
                            .clickable { isLargeArtActive = true }
                            .testTag("small_album_art_thumbnail"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artPath != null && File(artPath).exists()) {
                            AsyncImage(
                                model = artPath,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${song.artist} — ${song.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scrubber Slider
            Slider(
                value = sliderValue,
                onValueChange = { newVal ->
                    isUserSeeking = true
                    seekProgress = newVal
                },
                onValueChangeFinished = {
                    val targetMs = (seekProgress * playerState.durationMs).toLong()
                    onSeekTo(targetMs)
                    isUserSeeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = DarkSurfaceBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_slider")
            )

            // Duration labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = formatMs(playerState.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toggle_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playerState.isShuffleEnabled) MaterialTheme.colorScheme.primary else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous button
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("previous_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause FAB
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPauseClick)
                        .testTag("now_playing_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("next_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat button
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toggle_repeat_button")
                ) {
                    val icon = if (playerState.repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val tint = if (playerState.repeatMode > 0) MaterialTheme.colorScheme.primary else TextSecondary
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Up Next Queue BottomSheet
        if (showQueueSheet) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = sheetState,
                containerColor = DarkSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (playerState.upNextSongs.isEmpty()) {
                        Text(
                            text = "No upcoming tracks in queue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playerState.upNextSongs, key = { it.id }) { qSong ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceContainer)
                                        .clickable {
                                            onSelectQueueItem(qSong)
                                            showQueueSheet = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = qSong.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = qSong.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = qSong.formattedDuration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
