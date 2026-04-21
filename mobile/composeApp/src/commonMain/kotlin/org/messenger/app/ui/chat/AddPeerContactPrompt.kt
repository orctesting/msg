package org.messenger.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.messenger.app.shared.data.model.PeerUserDto
import org.messenger.app.shared.domain.repository.ContactsRepository

@Composable
fun AddPeerContactPrompt(
    peer: PeerUserDto,
    contactsRepository: ContactsRepository,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Добавить в контакты?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${peer.displayName} · ${peer.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onDismiss) { Text("Не сейчас") }
            TextButton(onClick = { showDialog = true }) { Text("Добавить") }
        }
    }

    if (showDialog) {
        AddContactInlineDialog(
            initialPhone = peer.phone,
            initialDisplayName = peer.displayName,
            contactsRepository = contactsRepository,
            onDismiss = { showDialog = false },
            onSuccess = {
                showDialog = false
                onAdded()
            },
        )
    }
}

@Composable
private fun AddContactInlineDialog(
    initialPhone: String,
    initialDisplayName: String,
    contactsRepository: ContactsRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    var phone by remember { mutableStateOf(initialPhone) }
    var name by remember { mutableStateOf(initialDisplayName) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
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
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && phone.length > 1 && name.isNotBlank(),
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            contactsRepository.createContact(phone.trim(), name.trim())
                            onSuccess()
                        } catch (e: Exception) {
                            error = e.message ?: "Ошибка"
                            isLoading = false
                        }
                    }
                }
            ) { Text(if (isLoading) "..." else "Сохранить") }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismiss) { Text("Отмена") }
        }
    )
}