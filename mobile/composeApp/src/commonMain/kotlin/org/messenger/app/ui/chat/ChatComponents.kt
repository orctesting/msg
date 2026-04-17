package org.messenger.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.data.model.PinnedMessageDto
import org.messenger.app.shared.data.model.ReplyPreviewDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onForward: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text("$count") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть")
            }
        },
        actions = {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
            }
            if (onForward != null) {
                IconButton(onClick = onForward) {
                    Icon(Icons.Default.Forward, contentDescription = "Переслать")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить")
            }
        }
    )
}

@Composable
fun SelectionBottomBar(
    selectedCount: Int,
    onReply: (() -> Unit)?,
    onForward: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedCount == 1 && onReply != null) {
                TextButton(onClick = onReply) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ответить")
                }
            }
            TextButton(onClick = onForward) {
                Icon(Icons.Default.Forward, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Переслать")
            }
        }
    }
}

@Composable
fun PinnedMessageBar(
    pin: PinnedMessageDto,
    onClose: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Закреплённое сообщение",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = pin.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть")
            }
        }
    }
}

@Composable
fun ScrollToBottomButton(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    Box {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Вниз")
        }
        if (unreadCount > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
fun DateSeparator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MessageInput(
    draft: String,
    replyTo: MessageDto?,
    editing: MessageDto?,
    onDraftChanged: (String) -> Unit,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding(),
    ) {
        Column {
            if (editing != null) {
                ContextBar(
                    icon = Icons.Default.Edit,
                    title = "Редактирование",
                    content = editing.content,
                    onClose = onCancelEdit,
                )
            } else if (replyTo != null) {
                ContextBar(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    title = replyTo.senderName ?: "Ответ",
                    content = replyTo.content,
                    onClose = onCancelReply,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onSend,
                    enabled = !isSending && draft.isNotBlank(),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (editing != null) Icons.Default.Check else Icons.Default.Send,
                            contentDescription = if (editing != null) "Сохранить" else "Отправить",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextBar(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Отменить")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageDto,
    isOwnMessage: Boolean,
    isReadByOthers: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val type = message.messageType.lowercase()
    val isNotification = type == "notification"
    val isSystem = type == "system"
    val isAdmin = message.senderRole?.lowercase() == "admin"

    if (isNotification) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFFFEBEE),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350)),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        return
    }

    if (isSystem) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color(0xFFFFF3E0),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF795548),
                    )
                    Text(
                        text = formatTimeFromIso(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF795548).copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
        return
    }

    val bubbleColor = when {
        isSelected -> MaterialTheme.colorScheme.tertiaryContainer
        isOwnMessage -> MaterialTheme.colorScheme.primaryContainer
        isAdmin -> Color(0xFFFFCDD2)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val nameColor = when {
        isAdmin -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (!isOwnMessage) {
                    Text(
                        text = when {
                            isAdmin -> "Admin"
                            !message.senderName.isNullOrBlank() -> message.senderName!!
                            else -> "Пользователь"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = nameColor,
                    )
                }

                message.forwardedFrom?.let { fwd ->
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Column {
                            Text(
                                text = "Переслано от ${fwd.senderName ?: "пользователя"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            if (fwd.isDeleted) {
                                Text(
                                    text = "(оригинал удалён)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                message.replyTo?.let { reply ->
                    ReplyQuote(reply)
                    Spacer(Modifier.height(4.dp))
                }

                LinkifiedText(
                    text = message.content,
                    baseStyle = MaterialTheme.typography.bodyMedium,
                    onUrlClick = { url -> org.messenger.app.shared.util.openUrl(url) },
                )

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (message.editedAt != null) {
                        Text(
                            text = "изм.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = formatTimeFromIso(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    if (isOwnMessage) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "✓✓",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isReadByOthers)
                                Color(0xFF2196F3)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(reply: ReplyPreviewDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 28.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = reply.senderName ?: "Сообщение",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (reply.isDeleted) "Сообщение удалено" else reply.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (reply.isDeleted)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: MessageDto,
    canEdit: Boolean,
    canDelete: Boolean,
    forwardAvailable: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onSelect: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ActionRow(icon = Icons.AutoMirrored.Filled.Reply, label = "Ответить", onClick = onReply)
            ActionRow(icon = Icons.Default.ContentCopy, label = "Копировать", onClick = onCopy)
            if (forwardAvailable) {
                ActionRow(icon = Icons.Default.Forward, label = "Переслать", onClick = onForward)
            }
            ActionRow(icon = Icons.Default.PushPin, label = "Закрепить", onClick = onPin)
            if (canEdit) {
                ActionRow(icon = Icons.Default.Edit, label = "Редактировать", onClick = onEdit)
            }
            if (canDelete) {
                ActionRow(
                    icon = Icons.Default.Delete,
                    label = "Удалить",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
            ActionRow(icon = Icons.Default.Check, label = "Выбрать", onClick = onSelect)
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ForwardConfirmDialog(
    targetChatName: String,
    previewMessages: List<MessageDto>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переслать сообщение?") },
        text = {
            Column {
                Text(
                    text = "Вы уверены, что пересылаете ${previewMessages.size} " +
                            declOfNum(previewMessages.size, "сообщение", "сообщения", "сообщений") +
                            " в чат \"$targetChatName\"?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                previewMessages.take(3).forEach { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                if (previewMessages.size > 3) {
                    Text(
                        text = "…и ещё ${previewMessages.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Переслать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun declOfNum(n: Int, one: String, few: String, many: String): String {
    val n100 = n % 100
    if (n100 in 11..14) return many
    return when (n % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}

internal fun formatTimeFromIso(iso: String): String {
    return try {
        val timePart = if (iso.contains("T")) {
            iso.substringAfter("T").substringBefore(".")
                .substringBefore("+").substringBefore("Z")
        } else iso
        timePart.take(5)
    } catch (_: Exception) {
        ""
    }
}

internal fun isMessageReadByOthers(
    message: MessageDto,
    messages: List<MessageDto>,
    readUpToId: String?,
): Boolean {
    if (readUpToId == null) return false
    if (message.id == readUpToId) return true
    val readIdx = messages.indexOfFirst { it.id == readUpToId }
    val msgIdx = messages.indexOfFirst { it.id == message.id }
    if (readIdx == -1 || msgIdx == -1) return false
    return msgIdx >= readIdx
}

@Composable
fun FocusOverlay(
    onDismiss: () -> Unit,
    excludeMessageId: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    // Находим координаты выделяемого сообщения
    val info = listState.layoutInfo
    // Ищем item, key которого == excludeMessageId
    val target = info.visibleItemsInfo.firstOrNull { it.key == excludeMessageId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    ) {
        if (target == null) {
            // сообщение не видно — затемняем целиком
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        } else {
            // затемняем область выше и ниже целевого item
            val topPx = target.offset
            val bottomPx = target.offset + target.size
            val density = androidx.compose.ui.platform.LocalDensity.current
            val topDp = with(density) { topPx.toDp().coerceAtLeast(0.dp) }
            val bottomDp = with(density) { bottomPx.toDp() }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topDp)
                        .background(Color.Black.copy(alpha = 0.45f))
                )
                // пропускаем выделенный item
                Box(modifier = Modifier.fillMaxWidth().height(with(density) { target.size.toDp() }))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }
    }
}
