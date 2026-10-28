package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.cover.CoverArtResolver
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceContainerHigh
import java.io.File

@Composable
fun PlaylistArtwork(
    playlist: Playlist,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    coverArtMap: Map<String, String> = emptyMap(),
    contentDescription: String? = "Playlist Cover"
) {
    val context = LocalContext.current
    val resolver = remember { CoverArtResolver.getInstance(context) }

    val customCoverFile = remember(playlist.customCoverPath) {
        playlist.customCoverPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    }

    Box(
        modifier = modifier
            .background(DarkSurfaceContainerHigh)
            .testTag("playlist_artwork_${playlist.id}"),
        contentAlignment = Alignment.Center
    ) {
        if (customCoverFile != null) {
            // Priority 1: User-picked custom square cover
            AsyncImage(
                model = customCoverFile.absolutePath,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (songs.isEmpty()) {
            // Default placeholder if empty
            Icon(
                imageVector = if (playlist.isSmart) Icons.Default.Bolt else Icons.Default.PlaylistPlay,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        } else if (songs.size == 1) {
            // Single song: show that song's artwork
            val songArt = remember(songs[0].id, coverArtMap) { resolver.getArtwork(songs[0]) }
            if (songArt != null && File(songArt).exists()) {
                AsyncImage(
                    model = songArt,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // 2x2 collage of the first 4 songs' artwork
            val firstFour = songs.take(4)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CollageQuadrant(
                        song = firstFour.getOrNull(0),
                        resolver = resolver,
                        coverArtMap = coverArtMap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Box(modifier = Modifier.size(0.5.dp).fillMaxHeight().background(DarkSurfaceBorder))
                    CollageQuadrant(
                        song = firstFour.getOrNull(1),
                        resolver = resolver,
                        coverArtMap = coverArtMap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().size(0.5.dp).background(DarkSurfaceBorder))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CollageQuadrant(
                        song = firstFour.getOrNull(2),
                        resolver = resolver,
                        coverArtMap = coverArtMap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Box(modifier = Modifier.size(0.5.dp).fillMaxHeight().background(DarkSurfaceBorder))
                    CollageQuadrant(
                        song = firstFour.getOrNull(3),
                        resolver = resolver,
                        coverArtMap = coverArtMap,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CollageQuadrant(
    song: Song?,
    resolver: CoverArtResolver,
    coverArtMap: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(DarkSurfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (song != null) {
            val art = remember(song.id, coverArtMap) { resolver.getArtwork(song) }
            if (art != null && File(art).exists()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
