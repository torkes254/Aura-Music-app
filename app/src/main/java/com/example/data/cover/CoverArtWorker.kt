package com.example.data.cover

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.MusicRepository

class CoverArtWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "CoverArtWorker"
        private const val TAG = "CoverArtWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting background cover art worker")
        val repository = MusicRepository(applicationContext)
        val resolver = CoverArtResolver(applicationContext)

        val songs = repository.queryAllSongs()

        for (song in songs) {
            if (isStopped) {
                Log.i(TAG, "Cover art worker stopped")
                return Result.retry()
            }

            try {
                resolver.resolveCoverForSong(song)
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving art for song ${song.title}", e)
            }
        }

        Log.i(TAG, "Finished background cover art resolution")
        return Result.success()
    }
}
