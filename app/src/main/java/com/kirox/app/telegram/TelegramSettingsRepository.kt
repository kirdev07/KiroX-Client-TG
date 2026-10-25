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
    ChatInvites("Приглашения в группы");

    internal fun toTd(): TdApi.UserPrivacySetting = when (this) {
        PhoneNumber -> TdApi.UserPrivacySettingShowPhoneNumber()
        LastSeen -> TdApi.UserPrivacySettingShowStatus()
        ProfilePhoto -> TdApi.UserPrivacySettingShowProfilePhoto()
        Calls -> TdApi.UserPrivacySettingAllowCalls()
        ChatInvites -> TdApi.UserPrivacySettingAllowChatInvites()
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
}
