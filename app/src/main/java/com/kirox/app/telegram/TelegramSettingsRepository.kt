package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi

/** Who may see or do something (base privacy rule; per-user exceptions are kept as they are). */
enum class PrivacyLevel(val title: String) { Everybody("Все"), Contacts("Мои контакты"), Nobody("Никто") }

/** Privacy settings shown in KiroX. */
enum class PrivacyItem(val title: String) {
    PhoneNumber("Номер телефона"),
    LastSeen("Время захода"),
    ProfilePhoto("Фото профиля"),
    Calls("Звонки"),
    ChatInvites("Приглашения в группы"),
    Bio("О себе"),
    Birthdate("Дата рождения"),
    ForwardLink("Ссылка при пересылке"),
    VoiceNotes("Голосовые и видеосообщения");

    internal fun toTd(): TdApi.UserPrivacySetting = when (this) {
        PhoneNumber -> TdApi.UserPrivacySettingShowPhoneNumber()
        LastSeen -> TdApi.UserPrivacySettingShowStatus()
        ProfilePhoto -> TdApi.UserPrivacySettingShowProfilePhoto()
        Calls -> TdApi.UserPrivacySettingAllowCalls()
        ChatInvites -> TdApi.UserPrivacySettingAllowChatInvites()
        Bio -> TdApi.UserPrivacySettingShowBio()
        Birthdate -> TdApi.UserPrivacySettingShowBirthdate()
        ForwardLink -> TdApi.UserPrivacySettingShowLinkInForwardedMessages()
        VoiceNotes -> TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages()
    }
}

/** Notification scopes: private chats, groups, channels. */
enum class NotifyScope(val title: String) {
    Private("Личные чаты"), Groups("Группы"), Channels("Каналы");

    internal fun toTd(): TdApi.NotificationSettingsScope = when (this) {
        Private -> TdApi.NotificationSettingsScopePrivateChats()
        Groups -> TdApi.NotificationSettingsScopeGroupChats()
        Channels -> TdApi.NotificationSettingsScopeChannelChats()
    }
}

data class ScopeNotifications(val enabled: Boolean, val showPreview: Boolean)

/** A blocked user or chat. */
data class BlockedSender(val id: Long, val isChat: Boolean, val name: String, val photo: TelegramFileRef?)

/** Cloud password (two-step verification) state; [pendingEmailPattern] while a recovery email waits for its code. */
data class CloudPasswordState(val hasPassword: Boolean, val hint: String, val hasRecoveryEmail: Boolean, val pendingEmailPattern: String?)

/** A chat whose notifications differ from the defaults. */
data class NotificationException(val chatId: Long, val title: String, val muted: Boolean)

enum class ProxyKind(val title: String) { Socks5("SOCKS5"), Http("HTTP"), MtProto("MTProto") }

/** A proxy added to TDLib. */
data class TelegramProxy(val id: Int, val server: String, val port: Int, val kind: ProxyKind, val enabled: Boolean)

/** Proxy from a tg://proxy, tg://socks or t.me/proxy link; null when the text is not one. */
fun parseProxyLink(link: String): Triple<String, Int, Pair<ProxyKind, String>>? {
    val text = link.trim()
    val kind = when {
        text.startsWith("tg://proxy", true) || text.contains("t.me/proxy", true) -> ProxyKind.MtProto
        text.startsWith("tg://socks", true) || text.contains("t.me/socks", true) -> ProxyKind.Socks5
        else -> return null
    }
    val params = text.substringAfter('?', "").split('&').mapNotNull { part ->
        part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].lowercase() to java.net.URLDecoder.decode(it[1], "UTF-8") }
    }.toMap()
    val server = params["server"]?.takeIf { it.isNotBlank() } ?: return null
    val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return Triple(server, port, kind to (if (kind == ProxyKind.MtProto) params["secret"].orEmpty() else listOf(params["user"].orEmpty(), params["pass"].orEmpty()).joinToString(":")))
}

data class StorageUsage(val filesBytes: Long, val fileCount: Int, val databaseBytes: Long)

/** Account-level settings stored in Telegram: notifications, privacy, local storage. */
class TelegramSettingsRepository(private val client: TelegramClient) {

    suspend fun notifications(scope: NotifyScope): ScopeNotifications =
        client.query(TdApi.GetScopeNotificationSettings(scope.toTd())).let { ScopeNotifications(it.muteFor == 0, it.showPreview) }

    suspend fun setNotifications(scope: NotifyScope, enabled: Boolean, showPreview: Boolean) {
        val current = client.query(TdApi.GetScopeNotificationSettings(scope.toTd()))
        current.muteFor = if (enabled) 0 else MUTE_FOREVER_S
        current.showPreview = showPreview
        client.query(TdApi.SetScopeNotificationSettings(scope.toTd(), current))
    }

    suspend fun privacy(item: PrivacyItem): PrivacyLevel {
        val rules = client.query(TdApi.GetUserPrivacySettingRules(item.toTd())).rules
        return when {
            rules.any { it is TdApi.UserPrivacySettingRuleAllowAll } -> PrivacyLevel.Everybody
            rules.any { it is TdApi.UserPrivacySettingRuleAllowContacts } -> PrivacyLevel.Contacts
            else -> PrivacyLevel.Nobody
        }
    }

