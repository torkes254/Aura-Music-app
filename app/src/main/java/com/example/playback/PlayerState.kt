package com.example.playback

import com.example.model.Song

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL
    val queue: List<Song> = emptyList(),
    val upNextSongs: List<Song> = emptyList(),
    val artworkPath: String? = null
)
