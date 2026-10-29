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
    private var myBirthday = ""
    private var contactsLoaded = false
    private var publishScheduled = false

    private val _profile = MutableStateFlow<TelegramUserProfile?>(null)
    val profile: StateFlow<TelegramUserProfile?> = _profile.asStateFlow()
    private val _contacts = MutableStateFlow<List<TelegramContact>>(emptyList())
    val contacts: StateFlow<List<TelegramContact>> = _contacts.asStateFlow()
    private val _contactsLoading = MutableStateFlow(false)
    val contactsLoading: StateFlow<Boolean> = _contactsLoading.asStateFlow()

    init { client.addUpdateHandler(::handle) }

    /** Dispatcher-only lookup for other repositories. */
    internal fun user(id: Long): TdApi.User? = users[id]
    internal fun myId(): Long = myId

    private fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                is TdApi.AuthorizationStateReady -> load()
                // A background pause keeps profile and contacts; Ready reloads them.
                is TdApi.AuthorizationStateClosed -> if (!client.paused) {
                    users.clear(); myId = 0; myBio = ""; myBirthday = ""; contactsLoaded = false
                    _profile.value = null; _contacts.value = emptyList()
                }
            }
            is TdApi.UpdateUser -> { users[update.user.id] = update.user; schedulePublish() }
            is TdApi.UpdateUserStatus -> users[update.userId]?.let { it.status = update.status; schedulePublish() }
            is TdApi.UpdateUserFullInfo -> if (update.userId == myId) { applyFullInfo(update.userFullInfo); schedulePublish() }
        }
    }

    private fun load() {
        client.send(TdApi.GetMe()) { result ->
            if (result !is TdApi.User) return@send
            myId = result.id
            users[result.id] = result
            client.send(TdApi.GetUserFullInfo(result.id)) { info ->
                if (info is TdApi.UserFullInfo) applyFullInfo(info)
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
            users[myId]?.let { _profile.value = it.toProfile(myBio, myBirthday) }
            if (contactsLoaded) {
                _contacts.value = users.values.filter { it.isContact && it.id != myId }.map { it.toContact() }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
        }
    }

    private fun applyFullInfo(info: TdApi.UserFullInfo) {
        myBio = info.bio?.text.orEmpty()
        myBirthday = info.birthdate?.let { formatBirthday(it.day, it.month, it.year) }.orEmpty()
    }

    /** Id of the signed-in user's own chat (Saved Messages), creating it locally if needed. */
    suspend fun savedMessagesChatId(): Long {
        val me = client.query(TdApi.GetMe())
        return client.query(TdApi.CreatePrivateChat(me.id, false)).id
    }

    /**
     * Adds a contact by phone number. Returns the Telegram user id, or null when the number
     * is not registered in Telegram.
     */
    suspend fun addContact(phone: String, firstName: String, lastName: String): Long? {
        val contact = TdApi.ImportedContact(phone, firstName, lastName, null)
        val result = client.query(TdApi.ImportContacts(arrayOf(contact)))
        return result.userIds.firstOrNull()?.takeIf { it != 0L }
    }

    /** Saves only what changed; TDLib then sends UpdateUser / UpdateUserFullInfo and the profile refreshes. */
    suspend fun updateProfile(first: String, last: String, bio: String, username: String, old: TelegramUserProfile?) {
        if (first != old?.firstName || last != old.lastName) client.query(TdApi.SetName(first, last))
        if (bio != old?.bio) client.query(TdApi.SetBio(bio))
        if (username != old?.username.orEmpty()) client.query(TdApi.SetUsername(username))
        client.query(TdApi.GetUserFullInfo(myId)).let { info -> client.post { applyFullInfo(info); schedulePublish() } }
    }

    suspend fun setProfilePhoto(path: String) {
        client.query(TdApi.SetProfilePhoto(TdApi.InputChatPhotoStatic(TdApi.InputFileLocal(path)), false))
    }

    private companion object { const val PUBLISH_DELAY_MS = 50L }
}

internal fun TdApi.User.displayName(): String =
    listOf(firstName, lastName).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "Удалённый аккаунт" }

internal fun TdApi.User.toContact() = TelegramContact(
    id, displayName(), usernames?.activeUsernames?.firstOrNull(), status.toStatus(), profilePhoto?.small?.toRef(), phoneNumber.orEmpty()
)

internal fun TdApi.User.toProfile(bio: String, birthday: String = "") = TelegramUserProfile(
    id, displayName(), usernames?.activeUsernames?.firstOrNull(), phoneNumber.orEmpty(), bio, profilePhoto?.small?.toRef(),
    isPremium = isPremium, isVerified = verificationStatus?.isVerified == true, birthday = birthday,
    firstName = firstName.orEmpty(), lastName = lastName.orEmpty()
)

internal fun formatBirthday(day: Int, month: Int, year: Int): String =
    if (day <= 0 || month <= 0) "" else if (year > 0) "%d.%02d.%d".format(day, month, year) else "%d.%02d".format(day, month)

internal fun TdApi.UserStatus?.toStatus(): TelegramUserStatus = when (this) {
    is TdApi.UserStatusOnline -> TelegramUserStatus.Online
    is TdApi.UserStatusOffline -> TelegramUserStatus.Offline(wasOnline)
    is TdApi.UserStatusRecently -> TelegramUserStatus.Recently
    is TdApi.UserStatusLastWeek -> TelegramUserStatus.LastWeek
    is TdApi.UserStatusLastMonth -> TelegramUserStatus.LastMonth
    else -> TelegramUserStatus.LongAgo
}
