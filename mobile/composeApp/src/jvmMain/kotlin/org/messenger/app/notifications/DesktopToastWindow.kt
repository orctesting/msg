package org.messenger.app.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.messenger.app.ui.theme.AppTheme
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Toolkit

/**
 * Отдельное undecorated окно поверх всех в правом нижнем углу экрана.
 * Используется когда пользователь выбрал режим "system overlay".
 */
@Composable
fun DesktopToastWindow(
    onClickToast: (chatId: String, chatName: String) -> Unit,
) {
    val toasts by DesktopNotificationManager.toasts.collectAsState()

    if (toasts.isEmpty()) return

    val (screenW, screenH) = remember {
        try {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val gd = ge.defaultScreenDevice
            val gc = gd.defaultConfiguration
            val bounds = gc.bounds
            val insets: Insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
            val w = bounds.width - insets.left - insets.right
            val h = bounds.height - insets.top - insets.bottom
            w to h
        } catch (_: Exception) {
            1280 to 800
        }
    }

    val toastsWidth = 380
    // Оценка высоты одной карточки: текст может занимать до 3 строк + отступы.
    val perToastHeight = 140
    val spacing = 8
    val paddingV = 16
    // Ограничиваем высоту окна максимальной разумной величиной.
    val maxToastsHeight = (screenH * 0.7f).toInt().coerceAtLeast(240)
    val toastsHeight = (toasts.size * perToastHeight + (toasts.size - 1).coerceAtLeast(0) * spacing + paddingV)
        .coerceAtMost(maxToastsHeight)
    val marginX = 16
    val marginY = 16

    val posX = (screenW - toastsWidth - marginX).coerceAtLeast(0)
    val posY = (screenH - toastsHeight - marginY).coerceAtLeast(0)

    val state = rememberWindowState(
        position = WindowPosition(posX.dp, posY.dp),
        width = toastsWidth.dp,
        height = toastsHeight.dp,
    )

    // Динамически обновляем размер и позицию при изменении количества тостов
    LaunchedEffect(toastsHeight, posY) {
        state.size = androidx.compose.ui.unit.DpSize(toastsWidth.dp, toastsHeight.dp)
        state.position = WindowPosition(posX.dp, posY.dp)
    }

    Window(
        onCloseRequest = {},
        state = state,
        visible = true,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        title = "Notifications",
    ) {
        AppTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    // Тосты прижимаем к низу окна и располагаем сверху вниз:
                    // новые внизу, старые уползают вверх в пределах окна.
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.End,
                ) {
                    // Разворачиваем список: первый в списке (самый новый) — последним в Column,
                    // т.е. внизу. Старые — выше.
                    toasts.asReversed().forEach { toast ->
                        key(toast.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                            ) {
                                ToastCardSystem(
                                    toast = toast,
                                    onClick = {
                                        onClickToast(toast.chatId, toast.chatName)
                                        DesktopNotificationManager.dismissToast(toast.id)
                                    },
                                    onClose = {
                                        DesktopNotificationManager.dismissToast(toast.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToastCardSystem(
    toast: DesktopToast,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 380.dp)
            .heightIn(min = 110.dp, max = 130.dp)
            .clickable(onClick = onClick),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toast.chatName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = toast.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = toast.text.ifBlank { "Вложение" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}