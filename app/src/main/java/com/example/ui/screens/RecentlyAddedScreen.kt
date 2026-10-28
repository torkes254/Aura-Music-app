package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.cover.CoverArtResolver
import com.example.data.local.RecentlyAddedTimeframe
import com.example.model.RecentlyAddedGrouper
import com.example.model.Song
import com.example.ui.components.SongItemRow
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RecentlyAddedScreen(
    songs: List<Song>,
    timeframe: RecentlyAddedTimeframe,
    currentSongId: Long?,
    isPlaying: Boolean,
    isScanning: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onRescanClick: () -> Unit,
    onGenerateSampleTracksClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectTimeframe: ((RecentlyAddedTimeframe) -> Unit)? = null,
    coverArtMap: Map<String, String> = emptyMap(),
    onFixCoverClick: ((Song) -> Unit)? = null,
    songVideos: Map<Long, String> = emptyMap(),
    onSetBackgroundVideoClick: ((Song) -> Unit)? = null,
    onRemoveBackgroundVideoClick: ((Song) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<Long> = emptySet(),
    onToggleSelect: ((Song) -> Unit)? = null,
    onStartSelection: ((Song) -> Unit)? = null,
    onDeleteSongClick: ((Song) -> Unit)? = null
) {
    if (isScanning) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scanning device library...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        return
    }

    // Filter by selected timeframe (7 / 30 / 90 days / All) and sort newest first
    val filteredRecentSongs = remember(songs, timeframe) {
        RecentlyAddedGrouper.filterByTimeframe(songs, timeframe)
    }

    // Group songs into sections: Today, Yesterday, This week, This month, Earlier
    val groupedSections = remember(filteredRecentSongs) {
        RecentlyAddedGrouper.groupSongs(filteredRecentSongs)
    }

    var showTimeframeMenu by remember { mutableStateOf(false) }

    if (filteredRecentSongs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("recently_added_empty_state"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = timeframe.emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("recently_added_empty_text")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (timeframe != RecentlyAddedTimeframe.ALL) {
                        "Try expanding your time window in Settings or switch to All to see older songs."
                    } else {
                        "Add audio files to your device or generate sample tracks to test the library."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (timeframe != RecentlyAddedTimeframe.ALL && onSelectTimeframe != null) {
                    FilledTonalButton(
                        onClick = { onSelectTimeframe(RecentlyAddedTimeframe.ALL) },
                        modifier = Modifier.testTag("empty_show_all_button")
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show All Added Songs")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                FilledTonalButton(
                    onClick = onGenerateSampleTracksClick,
                    modifier = Modifier.testTag("recently_added_generate_sample_button")
                ) {
                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate Sample Tracks")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRescanClick,
                    modifier = Modifier.testTag("recently_added_rescan_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan Storage")
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("recently_added_screen")
    ) {
        // Quick Action Bar: Play All & Shuffle All + Filter Horizon Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPlayAllClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("play_all_recently_added_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All")
            }

            FilledTonalButton(
                onClick = onShuffleAllClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shuffle_all_recently_added_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shuffle")
            }

            // Quick timeframe selector dropdown
            if (onSelectTimeframe != null) {
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showTimeframeMenu = true }
                            .testTag("timeframe_selector_chip"),
                        color = DarkSurfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeframe.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change time frame",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showTimeframeMenu,
                        onDismissRequest = { showTimeframeMenu = false }
                    ) {
                        RecentlyAddedTimeframe.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (option == timeframe) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == timeframe) MaterialTheme.colorScheme.primary else TextPrimary
                                    )
                                },
                                onClick = {
                                    showTimeframeMenu = false
                                    onSelectTimeframe(option)
                                },
                                modifier = Modifier.testTag("timeframe_menu_item_${option.days}")
                            )
                        }
                    }
                }
            }
        }

        val context = LocalContext.current
        val resolver = remember { CoverArtResolver.getInstance(context) }

        // Sectioned Song List: Today, Yesterday, This week, This month, Earlier
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groupedSections.forEach { (sectionTitle, sectionSongs) ->
                // Section Header
                item(key = "header_$sectionTitle") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 6.dp)
                            .testTag("section_header_$sectionTitle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkSurfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${sectionSongs.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Section Items
                items(sectionSongs, key = { it.id }) { song ->
                    val isCurrent = song.id == currentSongId
                    val artworkPath = remember(song.id, song.album, song.artist, coverArtMap) {
                        resolver.getArtwork(song)
                    }
                    val hasCustomVideo = songVideos.containsKey(song.id)

                    SongItemRow(
                        song = song,
                        isPlaying = isPlaying,
                        isCurrentSong = isCurrent,
                        artworkPath = artworkPath,
                        onClick = { onSongClick(song) },
                        onAddToPlaylistClick = { onAddToPlaylistClick(song) },
                        onFixCoverClick = { onFixCoverClick?.invoke(song) },
                        hasCustomVideo = hasCustomVideo,
                        onSetBackgroundVideoClick = { onSetBackgroundVideoClick?.invoke(song) },
                        onRemoveBackgroundVideoClick = { onRemoveBackgroundVideoClick?.invoke(song) },
                        isSelectionMode = isSelectionMode,
                        isSelected = song.id in selectedSongIds,
                        onToggleSelect = { onToggleSelect?.invoke(song) },
                        onStartSelection = { onStartSelection?.invoke(song) },
                        onDeleteClick = { onDeleteSongClick?.invoke(song) }
                    )
                }
            }
        }
    }
}
