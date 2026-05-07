package org.messenger.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.AvatarDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.profile.AvatarsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarsScreen(
    appModule: AppModule,
    onBack: () -> Unit,
) {
    val viewModel = remember {
        AvatarsViewModel(
            profileRepository = appModule.profileRepository,
            attachmentsRepository = appModule.attachmentsRepository,
        )
    }
    val state by viewModel.state.collectAsState()

    val filePicker = org.messenger.app.util.rememberFilePicker { picked ->
        if (picked.mimeType.startsWith("image/", ignoreCase = true)) {
            viewModel.pickSourceImage(picked.name, picked.mimeType, picked.bytes)
        }
    }

    var deletingAvatar by remember { mutableStateOf<AvatarDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аватары") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePicker.launch("image/*") },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Загрузить новый")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && state.avatars.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.avatars.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Нет аватаров", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Нажмите + чтобы загрузить",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.avatars, key = { it.id }) { avatar ->
                            AvatarTile(
                                avatar = avatar,
                                isPrimary = avatar.id == state.primaryAvatarId,
                                attachmentsRepository = appModule.attachmentsRepository,
                                onClick = { viewModel.setPrimary(avatar.id) },
                                onDelete = { deletingAvatar = avatar },
                            )
                        }
                    }
                }
            }

            if (state.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 4.dp,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Загрузка...")
                        }
                    }
                }
            }

            state.error?.let { err ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                    }
                ) { Text(err) }
            }
        }

        // Crop-диалог
        val pendingSource = state.pendingSource
        val pendingBytes = state.pendingSourceBytes
        if (pendingSource != null && pendingBytes != null) {
            CropAvatarDialog(
                imageBytes = pendingBytes,
                originalWidth = pendingSource.width ?: 0,
                originalHeight = pendingSource.height ?: 0,
                onCancel = { viewModel.cancelPendingSource() },
                onConfirm = { x, y, size ->
                    viewModel.confirmCrop(x, y, size)
                },
            )
        }

        deletingAvatar?.let { avatar ->
            AlertDialog(
                onDismissRequest = { deletingAvatar = null },
                title = { Text("Удалить аватар?") },
                text = { Text("Аватар будет удалён без возможности восстановления.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteAvatar(avatar.id)
                        deletingAvatar = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingAvatar = null }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun AvatarTile(
    avatar: AvatarDto,
    isPrimary: Boolean,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            val cropUrl = avatar.cropUrl
            if (cropUrl != null) {
                AvatarCropImage(
                    avatarId = avatar.crop_attachment_id_safe(),
                    cropUrl = cropUrl,
                    attachmentsRepository = attachmentsRepository,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (isPrimary) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Основной",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarCropImage(
    avatarId: String,
    cropUrl: String,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
) {
    var bitmap by remember(avatarId) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(avatarId) {
        val cacheKey = "avatar_${avatarId}_crop"
        val cached = org.messenger.app.shared.util.ImageCache.get(cacheKey)
        if (cached != null) {
            bitmap = org.messenger.app.ui.common.decodeImageBitmap(cached)
            return@LaunchedEffect
        }
        val bytes = attachmentsRepository.loadImageBytes(
            attachmentId = avatarId,
            thumbnailUrl = cropUrl,
            downloadUrl = cropUrl,
            thumb = true,
        )
        if (bytes != null) {
            org.messenger.app.shared.util.ImageCache.put(cacheKey, bytes)
            bitmap = org.messenger.app.ui.common.decodeImageBitmap(bytes)
        }
    }
    val bm = bitmap
    if (bm != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.graphics.painter.BitmapPainter(bm),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

private fun AvatarDto.crop_attachment_id_safe(): String = this.cropAttachmentId