package org.messenger.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.MeDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    appModule: AppModule,
    onBack: () -> Unit,
    onOpenAvatars: () -> Unit,
) {
    val viewModel = remember {
        ProfileViewModel(
            repository = appModule.profileRepository,
            tokenStorage = appModule.tokenStorage,
        )
    }
    val state by viewModel.state.collectAsState()

    var editing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.me != null && !editing) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                        }
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
                state.isLoading && state.me == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.me != null -> {
                    val me = state.me!!
                    if (editing) {
                        ProfileEditContent(
                            me = me,
                            isSaving = state.isSaving,
                            error = state.error,
                            onSave = { username, displayName, firstName, lastName, birthDate, bio, email ->
                                viewModel.updateProfile(
                                    username = username.takeIf { it != me.username },
                                    displayName = displayName.takeIf { it != me.displayName },
                                    firstName = firstName.takeIf { it != (me.firstName ?: "") },
                                    lastName = lastName.takeIf { it != (me.lastName ?: "") },
                                    birthDate = birthDate.takeIf { it != (me.birthDate ?: "") },
                                    bio = bio.takeIf { it != (me.bio ?: "") },
                                    email = email.takeIf { it != (me.email ?: "") },
                                    onSuccess = { editing = false },
                                )
                            },
                            onCancel = {
                                editing = false
                                viewModel.clearError()
                            },
                            onClearError = viewModel::clearError,
                        )
                    } else {
                        ProfileViewContent(
                            me = me,
                            attachmentsRepository = appModule.attachmentsRepository,
                            onOpenAvatars = onOpenAvatars,
                        )
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error ?: "Ошибка")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadProfile() }) { Text("Повторить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileViewContent(
    me: MeDto,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
    onOpenAvatars: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Аватар
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenAvatars),
            contentAlignment = Alignment.Center,
        ) {
            val thumbUrl = me.primaryAvatarThumbUrl
            val thumbId = me.primaryAvatarThumbAttachmentId
            if (thumbUrl != null && thumbId != null) {
                ProfileAvatarImage(
                    attachmentId = thumbId,
                    thumbnailUrl = thumbUrl,
                    downloadUrl = me.primaryAvatarUrl,
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
            // Иконка камеры в углу
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Изменить",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(me.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(
            "@${me.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        ProfileInfoRow(label = "Телефон", value = me.phone)

        val firstName = me.firstName
        val lastName = me.lastName
        if (!firstName.isNullOrBlank() || !lastName.isNullOrBlank()) {
            ProfileInfoRow(
                label = "Имя",
                value = listOfNotNull(firstName, lastName).joinToString(" "),
            )
        }

        val birthDate = me.birthDate
        if (!birthDate.isNullOrBlank()) {
            ProfileInfoRow(label = "Дата рождения", value = birthDate)
        }

        val email = me.email
        if (!email.isNullOrBlank()) {
            ProfileInfoRow(label = "Email", value = email)
        }

        val bio = me.bio
        if (!bio.isNullOrBlank()) {
            ProfileInfoRow(label = "О себе", value = bio)
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
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
private fun ProfileEditContent(
    me: MeDto,
    isSaving: Boolean,
    error: String?,
    onSave: (
        username: String,
        displayName: String,
        firstName: String,
        lastName: String,
        birthDate: String,
        bio: String,
        email: String,
    ) -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit,
) {
    var username by remember { mutableStateOf(me.username) }
    var displayName by remember { mutableStateOf(me.displayName) }
    var firstName by remember { mutableStateOf(me.firstName ?: "") }
    var lastName by remember { mutableStateOf(me.lastName ?: "") }
    var birthDate by remember { mutableStateOf(me.birthDate ?: "") }
    var bio by remember { mutableStateOf(me.bio ?: "") }
    var email by remember { mutableStateOf(me.email ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.lowercase() },
            label = { Text("Username") },
            supportingText = { Text("a-z, 0-9, _, -, ., #") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Отображаемое имя") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("Имя") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Фамилия") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it },
            label = { Text("Дата рождения") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("О себе") },
            minLines = 3,
            maxLines = 6,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Отмена") }

            Button(
                onClick = {
                    onClearError()
                    onSave(
                        username.trim(),
                        displayName.trim(),
                        firstName.trim(),
                        lastName.trim(),
                        birthDate.trim(),
                        bio.trim(),
                        email.trim(),
                    )
                },
                enabled = !isSaving && username.isNotBlank() && displayName.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Сохранить")
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatarImage(
    attachmentId: String,
    thumbnailUrl: String,
    downloadUrl: String?,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
) {
    var bitmap by remember(attachmentId) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(attachmentId) {
        val cacheKey = "avatar_${attachmentId}_thumb"
        val cached = org.messenger.app.shared.util.ImageCache.get(cacheKey)
        if (cached != null) {
            bitmap = org.messenger.app.ui.common.decodeImageBitmap(cached)
            return@LaunchedEffect
        }
        val bytes = attachmentsRepository.loadImageBytes(
            attachmentId = attachmentId,
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