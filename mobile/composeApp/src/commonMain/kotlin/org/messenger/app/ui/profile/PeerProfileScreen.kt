package org.messenger.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.PublicUserDto
import org.messenger.app.shared.di.AppModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerProfileScreen(
    appModule: AppModule,
    userId: String,
    onBack: () -> Unit,
) {
    var user by remember { mutableStateOf<PublicUserDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    LaunchedEffect(userId) {
        scope.launch {
            try {
                user = appModule.profileRepository.getPublicUser(userId)
            } catch (e: Exception) {
                error = e.message ?: "Ошибка загрузки"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                user != null -> PeerContent(
                    user = user!!,
                    attachmentsRepository = appModule.attachmentsRepository,
                )
                error != null -> Text(
                    error ?: "",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PeerContent(
    user: PublicUserDto,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val thumbUrl = user.primaryAvatarThumbUrl
            if (thumbUrl != null) {
                PeerAvatarImage(
                    userId = user.id,
                    thumbnailUrl = thumbUrl,
                    downloadUrl = user.primaryAvatarUrl,
                    attachmentsRepository = attachmentsRepository,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(user.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
            "@${user.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        val firstName = user.firstName
        val lastName = user.lastName
        if (!firstName.isNullOrBlank() || !lastName.isNullOrBlank()) {
            InfoRow(
                label = "Имя",
                value = listOfNotNull(firstName, lastName).joinToString(" "),
            )
        }

        val bio = user.bio
        if (!bio.isNullOrBlank()) {
            InfoRow(label = "О себе", value = bio)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}

@Composable
private fun PeerAvatarImage(
    userId: String,
    thumbnailUrl: String,
    downloadUrl: String?,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
) {
    var bitmap by remember(userId) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(userId) {
        val cacheKey = "peer_${userId}_thumb"
        val cached = org.messenger.app.shared.util.ImageCache.get(cacheKey)
        if (cached != null) {
            bitmap = org.messenger.app.ui.common.decodeImageBitmap(cached)
            return@LaunchedEffect
        }
        val bytes = attachmentsRepository.loadImageBytes(
            attachmentId = userId,
            thumbnailUrl = thumbnailUrl,
            downloadUrl = downloadUrl,
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
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}