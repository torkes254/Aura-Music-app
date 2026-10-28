package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.cover.CoverArtResolver
import com.example.model.Song
import com.example.ui.components.SongItemRow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SongsScreen(
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    isScanning: Boolean,
    isShuffleEnabled: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onRescanClick: () -> Unit,
    onGenerateSampleTracksClick: () -> Unit,
    modifier: Modifier = Modifier,
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

    if (songs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No songs found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add audio files to your device, enable music folders in Settings, or generate sample audio tracks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = onGenerateSampleTracksClick,
                    modifier = Modifier.testTag("empty_generate_sample_button")
                ) {
                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate Sample Tracks")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRescanClick,
                    modifier = Modifier.testTag("empty_rescan_button")
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
            .testTag("songs_screen")
    ) {
        // Quick Action Bar: Play All & Shuffle All
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlayAllClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("play_all_songs_button")
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All")
            }

            FilledTonalButton(
                onClick = onShuffleAllClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("shuffle_all_songs_button")
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shuffle")
            }
        }

        val context = LocalContext.current
        val resolver = remember { CoverArtResolver.getInstance(context) }

        // Song List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(songs, key = { it.id }) { song ->
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
