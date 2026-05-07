package org.messenger.app.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.messenger.app.MessengerApplication

@Composable
actual fun rememberFilePicker(
    onPicked: (PickedFile) -> Unit,
): FilePickerLauncher {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = queryFileName(context, uri) ?: "file"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            onPicked(PickedFile(name = name, mimeType = mime, bytes = bytes))
        } catch (_: Exception) {}
    }

    return remember {
        object : FilePickerLauncher {
            override fun launch(mimeFilter: String) {
                launcher.launch(mimeFilter)
            }
        }
    }
}

private fun queryFileName(
    context: android.content.Context,
    uri: android.net.Uri,
): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else null
        }
    } catch (_: Exception) {
        null
    }
}