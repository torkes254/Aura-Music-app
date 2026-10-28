package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.cover.CoverArtResolver
import com.example.model.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val queueManager = ShuffleQueueManager.getInstance(context)
    val playerState: StateFlow<PlayerState> = queueManager.queueState

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionJob: Job? = null

    init {
        initializeController()
        startPositionUpdates()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        queueManager.setIsPlaying(isPlaying)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            seekToNext()
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition
                    val dur = controller.duration
                    queueManager.updatePosition(pos, dur)
                }
                delay(400)
            }
        }
    }

    private fun getMediaItemForSong(song: Song): androidx.media3.common.MediaItem {
        val artPath = CoverArtResolver.getInstance(context).getArtwork(song)
        queueManager.updateArtwork(artPath)
        val artUri = artPath?.let { Uri.fromFile(File(it)) }
        return MediaItemHelper.toMediaItem(song, artUri)
    }

    fun playSong(song: Song, contextSongs: List<Song> = emptyList()) {
        com.example.data.local.PlayCountManager.getInstance(context).incrementPlayCount(song.id)
        queueManager.playSong(song, contextSongs)
        val service = PlaybackService.currentService
        if (service != null) {
            service.updatePlayerQueue(startPlay = true, positionMs = 0L)
        } else {
            val controller = mediaController
            if (controller != null) {
                val fullQueue = queueManager.getFullOrderedQueue()
                val mediaItems = MediaItemHelper.toMediaItems(context, fullQueue)
                val currentIndex = queueManager.getCurrentQueueIndex().coerceIn(0, mediaItems.size - 1)
                controller.setMediaItems(mediaItems, currentIndex, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun playAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSong(songs.first(), songs)
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        queueManager.startShuffleAll(songs)
        val song = queueManager.getCurrentSong() ?: return
        com.example.data.local.PlayCountManager.getInstance(context).incrementPlayCount(song.id)
        val service = PlaybackService.currentService
        if (service != null) {
            service.updatePlayerQueue(startPlay = true, positionMs = 0L)
        } else {
            val controller = mediaController
            if (controller != null) {
                val fullQueue = queueManager.getFullOrderedQueue()
                val mediaItems = MediaItemHelper.toMediaItems(context, fullQueue)
                val currentIndex = queueManager.getCurrentQueueIndex().coerceIn(0, mediaItems.size - 1)
                controller.setMediaItems(mediaItems, currentIndex, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun togglePlayPause() {
        val service = PlaybackService.currentService
        if (service != null) {
            if (service.player.isPlaying) {
                service.player.pause()
            } else {
                if (service.player.playbackState == Player.STATE_IDLE || service.player.playbackState == Player.STATE_ENDED) {
                    service.updatePlayerQueue(startPlay = true, positionMs = queueManager.getCurrentPositionMs())
                } else {
                    service.player.play()
                }
            }
        } else {
            val controller = mediaController
            if (controller != null) {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            } else {
                queueManager.setIsPlaying(!playerState.value.isPlaying)
            }
        }
    }

    fun seekToNext() {
        val service = PlaybackService.currentService
        if (service != null) {
            service.skipToNext()
        } else {
            mediaController?.seekToNext()
        }
    }

    fun seekToPrevious() {
        val service = PlaybackService.currentService
        if (service != null) {
            service.skipToPrevious()
        } else {
            mediaController?.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        val service = PlaybackService.currentService
        if (service != null) {
            service.player.seekTo(positionMs)
        } else {
            mediaController?.seekTo(positionMs)
        }
        val duration = mediaController?.duration ?: playerState.value.durationMs
        queueManager.updatePosition(positionMs, duration)
    }

    fun toggleShuffle(): Boolean {
        val result = queueManager.toggleShuffle()
        PlaybackService.currentService?.updatePlayerQueue(
            startPlay = playerState.value.isPlaying,
            positionMs = queueManager.getCurrentPositionMs()
        )
        return result
    }

    fun toggleRepeatMode(): Int = queueManager.toggleRepeat()

    fun updateLibrary(allSongs: List<Song>, isCurrentTrackPlaying: Boolean, allKnownSongs: List<Song>) {
        queueManager.updateLibrary(allSongs, isCurrentTrackPlaying, allKnownSongs)
        val service = PlaybackService.currentService
        if (service != null && service.player.playbackState == Player.STATE_IDLE) {
            service.updatePlayerQueue(startPlay = false, positionMs = queueManager.getCurrentPositionMs())
        }
    }

    fun onArtworkChanged(album: String, artist: String, newPath: String?) {
        val currentSong = queueManager.getCurrentSong()
        if (currentSong != null) {
            val currentAlbumKey = CoverArtResolver.getAlbumKey(currentSong.album, currentSong.artist)
            val targetAlbumKey = CoverArtResolver.getAlbumKey(album, artist)
            if (currentAlbumKey == targetAlbumKey || newPath != null) {
                val service = PlaybackService.currentService
                if (service != null) {
                    val isPlaying = service.player.isPlaying
                    val currentPos = service.player.currentPosition
                    service.updatePlayerQueue(startPlay = isPlaying, positionMs = currentPos)
                }
            }
        }
    }

    fun onSongsDeleted(deletedSongIds: Set<Long>) {
        val currentSong = queueManager.getCurrentSong()
        val isCurrentDeleted = currentSong != null && currentSong.id in deletedSongIds
        queueManager.removeSongs(deletedSongIds)

        val service = PlaybackService.currentService
        if (isCurrentDeleted) {
            val nextSong = queueManager.getCurrentSong()
            if (nextSong != null) {
                if (service != null) {
                    service.updatePlayerQueue(startPlay = playerState.value.isPlaying, positionMs = 0L)
                } else {
                    mediaController?.let { controller ->
                        val fullQueue = queueManager.getFullOrderedQueue()
                        val mediaItems = MediaItemHelper.toMediaItems(context, fullQueue)
                        val currentIndex = queueManager.getCurrentQueueIndex().coerceIn(0, mediaItems.size - 1)
                        controller.setMediaItems(mediaItems, currentIndex, 0L)
                        controller.prepare()
                        if (playerState.value.isPlaying) controller.play()
                    }
                }
            } else {
                if (service != null) {
                    service.player.stop()
                    service.player.clearMediaItems()
                } else {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                }
                queueManager.setIsPlaying(false)
            }
        } else {
            service?.updatePlayerQueue(startPlay = playerState.value.isPlaying, positionMs = service.player.currentPosition)
        }
    }

    fun release() {
        positionJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
