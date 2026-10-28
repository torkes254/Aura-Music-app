package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.data.cover.CoverArtResolver
import com.example.model.Song
import java.io.File

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set
    lateinit var queueManager: ShuffleQueueManager
        private set

    companion object {
        const val CHANNEL_ID = "aura_music_playback_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var currentService: PlaybackService? = null
            private set
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        currentService = this
        queueManager = ShuffleQueueManager.getInstance(this)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                queueManager.setIsPlaying(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val nextSong = queueManager.nextTrack(isManualSkip = false)
                    if (nextSong != null) {
                        com.example.data.local.PlayCountManager.getInstance(this@PlaybackService).incrementPlayCount(nextSong.id)
                        updatePlayerQueue(startPlay = true, positionMs = 0L)
                    } else {
                        queueManager.setIsPlaying(false)
                    }
                }
            }
        })

        // Preload full ordered queue into player so Bluetooth/Car see the queue immediately
        val fullQueue = queueManager.getFullOrderedQueue()
        val currentSong = queueManager.getCurrentSong()
        if (fullQueue.isNotEmpty() && currentSong != null) {
            val mediaItems = MediaItemHelper.toMediaItems(this, fullQueue)
            val currentIndex = queueManager.getCurrentQueueIndex().coerceIn(0, mediaItems.size - 1)
            val pos = queueManager.getCurrentPositionMs()
            val artPath = CoverArtResolver.getInstance(this).getArtwork(currentSong)
            queueManager.updateArtwork(artPath)
            player.setMediaItems(mediaItems, currentIndex, pos)
            player.prepare()
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows now playing notifications and playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun updatePlayerQueue(startPlay: Boolean = true, positionMs: Long = 0L) {
        val fullQueue = queueManager.getFullOrderedQueue()
        val currentSong = queueManager.getCurrentSong() ?: fullQueue.firstOrNull()
        if (fullQueue.isEmpty() || currentSong == null) {
            player.clearMediaItems()
            return
        }

        val mediaItems = MediaItemHelper.toMediaItems(this, fullQueue)
        val currentIndex = queueManager.getCurrentQueueIndex().coerceIn(0, mediaItems.size - 1)
        val artPath = CoverArtResolver.getInstance(this).getArtwork(currentSong)
        queueManager.updateArtwork(artPath)

        player.setMediaItems(mediaItems, currentIndex, positionMs)
        player.prepare()
        if (startPlay) {
            player.play()
        }
    }

    fun playTrack(song: Song) {
        val artPath = CoverArtResolver.getInstance(this).getArtwork(song)
        queueManager.updateArtwork(artPath)
        updatePlayerQueue(startPlay = true, positionMs = 0L)
    }

    fun skipToNext() {
        val nextSong = queueManager.nextTrack(isManualSkip = true)
        if (nextSong != null) {
            com.example.data.local.PlayCountManager.getInstance(this).incrementPlayCount(nextSong.id)
            updatePlayerQueue(startPlay = true, positionMs = 0L)
        } else {
            player.pause()
        }
    }

    fun skipToPrevious() {
        val currentPos = player.currentPosition
        if (currentPos > 3000L) {
            player.seekTo(0L)
            queueManager.updatePosition(0L)
            return
        }
        val prevSong = queueManager.previousTrack(currentPos)
        if (prevSong != null) {
            updatePlayerQueue(startPlay = true, positionMs = 0L)
        } else {
            player.seekTo(0L)
            queueManager.updatePosition(0L)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        if (currentService == this) {
            currentService = null
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {

        override fun onMediaButtonEvent(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                        skipToNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                        skipToPrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        if (player.isPlaying) player.pause() else player.play()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(mediaSession, controller, intent)
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    skipToNext()
                    return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    skipToPrevious()
                    return androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaSession.MediaItemsWithStartPosition> {
            val fullQueue = queueManager.getFullOrderedQueue()
            val currentSong = queueManager.getCurrentSong()
            if (fullQueue.isNotEmpty() && currentSong != null) {
                val mediaItems = MediaItemHelper.toMediaItems(this@PlaybackService, fullQueue)
                val currentIndex = queueManager.getCurrentQueueIndex().coerceIn(0, mediaItems.size - 1)
                val pos = queueManager.getCurrentPositionMs()
                val artPath = CoverArtResolver.getInstance(this@PlaybackService).getArtwork(currentSong)
                queueManager.updateArtwork(artPath)
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        currentIndex,
                        pos
                    )
                )
            }
            return super.onPlaybackResumption(mediaSession, controller)
        }
    }
}
