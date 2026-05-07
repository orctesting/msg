package org.messenger.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.messenger.app.ui.common.decodeImageBitmap
import kotlin.math.min

/**
 * Диалог обрезки аватара. В центре экрана — фиксированный круглый "вьюпорт".
 * Пользователь двигает/масштабирует изображение под кругом.
 * При подтверждении вычисляются координаты (x, y, size) в пикселях оригинала.
 */
@Composable
fun CropAvatarDialog(
    imageBytes: ByteArray,
    originalWidth: Int,
    originalHeight: Int,
    onCancel: () -> Unit,
    onConfirm: (cropX: Int, cropY: Int, cropSize: Int) -> Unit,
) {
    val bitmap: ImageBitmap? = remember(imageBytes) { decodeImageBitmap(imageBytes) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (bitmap == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                CropEditor(
                    bitmap = bitmap,
                    onCancel = onCancel,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun CropEditor(
    bitmap: ImageBitmap,
    onCancel: () -> Unit,
    onConfirm: (cropX: Int, cropY: Int, cropSize: Int) -> Unit,
) {
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    val viewportSize: Float = if (containerSize.width > 0 && containerSize.height > 0) {
        min(containerSize.width, containerSize.height) * 0.8f
    } else 0f

    // baseScale: меньшая сторона изображения помещается в вьюпорт.
    val baseScale: Float = if (imgW > 0 && imgH > 0 && viewportSize > 0) {
        viewportSize / min(imgW, imgH)
    } else 1f

    val minScale = 1f
    val maxScale = 5f

    LaunchedEffect(containerSize, imgW, imgH, viewportSize) {
        if (!initialized && containerSize.width > 0 && containerSize.height > 0
            && imgW > 0 && imgH > 0 && viewportSize > 0
        ) {
            scale = 1f
            val drawW = imgW * baseScale * scale
            val drawH = imgH * baseScale * scale
            offsetX = (containerSize.width - drawW) / 2f
            offsetY = (containerSize.height - drawH) / 2f
            initialized = true
        }
    }

    fun clampOffsets() {
        if (viewportSize <= 0f || containerSize.width == 0) return
        val drawW = imgW * baseScale * scale
        val drawH = imgH * baseScale * scale
        val viewportLeft = (containerSize.width - viewportSize) / 2f
        val viewportTop = (containerSize.height - viewportSize) / 2f
        val viewportRight = viewportLeft + viewportSize
        val viewportBottom = viewportTop + viewportSize

        val maxOffX = viewportLeft
        val minOffX = viewportRight - drawW
        val maxOffY = viewportTop
        val minOffY = viewportBottom - drawH

        offsetX = if (minOffX > maxOffX) (maxOffX + minOffX) / 2f
        else offsetX.coerceIn(minOffX, maxOffX)
        offsetY = if (minOffY > maxOffY) (maxOffY + minOffY) / 2f
        else offsetY.coerceIn(minOffY, maxOffY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        // Изображение с жестами
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        scale = newScale
                        offsetX += pan.x
                        offsetY += pan.y
                        clampOffsets()
                    }
                },
        ) {
            if (containerSize.width > 0 && initialized) {
                val drawW = imgW * baseScale * scale
                val drawH = imgH * baseScale * scale
                val density = LocalDensity.current

                androidx.compose.foundation.Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset {
                            IntOffset(offsetX.toInt(), offsetY.toInt())
                        }
                        .size(
                            width = with(density) { drawW.toDp() },
                            height = with(density) { drawH.toDp() },
                        ),
                )
            }
        }

        // Затемнение + круглый вырез
        if (viewportSize > 0) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.Black.copy(alpha = 0.55f))
                drawCircle(
                    color = Color.Transparent,
                    radius = viewportSize / 2f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    blendMode = BlendMode.Clear,
                )
                drawCircle(
                    color = Color.White,
                    radius = viewportSize / 2f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2f),
                )
            }
        }

        // Кнопки
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("Отмена") }

            Button(
                onClick = {
                    if (containerSize.width == 0 || viewportSize <= 0f) return@Button
                    val viewportLeft = (containerSize.width - viewportSize) / 2f
                    val viewportTop = (containerSize.height - viewportSize) / 2f

                    val totalScale = baseScale * scale
                    val cropXf = (viewportLeft - offsetX) / totalScale
                    val cropYf = (viewportTop - offsetY) / totalScale
                    val cropSizef = viewportSize / totalScale

                    val cx = cropXf.coerceIn(0f, (imgW - 1).coerceAtLeast(0f)).toInt()
                    val cy = cropYf.coerceIn(0f, (imgH - 1).coerceAtLeast(0f)).toInt()
                    val maxSide = min(imgW - cx, imgH - cy).coerceAtLeast(1f)
                    val cs = cropSizef.coerceIn(1f, maxSide).toInt().coerceAtLeast(1)

                    onConfirm(cx, cy, cs)
                },
                modifier = Modifier.weight(1f),
            ) { Text("Готово") }
        }
    }
}