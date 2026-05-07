package org.messenger.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.AttachmentDto
import org.messenger.app.shared.domain.repository.AttachmentsRepository
import org.messenger.app.shared.util.ImageCache

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

enum class ImageVariant { THUMB, FULL }

@Composable
fun CachedAttachmentImage(
    attachment: AttachmentDto,
    attachmentsRepository: AttachmentsRepository,
    variant: ImageVariant = ImageVariant.THUMB,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = { DefaultPlaceholder() },
) {
    val cacheKey = "${attachment.id}_${variant.name.lowercase()}"
    var bitmap by remember(cacheKey) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(cacheKey) { mutableStateOf(true) }

    LaunchedEffect(cacheKey) {
        loading = true
        try {
            val cached = ImageCache.get(cacheKey)
            if (cached != null) {
                bitmap = decodeImageBitmap(cached)
                loading = false
                return@LaunchedEffect
            }

            val bytes = attachmentsRepository.loadImageBytes(
                attachmentId = attachment.id,
                thumbnailUrl = attachment.thumbnailUrl,
                downloadUrl = attachment.downloadUrl,
                thumb = (variant == ImageVariant.THUMB),
            )

            if (bytes != null) {
                ImageCache.put(cacheKey, bytes)
                bitmap = decodeImageBitmap(bytes)
            }
        } finally {
            loading = false
        }
    }

    Box(modifier) {
        val bm = bitmap
        when {
            bm != null -> Image(
                painter = BitmapPainter(bm),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
            loading -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            else -> placeholder()
        }
    }
}

@Composable
private fun DefaultPlaceholder() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
}