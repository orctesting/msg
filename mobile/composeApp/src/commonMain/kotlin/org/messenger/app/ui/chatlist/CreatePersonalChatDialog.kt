package org.messenger.app.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.ChatDto
import org.messenger.app.shared.data.model.ContactDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.contacts.ContactsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePersonalChatDialog(
    appModule: AppModule,
    onDismiss: () -> Unit,
    onChatReady: (ChatDto) -> Unit,
) {
    val viewModel = remember { ContactsViewModel(appModule.contactsRepository) }
    val state by viewModel.state.collectAsState()
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                        title = { Text("Новый личный чат") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
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
                        state.isLoading && state.contacts.isEmpty() -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.contacts.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Нет контактов")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Добавьте контакты в разделе «Контакты»",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.contacts, key = { it.id }) { contact ->
                                    PickContactRow(
                                        contact = contact,
                                        enabled = contact.isRegistered && !isCreating,
                                        onClick = {
                                            if (!contact.isRegistered) return@PickContactRow
                                            isCreating = true
                                            errorMessage = null
                                            scope.launch {
                                                try {
                                                    val chat = appModule.chatRepository
                                                        .createPersonalChat(contactId = contact.id)
                                                    onChatReady(chat)
                                                } catch (e: Exception) {
                                                    errorMessage = e.message ?: "Ошибка"
                                                } finally {
                                                    isCreating = false
                                                }
                                            }
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    if (isCreating) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    errorMessage?.let { err ->
                        Snackbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            action = {
                                TextButton(onClick = { errorMessage = null }) { Text("OK") }
                            }
                        ) { Text(err) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickContactRow(
    contact: ContactDto,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(contact.displayName) },
        supportingContent = {
            Column {
                Text(contact.phone, style = MaterialTheme.typography.bodySmall)
                if (!contact.isRegistered) {
                    Text(
                        "Не зарегистрирован",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                color = if (contact.isRegistered)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (contact.isRegistered) Icons.Default.Person else Icons.Default.PersonOff,
                        contentDescription = null
                    )
                }
            }
        }
    )
}