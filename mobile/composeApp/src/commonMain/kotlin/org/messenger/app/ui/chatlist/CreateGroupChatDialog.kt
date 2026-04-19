package org.messenger.app.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.UserDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.admin.CreateGroupChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupChatDialog(
    appModule: AppModule,
    onDismiss: () -> Unit,
    onCreated: (ChatDto) -> Unit,
) {
    val currentUserId = remember { appModule.tokenStorage.getUserId() }
    val viewModel = remember {
        CreateGroupChatViewModel(appModule.chatRepository, currentUserId)
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.createdChat) {
        state.createdChat?.let { onCreated(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Новый групповой чат") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = viewModel::createChat,
                                enabled = !state.isCreating && state.chatName.isNotBlank() && state.selectedIds.isNotEmpty()
                            ) {
                                if (state.isCreating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = "Создать")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = state.chatName,
                        onValueChange = viewModel::onChatNameChanged,
                        label = { Text("Название чата") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::onSearchChanged,
                        label = { Text("Поиск пользователей") },
                        placeholder = { Text("Имя или телефон") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Выбрано: ${state.selectedIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.users.isEmpty() && state.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (state.users.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Пользователи не найдены")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(state.users, key = { it.id }) { user ->
                                UserSelectRow(
                                    user = user,
                                    checked = state.selectedIds.contains(user.id),
                                    onToggle = { viewModel.toggleUser(user.id) }
                                )
                                HorizontalDivider()
                            }
                            if (state.hasMore) {
                                item {
                                    LaunchedEffect(Unit) { viewModel.loadUsers() }
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    state.error?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSelectRow(
    user: UserDto,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(user.displayName) },
        supportingContent = { Text(user.phone, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    )
}