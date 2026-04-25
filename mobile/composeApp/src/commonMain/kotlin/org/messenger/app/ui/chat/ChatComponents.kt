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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import org.messenger.app.shared.data.model.AttachmentDto
import org.messenger.app.shared.ui.chat.UploadingAttachment
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import org.messenger.app.shared.data.model.MessageDto
import org.messenger.app.shared.data.model.PinnedMessageDto
import org.messenger.app.shared.data.model.ReplyPreviewDto
import androidx.compose.foundation.layout.aspectRatio

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
    uploadingAttachments: List<UploadingAttachment>,
    onDraftChanged: (String) -> Unit,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
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

            if (uploadingAttachments.isNotEmpty() && editing == null) {
                UploadingAttachmentsRow(
                    items = uploadingAttachments,
                    onRemove = onRemoveAttachment,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing == null) {
                    IconButton(onClick = onAttachClick) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить файл")
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.width(8.dp))

                val hasReadyAttachments = uploadingAttachments.any { !it.isUploading && it.attachment != null }
                val anyUploading = uploadingAttachments.any { it.isUploading }
                val canSend = !isSending && !anyUploading &&
                        (draft.isNotBlank() || hasReadyAttachments || editing != null)

                IconButton(
                    onClick = onSend,
                    enabled = canSend,
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
private fun UploadingAttachmentsRow(
    items: List<UploadingAttachment>,
    onRemove: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.localId }) { item ->
            UploadingAttachmentChip(item = item, onRemove = { onRemove(item.localId) })
        }
    }
}

@Composable
private fun UploadingAttachmentChip(
    item: UploadingAttachment,
    onRemove: () -> Unit,
) {
    val kind = detectKindByMime(item.mimeType)
    val isImage = kind == "image"

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.widthIn(min = if (isImage) 80.dp else 140.dp, max = 220.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Иконка или превью
            val preview = item.previewBytes
            if (isImage && preview != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small),
                ) {
                    org.messenger.app.ui.common.LocalBytesImage(
                        bytes = preview,
                        cacheKey = item.localId,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    if (item.isUploading) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        }
                    }
                }
            } else {
                Icon(
                    imageVector = iconForKind(kind),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.filename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val err = item.error
                when {
                    err != null -> {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item.isUploading -> {
                        Text(
                            text = "Загрузка...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            text = formatSize(item.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            if (item.isUploading && !isImage) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else if (!item.isUploading) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentChip(
    attachment: AttachmentDto,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
    onClick: () -> Unit,
) {
    val kind = attachment.fileKind.lowercase()
    if (kind == "image") {
        ImageAttachmentPreview(
            attachment = attachment,
            attachmentsRepository = attachmentsRepository,
            onClick = onClick,
        )
        return
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier
            .widthIn(min = 160.dp, max = 240.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconForKind(kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.originalFilename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatSize(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImageAttachmentPreview(
    attachment: AttachmentDto,
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
    onClick: () -> Unit,
) {
    val w = attachment.width ?: 1
    val h = attachment.height ?: 1
    val aspect = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1f

    var showViewer by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = Modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .clickable { showViewer = true },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.5f, 2.0f)),
        ) {
            org.messenger.app.ui.common.CachedAttachmentImage(
                attachment = attachment,
                attachmentsRepository = attachmentsRepository,
                variant = org.messenger.app.ui.common.ImageVariant.THUMB,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
    }

    if (showViewer) {
        org.messenger.app.ui.common.ImageViewerDialog(
            attachment = attachment,
            attachmentsRepository = attachmentsRepository,
            onDismiss = { showViewer = false },
        )
    }
}

private fun detectKindByMime(mime: String): String {
    val m = mime.lowercase()
    return when {
        m.startsWith("image/") -> "image"
        m.startsWith("video/") -> "video"
        m.startsWith("audio/") -> "audio"
        else -> "file"
    }
}

private fun iconForKind(kind: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (kind.lowercase()) {
        "image" -> Icons.Default.Image
        "video" -> Icons.Default.Movie
        "audio" -> Icons.Default.AudioFile
        else -> Icons.Default.InsertDriveFile
    }

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${"%.1f".format(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${"%.1f".format(mb)} MB"
    val gb = mb / 1024.0
    return "${"%.1f".format(gb)} GB"
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
    attachmentsRepository: org.messenger.app.shared.domain.repository.AttachmentsRepository,
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

                if (message.attachments.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = if (message.content.isNotBlank()) 6.dp else 0.dp),
                    ) {
                        message.attachments.forEach { att ->
                            AttachmentChip(
                                attachment = att,
                                attachmentsRepository = attachmentsRepository,
                                onClick = {
                                    att.downloadUrl?.let { url ->
                                        org.messenger.app.shared.util.openUrl(url)
                                    }
                                },
                            )
                        }
                    }
                }

                if (message.content.isNotBlank()) {
                    LinkifiedText(
                        text = message.content,
                        baseStyle = MaterialTheme.typography.bodyMedium,
                        onUrlClick = { url -> org.messenger.app.shared.util.openUrl(url) },
                    )
                }

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
        val normalized = if (
            iso.endsWith("Z") ||
            iso.contains("+") ||
            iso.substringAfter("T", "").contains("-")
        ) iso else "${iso}Z"
        val instant = kotlinx.datetime.Instant.parse(normalized)
        val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val hh = local.hour.toString().padStart(2, '0')
        val mm = local.minute.toString().padStart(2, '0')
        "$hh:$mm"
    } catch (_: Exception) {
        try {
            val timePart = if (iso.contains("T")) {
                iso.substringAfter("T").substringBefore(".")
                    .substringBefore("+").substringBefore("Z")
            } else iso
            timePart.take(5)
        } catch (_: Exception) { "" }
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
    targetRect: androidx.compose.ui.geometry.Rect?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    ) {
        if (targetRect == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        } else {
            val density = LocalDensity.current
            Layout(
                content = {
                    // top
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)))
                    // bottom
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)))
                    // left (of item row)
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)))
                    // right (of item row)
                    Box(modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)))
                },
                modifier = Modifier.fillMaxSize()
            ) { measurables, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val top = targetRect.top.toInt().coerceIn(0, h)
                val bottom = targetRect.bottom.toInt().coerceIn(0, h)
                val left = targetRect.left.toInt().coerceIn(0, w)
                val right = targetRect.right.toInt().coerceIn(0, w)

                val topH = top
                val bottomH = (h - bottom).coerceAtLeast(0)
                val midH = (bottom - top).coerceAtLeast(0)
                val leftW = left
                val rightW = (w - right).coerceAtLeast(0)

                val topP = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(w, topH))
                val bottomP = measurables[1].measure(androidx.compose.ui.unit.Constraints.fixed(w, bottomH))
                val leftP = measurables[2].measure(androidx.compose.ui.unit.Constraints.fixed(leftW, midH))
                val rightP = measurables[3].measure(androidx.compose.ui.unit.Constraints.fixed(rightW, midH))

                layout(w, h) {
                    topP.place(0, 0)
                    bottomP.place(0, bottom)
                    leftP.place(0, top)
                    rightP.place(right, top)
                }
            }
        }
    }
}

private data class TargetInfo(
    val offset: Int,
    val size: Int,
    val viewportStart: Int,
    val viewportEnd: Int,
)


@Composable
fun StickyDateHeader(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            tonalElevation = 2.dp,
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