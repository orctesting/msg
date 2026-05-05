package org.messenger.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.messenger.app.isDesktop
import org.messenger.app.shared.ui.auth.AuthStep
import org.messenger.app.shared.ui.auth.AuthViewModel

// 30% от минимальной ширины окна (800 dp) = 240 dp
private val DESKTOP_FORM_WIDTH = 320.dp

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onRequestOtp: (serverAddress: String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val formModifier = if (isDesktop) {
                Modifier.width(DESKTOP_FORM_WIDTH)
            } else {
                Modifier.fillMaxWidth()
            }

            Column(
                modifier = formModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Мессенджер",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                when (state.step) {
                    AuthStep.PHONE -> PhoneStep(
                        phone = state.phone,
                        serverAddress = state.serverAddress,
                        onPhoneChanged = viewModel::onPhoneChanged,
                        onServerAddressChanged = viewModel::onServerAddressChanged,
                        onSubmit = { onRequestOtp(state.serverAddress.trim()) },
                        isLoading = state.isLoading
                    )
                    AuthStep.CODE -> CodeStep(
                        phone = state.phone,
                        code = state.code,
                        onCodeChanged = viewModel::onCodeChanged,
                        onSubmit = viewModel::verifyOtp,
                        onBack = viewModel::backToPhone,
                        isLoading = state.isLoading
                    )
                }

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneStep(
    phone: String,
    serverAddress: String,
    onPhoneChanged: (String) -> Unit,
    onServerAddressChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean
) {
    LaunchedEffect(Unit) {
        if (phone.isBlank()) {
            onPhoneChanged("+7")
        }
    }

    OutlinedTextField(
        value = serverAddress,
        onValueChange = onServerAddressChanged,
        label = { Text("Адрес сервера") },
        placeholder = { Text("192.168.1.100:8000") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = phone,
        onValueChange = { newValue ->
            val sanitized = when {
                newValue.isEmpty() -> "+"
                !newValue.startsWith("+") -> "+$newValue"
                else -> newValue
            }
            onPhoneChanged(sanitized)
        },
        label = { Text("Номер телефона") },
        placeholder = { Text("+7 999 123 45 67") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onSubmit,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text("Получить код")
        }
    }
}

@Composable
private fun CodeStep(
    phone: String,
    code: String,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean
) {
    Text(
        text = "Код отправлен на $phone",
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = code,
        onValueChange = onCodeChanged,
        label = { Text("Код подтверждения") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onSubmit,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text("Войти")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(onClick = onBack) {
        Text("Изменить номер")
    }
}