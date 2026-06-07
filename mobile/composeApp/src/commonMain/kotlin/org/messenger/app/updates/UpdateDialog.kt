package org.messenger.app.updates

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.messenger.app.shared.data.model.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    progress: UpdateProgress,
    installing: Boolean,
    onUpdate: () -> Unit,
    onPostpone: () -> Unit,
    onDismissError: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!installing && !info.isMandatory) onPostpone() },
        title = { Text("Доступно обновление ${info.versionName}") },
        text = {
            Column {
                info.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                when (val p = progress) {
                    is UpdateProgress.Downloading -> {
                        val frac = if (p.total > 0) p.downloaded.toFloat() / p.total else 0f
                        LinearProgressIndicator(
                            progress = { frac.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Загрузка ${(frac * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    is UpdateProgress.Verifying -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Проверка целостности…", style = MaterialTheme.typography.labelSmall)
                    }
                    is UpdateProgress.Installing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Установка, перезапуск…", style = MaterialTheme.typography.labelSmall)
                    }
                    is UpdateProgress.Failed -> {
                        Text(
                            p.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    UpdateProgress.Idle -> {
                        if (info.isMandatory) {
                            Text(
                                "Это обновление обязательно для продолжения работы.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                progress is UpdateProgress.Failed -> {
                    TextButton(onClick = onDismissError) { Text("Закрыть") }
                }
                !installing -> {
                    TextButton(onClick = onUpdate) { Text("Обновить") }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (!installing && progress !is UpdateProgress.Failed && !info.isMandatory) {
                TextButton(onClick = onPostpone) { Text("Позже") }
            }
        },
    )
}