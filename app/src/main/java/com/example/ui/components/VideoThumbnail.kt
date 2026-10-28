package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkSurfaceContainerHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbnailLoader {
    private val memoryCache = LruCache<String, Bitmap>(30)

    suspend fun getThumbnail(videoPath: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (videoPath.isNullOrBlank()) return@withContext null
        val cached = memoryCache.get(videoPath)
        if (cached != null) return@withContext cached

        val file = File(videoPath)
        if (!file.exists() || file.length() == 0L) return@withContext null

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val bitmap = retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()
            if (bitmap != null) {
                memoryCache.put(videoPath, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun VideoThumbnail(
    videoPath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Video Thumbnail"
) {
    var bitmap by remember(videoPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoPath) {
        bitmap = VideoThumbnailLoader.getThumbnail(videoPath)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
