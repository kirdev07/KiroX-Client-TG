package com.kirox.app.ui.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.TelegramEntityType
import com.kirox.app.telegram.TelegramTextEntity
import com.kirox.app.ui.theme.Fusion

/**
 * Where tapping an entity leads, or null when it is not a link. `t.me` and `tg:` links are opened
 * inside KiroX by the chat's UriHandler; everything else goes to the system.
 */
internal fun entityLink(text: String, entity: TelegramTextEntity): String? {
    val end = entity.offset + entity.length
    if (entity.offset < 0 || end > text.length) return null
    val value = text.substring(entity.offset, end)
    return when (entity.type) {
        TelegramEntityType.TextUrl, TelegramEntityType.MentionName -> entity.url
        TelegramEntityType.Url -> if (value.contains("://")) value else "https://$value"
        TelegramEntityType.Mention -> "https://t.me/${value.removePrefix("@")}"
        TelegramEntityType.Email -> "mailto:$value"
        TelegramEntityType.Phone -> "tel:${value.filter { it.isDigit() || it == '+' }}"
        else -> null
    }
}

/**
 * Opens a link tapped in a message. Set by the app so t.me and tg: links open chats inside KiroX;
 * when not set, links go to the system browser.
 */
val LocalLinkOpener = androidx.compose.runtime.staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Message text with Telegram formatting, tappable links and spoilers that open on tap. */
@Composable
internal fun MessageText(text: String, entities: List<TelegramTextEntity>, scale: Float, color: Color = Fusion.colors.textPrimary) {
    if (text.isEmpty()) return
    var revealed by remember(text) { mutableStateOf(false) }
    val palette = TextPalette(link = color, secondary = Fusion.colors.textSecondary, hidden = Fusion.colors.surface3)
    val opener = LocalLinkOpener.current
    val annotated = remember(text, entities, revealed, palette, opener) { formatMessage(text, entities, revealed, palette, opener) { revealed = true } }
    Text(annotated, color = color, fontSize = (15f * scale).sp, lineHeight = (20f * scale).sp)
}

/** Fusion keeps links monochrome: the text colour, underlined. */
private data class TextPalette(val link: Color, val secondary: Color, val hidden: Color)

private val CodeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x26808080))

private fun formatMessage(text: String, entities: List<TelegramTextEntity>, revealed: Boolean, palette: TextPalette, opener: ((String) -> Unit)?, onReveal: () -> Unit): AnnotatedString = buildAnnotatedString {
    val linkStyle = SpanStyle(color = palette.link, textDecoration = TextDecoration.Underline)
    val hiddenStyle = SpanStyle(color = palette.hidden, background = palette.hidden)
    append(text)
    for (entity in entities) {
        val start = entity.offset
        val end = entity.offset + entity.length
        if (start < 0 || end > text.length || start >= end) continue
        when (entity.type) {
            TelegramEntityType.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            TelegramEntityType.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            TelegramEntityType.Underline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            TelegramEntityType.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            TelegramEntityType.Code, TelegramEntityType.Pre -> addStyle(CodeStyle, start, end)
            TelegramEntityType.Quote -> addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = palette.secondary), start, end)
            TelegramEntityType.Hashtag, TelegramEntityType.BotCommand -> addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, end)
            TelegramEntityType.Spoiler -> if (!revealed) {
                addStyle(hiddenStyle, start, end)
                addLink(LinkAnnotation.Clickable("spoiler", TextLinkStyles(hiddenStyle)) { onReveal() }, start, end)
            }
            else -> entityLink(text, entity)?.let { url ->
                val link = if (opener != null) LinkAnnotation.Clickable(url, TextLinkStyles(linkStyle)) { opener(url) }
                else LinkAnnotation.Url(url, TextLinkStyles(linkStyle))
                addLink(link, start, end)
            }
        }
    }
}
