package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi

/** A Telegram link KiroX opens itself instead of handing it to the browser. */
sealed interface TelegramLink {
    /** Public user, bot, group or channel: t.me/name, tg://resolve?domain=name. */
    data class Username(val name: String) : TelegramLink
    /** A user mentioned by id (mention without a username). */
    data class User(val id: Long) : TelegramLink
}

private val usernamePattern = Regex("^[A-Za-z][A-Za-z0-9_]{3,31}$")
/** t.me paths that are not usernames (invites, sticker sets, proxies, …); the browser handles them. */
private val servicePaths = setOf("joinchat", "addstickers", "addemoji", "share", "proxy", "socks", "login", "setlanguage", "addtheme", "invoice", "boost", "c", "m", "s", "iv")

/** Parses [url]; null when it is not a link KiroX can open in the app. */
fun telegramLinkOf(url: String): TelegramLink? {
    val text = url.trim()
    if (text.startsWith("tg://", ignoreCase = true)) {
        val query = text.substringAfter('?', "")
        val params = query.split('&').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        return when (text.substringAfter("tg://").substringBefore('?').lowercase()) {
            "user" -> params["id"]?.toLongOrNull()?.let { TelegramLink.User(it) }
            "resolve" -> params["domain"]?.takeIf { usernamePattern.matches(it) }?.let { TelegramLink.Username(it) }
            else -> null
        }
    }
    val withoutScheme = text.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    val host = withoutScheme.substringBefore('/').lowercase()
    if (host != "t.me" && host != "telegram.me" && host != "telegram.dog") return null
    val name = withoutScheme.substringAfter('/', "").substringBefore('/').substringBefore('?').substringBefore('#')
    if (name.isEmpty() || name.lowercase() in servicePaths || name.startsWith("+")) return null
    return if (usernamePattern.matches(name)) TelegramLink.Username(name) else null
}

/** Chat to open for [link], or null when Telegram does not know it. */
suspend fun TelegramClient.resolveLink(link: TelegramLink): Long? = runCatching {
    when (link) {
        is TelegramLink.Username -> query(TdApi.SearchPublicChat(link.name)).id
        is TelegramLink.User -> query(TdApi.CreatePrivateChat(link.id, false)).id
    }
}.getOrNull()
