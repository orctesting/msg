package org.messenger.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale

@Composable
fun LocalBytesImage(
    bytes: ByteArray,
    cacheKey: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(cacheKey) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(cacheKey) {
        bitmap = decodeImageBitmap(bytes)
    }
    Box(modifier) {
        val bm = bitmap
        if (bm != null) {
            Image(
                painter = BitmapPainter(bm),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}