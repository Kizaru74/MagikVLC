package com.magikvlc.app.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.magikvlc.app.data.StorageKind
import com.magikvlc.app.data.ThumbnailCache
import com.magikvlc.app.data.VideoItem

@Composable
fun VideoThumb(
    video: VideoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showStorageBadge: Boolean = true,
    placeholder: @Composable BoxScope.() -> Unit = { VideoThumbPlaceholder() },
) {
    val context = LocalContext.current
    val cacheKey = remember(video.uri, video.id) { video.uri.toString() to video.id }
    val bitmap by produceState<Bitmap?>(null, cacheKey) {
        value = ThumbnailCache.get(context, video)
    }

    Box(modifier = modifier) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            placeholder()
        }
        if (showStorageBadge) {
            StorageBadge(
                storage = video.storage,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}

@Composable
fun VideoThumbPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
fun StorageBadge(storage: StorageKind, modifier: Modifier = Modifier) {
    if (storage == StorageKind.UNKNOWN) return
    Surface(
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (storage == StorageKind.INTERNAL) Icons.Filled.Smartphone else Icons.Filled.SdStorage,
            contentDescription = if (storage == StorageKind.INTERNAL) "Memoria interna" else "Tarjeta SD",
            tint = Color.White,
            modifier = Modifier.padding(3.dp).size(14.dp),
        )
    }
}
