package com.example.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val folder: String = "Music/",
    val dateAdded: Long = 0L,
    val dataPath: String? = null,
    val genre: String? = null,
    val playCount: Int = 0
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
