package org.messenger.app.ui.forward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.chatlist.ChatListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardTargetScreen(
    appModule: AppModule,
    sourceChatId: String,
    messageIds: List<String>,
    onCancel: () -> Unit,
    onPicked: (targetChatId: String) -> Unit,
) {

    val viewModel = remember {
        ChatListViewModel(
            chatRepository = appModule.chatRepository,
            wsService = appModule.wsService,
            tokenStorage = appModule.tokenStorage,
            activeChatIdProvider = { org.messenger.app.ActiveChatHolder.get() },
        )
    }
    val state by viewModel.state.collectAsState()

    // Подтверждение
    var pendingTarget by remember { mutableStateOf<ChatDto?>(null) }
    var previewMessages by remember { mutableStateOf<List<MessageDto>>(emptyList()) }
    org.messenger.app.util.PlatformBackHandler(
        enabled = true,
        onBack = {
            if (pendingTarget != null) pendingTarget = null
            else onCancel()
        }
    )
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        // Подгрузим превью сообщений из исходного чата
        try {
            val page = appModule.chatRepository.getMessages(sourceChatId, limit = 100)
            previewMessages = page.messages.filter { messageIds.contains(it.id) }
        } catch (_: Exception) {
            previewMessages = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Переслать в...") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading && state.chats.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val targets = state.chats.filter { it.id != sourceChatId }
                if (targets.isEmpty()) {
                    Text(
                        text = "Нет доступных чатов",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(targets, key = { it.id }) { chat ->
                            ForwardTargetRow(chat = chat, onClick = { pendingTarget = chat })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        pendingTarget?.let { target ->
            if (previewMessages.isNotEmpty()) {
                org.messenger.app.ui.chat.ForwardConfirmDialog(
                    targetChatName = target.name ?: "чат",
                    previewMessages = previewMessages,
                    onConfirm = {
                        onPicked(target.id)
                        pendingTarget = null
                    },
                    onDismiss = { pendingTarget = null },
                )
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(chat: ChatDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.name ?: chat.id,
                style = MaterialTheme.typography.bodyLarge,
            )
            chat.lastMessage?.let {
                Text(
                    text = it.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}