    /** Replaces the base rule and keeps exceptions for specific users and chats. */
    suspend fun setPrivacy(item: PrivacyItem, level: PrivacyLevel) {
        val exceptions = client.query(TdApi.GetUserPrivacySettingRules(item.toTd())).rules.filter {
            it is TdApi.UserPrivacySettingRuleAllowUsers || it is TdApi.UserPrivacySettingRuleRestrictUsers ||
                it is TdApi.UserPrivacySettingRuleAllowChatMembers || it is TdApi.UserPrivacySettingRuleRestrictChatMembers
        }
        val base: List<TdApi.UserPrivacySettingRule> = when (level) {
            PrivacyLevel.Everybody -> listOf(TdApi.UserPrivacySettingRuleAllowAll())
            PrivacyLevel.Contacts -> listOf(TdApi.UserPrivacySettingRuleAllowContacts(), TdApi.UserPrivacySettingRuleRestrictAll())
            PrivacyLevel.Nobody -> listOf(TdApi.UserPrivacySettingRuleRestrictAll())
        }
        // Exceptions go first: Telegram applies the first matching rule.
        client.query(TdApi.SetUserPrivacySettingRules(item.toTd(), TdApi.UserPrivacySettingRules((exceptions + base).toTypedArray())))
    }

    suspend fun storage(): StorageUsage =
        client.query(TdApi.GetStorageStatisticsFast()).let { StorageUsage(it.filesSize, it.fileCount, it.databaseSize) }

    /** Deletes all cached media and files; messages stay in the cloud and download again when opened. */
    suspend fun clearCache() {
        client.query(TdApi.OptimizeStorage(0, 0, 0, 0, null, null, null, false, 0))
    }

    private companion object { const val MUTE_FOREVER_S = 400 * 24 * 60 * 60 }

    // ---------- Blocked users ----------

    suspend fun blocked(): List<BlockedSender> {
        val senders = client.query(TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), 0, 100)).senders
        return senders.mapNotNull { sender ->
            runCatching {
                when (sender) {
                    is TdApi.MessageSenderUser -> client.query(TdApi.GetUser(sender.userId)).let { u ->
                        BlockedSender(u.id, false, listOf(u.firstName, u.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Удалённый аккаунт" }, u.profilePhoto?.small?.toRef())
                    }
                    is TdApi.MessageSenderChat -> client.query(TdApi.GetChat(sender.chatId)).let { c -> BlockedSender(c.id, true, c.title, c.photo?.small?.toRef()) }
                    else -> null
                }
            }.getOrNull()
        }
    }

    suspend fun unblock(sender: BlockedSender) {
        val id: TdApi.MessageSender = if (sender.isChat) TdApi.MessageSenderChat(sender.id) else TdApi.MessageSenderUser(sender.id)
        client.query(TdApi.SetMessageSenderBlockList(id, null))
    }

    // ---------- Cloud password ----------

    suspend fun passwordState(): CloudPasswordState = client.query(TdApi.GetPasswordState()).toState()

    /**
     * Sets, changes or (with an empty [newPassword]) removes the cloud password. [oldPassword] is
     * needed when one is set. A new [recoveryEmail] waits for a code, see [confirmRecoveryEmail].
     */
    suspend fun setPassword(oldPassword: String, newPassword: String, hint: String, recoveryEmail: String?): CloudPasswordState =
        client.query(TdApi.SetPassword(oldPassword, newPassword, hint, recoveryEmail != null, recoveryEmail.orEmpty())).toState()

    suspend fun confirmRecoveryEmail(code: String): CloudPasswordState = client.query(TdApi.CheckRecoveryEmailAddressCode(code)).toState()

    private fun TdApi.PasswordState.toState() = CloudPasswordState(
        hasPassword, passwordHint.orEmpty(), hasRecoveryEmailAddress, recoveryEmailAddressCodeInfo?.emailAddressPattern
    )

    // ---------- Notification exceptions ----------

    suspend fun notificationExceptions(): List<NotificationException> {
        val ids = client.query(TdApi.GetChatNotificationSettingsExceptions(null, false)).chatIds
        return ids.mapNotNull { id ->
            runCatching { client.query(TdApi.GetChat(id)) }.getOrNull()?.let { chat ->
                NotificationException(chat.id, chat.title, chat.notificationSettings.muteFor > 0)
            }
        }
    }

    // ---------- Proxy ----------

    suspend fun proxies(): List<TelegramProxy> = client.query(TdApi.GetProxies()).proxies.map { added ->
        TelegramProxy(
            added.id, added.proxy.server, added.proxy.port,
            when (added.proxy.type) { is TdApi.ProxyTypeHttp -> ProxyKind.Http; is TdApi.ProxyTypeMtproto -> ProxyKind.MtProto; else -> ProxyKind.Socks5 },
            added.isEnabled
        )
    }

    /** Adds and enables a proxy; [secret] is the MTProto secret or "user:password" for SOCKS5/HTTP. */
    suspend fun addProxy(server: String, port: Int, kind: ProxyKind, secret: String) {
        val user = secret.substringBefore(':', secret)
        val pass = secret.substringAfter(':', "")
        val type: TdApi.ProxyType = when (kind) {
            ProxyKind.Socks5 -> TdApi.ProxyTypeSocks5(user, pass)
            ProxyKind.Http -> TdApi.ProxyTypeHttp(user, pass, false)
            ProxyKind.MtProto -> TdApi.ProxyTypeMtproto(secret)
        }
        client.query(TdApi.AddProxy(TdApi.Proxy(server, port, type), true, ""))
    }

    suspend fun enableProxy(id: Int) { client.query(TdApi.EnableProxy(id)) }
    suspend fun disableProxy() { client.query(TdApi.DisableProxy()) }
    suspend fun removeProxy(id: Int) { client.query(TdApi.RemoveProxy(id)) }

}
