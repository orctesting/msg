package org.messenger.app.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.ContactDto
import org.messenger.app.shared.di.AppModule
import org.messenger.app.shared.ui.contacts.ContactsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    appModule: AppModule,
    onBack: () -> Unit,
    onContactClick: (ContactDto) -> Unit,
) {
    val viewModel = remember {
        ContactsViewModel(appModule.contactsRepository)
    }
    val state by viewModel.state.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactDto?>(null) }
    var deletingContact by remember { mutableStateOf<ContactDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Контакты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить контакт")
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
                        Text("Нет контактов", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Нажмите + чтобы добавить",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.contacts, key = { it.id }) { contact ->
                            ContactRow(
                                contact = contact,
                                onClick = {
                                    if (contact.isRegistered) onContactClick(contact)
                                },
                                onEdit = { editingContact = contact },
                                onDelete = { deletingContact = contact }
                            )
                            HorizontalDivider()
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
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(err)
                }
            }
        }

        if (showAddDialog) {
            ContactFormDialog(
                title = "Новый контакт",
                initialPhone = "",
                initialDisplayName = "",
                onDismiss = { showAddDialog = false },
                onConfirm = { phone, name ->
                    viewModel.addContact(phone, name) { ok ->
                        if (ok) showAddDialog = false
                    }
                }
            )
        }

        editingContact?.let { contact ->
            ContactFormDialog(
                title = "Редактировать",
                initialPhone = contact.phone,
                initialDisplayName = contact.displayName,
                onDismiss = { editingContact = null },
                onConfirm = { phone, name ->
                    viewModel.updateContact(
                        contactId = contact.id,
                        displayName = name.takeIf { it != contact.displayName },
                        phone = phone.takeIf { it != contact.phone },
                    ) { ok ->
                        if (ok) editingContact = null
                    }
                }
            )
        }

        deletingContact?.let { contact ->
            AlertDialog(
                onDismissRequest = { deletingContact = null },
                title = { Text("Удалить контакт?") },
                text = { Text("Удалить \"${contact.displayName}\" из контактов?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteContact(contact.id)
                        deletingContact = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingContact = null }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactDto,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(contact.displayName)
        },
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
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                color = if (contact.isRegistered)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (contact.isRegistered) Icons.Default.Person else Icons.Default.PersonOff,
                        contentDescription = null,
                        tint = if (contact.isRegistered)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun ContactFormDialog(
    title: String,
    initialPhone: String,
    initialDisplayName: String,
    onDismiss: () -> Unit,
    onConfirm: (phone: String, displayName: String) -> Unit,
) {
    var phone by remember { mutableStateOf(initialPhone.ifBlank { "+7" }) }
    var displayName by remember { mutableStateOf(initialDisplayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        val sanitized = when {
                            it.isEmpty() -> "+"
                            !it.startsWith("+") -> "+$it"
                            else -> it
                        }
                        phone = sanitized
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