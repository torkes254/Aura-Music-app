package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.cover.CoverArtResolver
import com.example.model.Artist
import com.example.model.Song
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.SongItemRow
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    songs: List<Song>,
    currentSongId: Long?,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
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
    onDeleteSongClick: ((Song) -> Unit)? = null,
    onSelectAllClick: (() -> Unit)? = null,
    onDeleteSelectedClick: (() -> Unit)? = null,
    onCancelSelectionClick: (() -> Unit)? = null,
    onSetVideoClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolver = remember { CoverArtResolver.getInstance(context) }
    val artistSongs = songs.filter { it.artist.equals(artist.name, ignoreCase = true) }
    val artistArtPath = remember(artist.name, artistSongs, coverArtMap) {
        artistSongs.firstNotNullOfOrNull { s ->
            resolver.getArtwork(s) ?: coverArtMap[CoverArtResolver.getAlbumKey(s.album, s.artist)]
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("artist_detail_screen")
    ) {
        // Top navigation bar / Selection Top Bar
        if (isSelectionMode) {
            SelectionTopBar(
                selectedCount = selectedSongIds.size,
                onSelectAllClick = { onSelectAllClick?.invoke() },
                onDeleteClick = { onDeleteSelectedClick?.invoke() },
                onCancelClick = { onCancelSelectionClick?.invoke() },
                onSetVideoClick = onSetVideoClick
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("artist_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Artist Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (artistArtPath != null && File(artistArtPath).exists()) {
                    AsyncImage(
                        model = artistArtPath,
                        contentDescription = "Artist Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${artistSongs.size} tracks",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlayAllClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("artist_play_all_button")
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All")
            }

            FilledTonalButton(
                onClick = onShuffleClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag("artist_shuffle_button")
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shuffle")
            }
        }

        // Track list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(artistSongs, key = { it.id }) { song ->
                val artworkPath = remember(song.id, song.album, song.artist, coverArtMap) {
                    resolver.getArtwork(song)
                }
                val hasCustomVideo = songVideos.containsKey(song.id)
                SongItemRow(
                    song = song,
                    isPlaying = isPlaying,
                    isCurrentSong = song.id == currentSongId,
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
