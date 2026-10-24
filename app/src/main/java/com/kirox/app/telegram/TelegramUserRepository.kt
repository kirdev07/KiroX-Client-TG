package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Users cache, the contact list and the signed-in user's profile. Dispatcher-confined like chats. */
class TelegramUserRepository(private val client: TelegramClient) {
    private val users = HashMap<Long, TdApi.User>()
    private var myId = 0L
    private var myBio = ""
    private var contactsLoaded = false
    private var publishScheduled = false

    private val _profile = MutableStateFlow<TelegramUserProfile?>(null)
    val profile: StateFlow<TelegramUserProfile?> = _profile.asStateFlow()
    private val _contacts = MutableStateFlow<List<TelegramContact>>(emptyList())
    val contacts: StateFlow<List<TelegramContact>> = _contacts.asStateFlow()
    private val _contactsLoading = MutableStateFlow(false)
    val contactsLoading: StateFlow<Boolean> = _contactsLoading.asStateFlow()

    init { client.addUpdateHandler(::handle) }

    private fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                is TdApi.AuthorizationStateReady -> load()
                is TdApi.AuthorizationStateClosed -> {
                    users.clear(); myId = 0; myBio = ""; contactsLoaded = false
                    _profile.value = null; _contacts.value = emptyList()
                }
            }
            is TdApi.UpdateUser -> { users[update.user.id] = update.user; schedulePublish() }
            is TdApi.UpdateUserStatus -> users[update.userId]?.let { it.status = update.status; schedulePublish() }
            is TdApi.UpdateUserFullInfo -> if (update.userId == myId) { myBio = update.userFullInfo.bio?.text.orEmpty(); schedulePublish() }
        }
    }

    private fun load() {
        client.send(TdApi.GetMe()) { result ->
            if (result !is TdApi.User) return@send
            myId = result.id
            users[result.id] = result
            client.send(TdApi.GetUserFullInfo(result.id)) { info ->
                if (info is TdApi.UserFullInfo) myBio = info.bio?.text.orEmpty()
                schedulePublish()
            }
            schedulePublish()
        }
        _contactsLoading.value = true
        client.send(TdApi.GetContacts()) { result ->
            // TDLib sends UpdateUser for every returned id before this result, so the cache is filled.
            contactsLoaded = true
            _contactsLoading.value = false
            schedulePublish()
        }
    }

    private fun schedulePublish() {
        if (publishScheduled) return
        publishScheduled = true
        client.post(PUBLISH_DELAY_MS) {
            publishScheduled = false
            users[myId]?.let { _profile.value = it.toProfile(myBio) }
            if (contactsLoaded) {
                _contacts.value = users.values.filter { it.isContact && it.id != myId }.map { it.toContact() }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
        }
    }

    private companion object { const val PUBLISH_DELAY_MS = 50L }
}

internal fun TdApi.User.displayName(): String =
    listOf(firstName, lastName).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "Удалённый аккаунт" }

internal fun TdApi.User.toContact() = TelegramContact(
    id, displayName(), usernames?.activeUsernames?.firstOrNull(), status.toStatus(), profilePhoto?.small?.toRef()
)

internal fun TdApi.User.toProfile(bio: String) = TelegramUserProfile(
    id, displayName(), usernames?.activeUsernames?.firstOrNull(), phoneNumber.orEmpty(), bio, profilePhoto?.small?.toRef()
)

internal fun TdApi.UserStatus?.toStatus(): TelegramUserStatus = when (this) {
    is TdApi.UserStatusOnline -> TelegramUserStatus.Online
    is TdApi.UserStatusOffline -> TelegramUserStatus.Offline(wasOnline)
    is TdApi.UserStatusRecently -> TelegramUserStatus.Recently
    is TdApi.UserStatusLastWeek -> TelegramUserStatus.LastWeek
    is TdApi.UserStatusLastMonth -> TelegramUserStatus.LastMonth
    else -> TelegramUserStatus.LongAgo
}
