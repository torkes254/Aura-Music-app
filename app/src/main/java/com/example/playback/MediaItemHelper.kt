package com.example.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.model.Song

object MediaItemHelper {

    private const val KEY_SONG_ID = "song_id"
    private const val KEY_ALBUM_ID = "album_id"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_FOLDER = "folder"

    fun toMediaItem(song: Song, artworkUri: android.net.Uri? = null): MediaItem {
        val extras = Bundle().apply {
            putLong(KEY_SONG_ID, song.id)
            putLong(KEY_ALBUM_ID, song.albumId)
            putLong(KEY_DURATION_MS, song.durationMs)
            putString(KEY_FOLDER, song.folder)
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setExtras(extras)

        if (artworkUri != null) {
            metadataBuilder.setArtworkUri(artworkUri)
        }

        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.contentUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun toMediaItems(context: android.content.Context, songs: List<Song>): List<MediaItem> {
        val resolver = com.example.data.cover.CoverArtResolver.getInstance(context)
        return songs.map { song ->
            val artPath = resolver.getArtwork(song)
            val artUri = artPath?.let { android.net.Uri.fromFile(java.io.File(it)) }
            toMediaItem(song, artUri)
        }
    }

    fun fromMediaItem(mediaItem: MediaItem): Song? {
        val id = mediaItem.mediaId.toLongOrNull() ?: return null
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString() ?: "Unknown"
        val artist = metadata.artist?.toString() ?: "Unknown"
        val album = metadata.albumTitle?.toString() ?: "Unknown"
        val uri = mediaItem.requestMetadata.mediaUri ?: mediaItem.localConfiguration?.uri ?: return null
        val extras = metadata.extras
        val albumId = extras?.getLong(KEY_ALBUM_ID, 0L) ?: 0L
        val durationMs = extras?.getLong(KEY_DURATION_MS, 0L) ?: 0L
        val folder = extras?.getString(KEY_FOLDER, "Music/") ?: "Music/"

        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = durationMs,
            contentUri = uri,
            folder = folder
        )
    }
}
