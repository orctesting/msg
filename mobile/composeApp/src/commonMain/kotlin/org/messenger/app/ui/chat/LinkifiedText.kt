package org.messenger.app.ui.chat

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private val URL_REGEX = Regex(
    """(https?://|www\.)[^\s]+""",
    RegexOption.IGNORE_CASE
)

@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = LocalTextStyle.current,
    onUrlClick: (String) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        var lastIdx = 0
        URL_REGEX.findAll(text).forEach { match ->
            if (match.range.first > lastIdx) {
                append(text.substring(lastIdx, match.range.first))
            }
            val url = match.value
            val href = if (url.startsWith("www.", ignoreCase = true)) "http://$url" else url
            pushStringAnnotation(tag = "URL", annotation = href)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(url)
            }
            pop()
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) append(text.substring(lastIdx))
    }

    ClickableText(
        text = annotated,
        style = baseStyle.copy(color = baseStyle.color.takeOrDefault(MaterialTheme.colorScheme.onSurface)),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { onUrlClick(it.item) }
        }
    )
}

private fun androidx.compose.ui.graphics.Color.takeOrDefault(
    default: androidx.compose.ui.graphics.Color
): androidx.compose.ui.graphics.Color =
    if (this == androidx.compose.ui.graphics.Color.Unspecified) default else this