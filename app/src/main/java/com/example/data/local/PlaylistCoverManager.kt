package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class PlaylistCoverManager private constructor(private val context: Context) {

    private val coversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }

    suspend fun saveCustomCover(playlistId: Long, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                // Decode options to avoid OutOfMemory on massive camera images
                val original = BitmapFactory.decodeStream(input) ?: return@withContext null
                val squareBitmap = cropToSquare(original)

                val destFile = File(coversDir, "playlist_${playlistId}.jpg")
                FileOutputStream(destFile).use { out ->
                    squareBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                if (squareBitmap != original) {
                    squareBitmap.recycle()
                }
                original.recycle()

                destFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving custom playlist cover", e)
            null
        }
    }

    fun removeCustomCover(playlistId: Long): Boolean {
        return try {
            val file = File(coversDir, "playlist_${playlistId}.jpg")
            if (file.exists()) {
                file.delete()
            } else true
        } catch (e: Exception) {
            Log.e(TAG, "Failed removing custom playlist cover", e)
            false
        }
    }

    fun getCoverPath(playlistId: Long): String? {
        val file = File(coversDir, "playlist_${playlistId}.jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val edge = min(width, height)
        val xOffset = (width - edge) / 2
        val yOffset = (height - edge) / 2

        val cropped = Bitmap.createBitmap(bitmap, xOffset, yOffset, edge, edge)
        val targetSize = min(800, edge)
        return if (edge != targetSize) {
            Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true).also {
                if (it != cropped) cropped.recycle()
            }
        } else {
            cropped
        }
    }

    companion object {
        private const val TAG = "PlaylistCoverManager"

        @Volatile
        private var instance: PlaylistCoverManager? = null

        fun getInstance(context: Context): PlaylistCoverManager {
            return instance ?: synchronized(this) {
                instance ?: PlaylistCoverManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
