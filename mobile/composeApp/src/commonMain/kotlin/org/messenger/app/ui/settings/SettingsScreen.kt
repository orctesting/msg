package org.messenger.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Person
import org.messenger.app.shared.di.AppModule

const val APP_NAME = "Messenger Oreshnik"
const val APP_VERSION = "1.0.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appModule: AppModule,
    onBack: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val phone = appModule.tokenStorage.getUserPhone()
    val displayName = appModule.tokenStorage.getUserDisplayName()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!displayName.isNullOrBlank()) {
                    Text(displayName, style = MaterialTheme.typography.titleMedium)
                }
                if (!phone.isNullOrBlank()) {
                    Text(
                        phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                    headlineContent = { Text("Профиль") },
                    leadingContent = {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Person,
                            contentDescription = null,
                        )
                    }
                )
                HorizontalDivider()

                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenContacts),
                    headlineContent = { Text("Контакты") },
                    leadingContent = {
                        Icon(Icons.Default.Contacts, contentDescription = null)
                    }
                )
                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выйти из аккаунта")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = APP_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v$APP_VERSION",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}