package org.messenger.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.messenger.app.getPlatformName
import org.messenger.app.shared.data.model.NotificationMode
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.notifications.NotificationSettingsViewModel
import org.messenger.app.notifications.reloadDesktopNotificationSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    appModule: AppModule,
    onBack: () -> Unit,
) {
    val platform = remember { getPlatformName() }
    val viewModel = remember {
        NotificationSettingsViewModel(
            platform = platform,
            notificationsRepository = appModule.notificationsRepository,
            chatRepository = appModule.chatRepository,
        )
    }
    val state by viewModel.state.collectAsState()

    var showChatPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Платформа: $platform",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    if (platform == "desktop") {
                        DesktopPlacementSelector()
                        Spacer(Modifier.height(16.dp))
                    }

                    Text("Показывать уведомления:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    ModeRow(
                        label = "Все",
                        selected = state.mode == NotificationMode.ALL,
                        onClick = { viewModel.setMode(NotificationMode.ALL) },
                    )
                    ModeRow(
                        label = "Только из личных чатов",
                        selected = state.mode == NotificationMode.PERSONAL_ONLY,
                        onClick = { viewModel.setMode(NotificationMode.PERSONAL_ONLY) },
                    )
                    ModeRow(
                        label = "Из выбранных чатов",
                        selected = state.mode == NotificationMode.WHITELIST,
                        onClick = { viewModel.setMode(NotificationMode.WHITELIST) },
                    )
                    ModeRow(
                        label = "Не показывать",
                        selected = state.mode == NotificationMode.NONE,
                        onClick = { viewModel.setMode(NotificationMode.NONE) },
                    )

                    if (state.mode == NotificationMode.WHITELIST) {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showChatPicker = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Выбранные чаты", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "${state.whitelistChatIds.size} из ${state.allChats.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { showChatPicker = true }) {
                                    Text("Изменить")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.save {
                                reloadDesktopNotificationSettings()
                            }
                        },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Сохранить")
                        }
                    }

                    if (state.savedSuccess) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Сохранено",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    state.error?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showChatPicker) {
        NotificationChatPickerDialog(
            allChats = state.allChats,
            selectedIds = state.whitelistChatIds,
            onDismiss = { showChatPicker = false },
            onConfirm = { ids ->
                viewModel.setWhitelist(ids)
                showChatPicker = false
            },
        )
    }
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationChatPickerDialog(
    allChats: List<org.messenger.app.shared.data.model.ChatDto>,
    selectedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var localSelected by remember { mutableStateOf(selectedIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Чаты для уведомлений") },
        text = {
            if (allChats.isEmpty()) {
                Text("Нет чатов")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(allChats, key = { it.id }) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    localSelected = if (localSelected.contains(chat.id))
                                        localSelected - chat.id
                                    else localSelected + chat.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = localSelected.contains(chat.id),
                                onCheckedChange = {
                                    localSelected = if (it)
                                        localSelected + chat.id
                                    else localSelected - chat.id
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = chat.name ?: "Чат",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localSelected) }) { Text("ОК") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun DesktopPlacementSelector() {
    val placement by org.messenger.app.notifications.DesktopPlacementBridge.placement.collectAsState()

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Тип уведомлений:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            PlacementRow(
                label = "Поверх всех окон",
                selected = placement == "system_overlay",
                onClick = { org.messenger.app.notifications.DesktopPlacementBridge.set("system_overlay") },
            )
            PlacementRow(
                label = "Внутри окна приложения",
                selected = placement == "in_app",
                onClick = { org.messenger.app.notifications.DesktopPlacementBridge.set("in_app") },
            )
        }
    }
}

@Composable
private fun PlacementRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}