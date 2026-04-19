package org.messenger.app.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
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
    var showAddContactDialog by remember { mutableStateOf(false) }

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
                },
                bottomBar = {
                    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showAddContactDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить контакт")
                        }
                    }
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
                                    "Добавьте контакт кнопкой снизу",
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

    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onConfirm = { phone, name ->
                viewModel.addContact(phone, name) { ok ->
                    if (ok) showAddContactDialog = false
                }
            }
        )
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (phone: String, displayName: String) -> Unit,
) {
    var phone by remember { mutableStateOf("+7") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый контакт") },
        text = {
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        val s = when {
                            it.isEmpty() -> "+"
                            !it.startsWith("+") -> "+$it"
                            else -> it
                        }
                        phone = s
                    },
                    label = { Text("Телефон") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(phone.trim(), displayName.trim()) },
                enabled = phone.length > 1 && displayName.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
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