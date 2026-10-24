package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Authorization state machine. [revision] grows on every TDLib auth update, even when the state is equal. */
class TelegramAuthRepository(private val client: TelegramClient) {
    private val _state = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Initializing)
    val state: StateFlow<TelegramAuthState> = _state.asStateFlow()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    val blocked: StateFlow<String?> = client.blocked
    private val _recoveryCodeSent = MutableStateFlow(false)
    /** True after Telegram emailed a 2FA recovery code; reset whenever the auth state changes. */
    val recoveryCodeSent: StateFlow<Boolean> = _recoveryCodeSent.asStateFlow()

    init {
        client.addUpdateHandler { update -> if (update is TdApi.UpdateAuthorizationState) apply(update.authorizationState) }
        client.addFailureHandler(::setFailure)
    }

    fun phone(value: String) = send(TdApi.SetAuthenticationPhoneNumber().apply {
        phoneNumber = value
        settings = TdApi.PhoneNumberAuthenticationSettings()
    })
    fun code(value: String) = send(TdApi.CheckAuthenticationCode(value))
    fun password(value: String) = send(TdApi.CheckAuthenticationPassword(value))
    fun email(value: String) = send(TdApi.SetAuthenticationEmailAddress(value))
    fun emailCode(value: String) = send(TdApi.CheckAuthenticationEmailCode().apply { code = TdApi.EmailAddressAuthenticationCode(value) })
    fun register(first: String, last: String) = send(TdApi.RegisterUser(first, last, false))
    fun resend() = send(TdApi.ResendAuthenticationCode(null))
    fun requestQr() = send(TdApi.RequestQrCodeAuthentication(LongArray(0)))
    fun requestPasswordRecovery() = send(TdApi.RequestAuthenticationPasswordRecovery()) { _recoveryCodeSent.value = true }
    fun recoverPassword(code: String, newPassword: String, newHint: String) = send(TdApi.RecoverAuthenticationPassword(code, newPassword, newHint))
    fun cancelPasswordRecovery() { _recoveryCodeSent.value = false; clearError() }
    /** Logs out a signed-in account, or abandons an unfinished login (e.g. QR) so a fresh one can start. */
    fun logOut() = send(TdApi.LogOut())
    fun clearError() { _error.value = null }

    private fun send(function: TdApi.Function<*>, onOk: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        val sent = client.send(function) { result ->
            _busy.value = false
            if (result is TdApi.Error) setFailure(humanError(result)) else onOk()
        }
        if (!sent) setFailure("TDLib ещё не готова")
    }

    private fun setFailure(message: String) { _busy.value = false; _error.value = message }

    private fun apply(state: TdApi.AuthorizationState) {
        _state.value = when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> TelegramAuthState.Initializing
            is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> TelegramAuthState.WaitCode(
                state.codeInfo.phoneNumber.orEmpty(), codeLength(state.codeInfo.type), state.codeInfo.nextType != null, state.codeInfo.timeout
            )
            is TdApi.AuthorizationStateWaitPassword -> TelegramAuthState.WaitPassword(
                state.passwordHint.orEmpty(), state.hasRecoveryEmailAddress, state.recoveryEmailAddressPattern.orEmpty()
            )
            is TdApi.AuthorizationStateWaitEmailAddress -> TelegramAuthState.WaitEmailAddress
            is TdApi.AuthorizationStateWaitEmailCode -> TelegramAuthState.WaitEmailCode(state.codeInfo.length)
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> TelegramAuthState.WaitOtherDevice(state.link)
            is TdApi.AuthorizationStateWaitRegistration -> TelegramAuthState.WaitRegistration(
                state.termsOfService?.text?.text.orEmpty(), state.termsOfService?.minUserAge ?: 0
            )
            is TdApi.AuthorizationStateReady -> TelegramAuthState.Ready
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> TelegramAuthState.Closing
            is TdApi.AuthorizationStateClosed -> TelegramAuthState.Initializing
            is TdApi.AuthorizationStateWaitPremiumPurchase -> TelegramAuthState.Blocked("Telegram требует завершить покупку Premium в официальном приложении")
            else -> _state.value
        }
        if (state !is TdApi.AuthorizationStateWaitPassword) _recoveryCodeSent.value = false
        _revision.value++
    }

    private fun codeLength(type: TdApi.AuthenticationCodeType): Int = when (type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length
        is TdApi.AuthenticationCodeTypeSms -> type.length
        is TdApi.AuthenticationCodeTypeCall -> type.length
        is TdApi.AuthenticationCodeTypeFragment -> type.length
        else -> 0
    }
}

/** Main and archive chat lists built from TDLib updates. */
class TelegramChatRepository(private val client: TelegramClient) {
    private val chatsById = HashMap<Long, TdApi.Chat>() // touched only on the TDLib dispatcher
    private val _mainChats = MutableStateFlow<List<TelegramChatItem>>(emptyList())
    val mainChats: StateFlow<List<TelegramChatItem>> = _mainChats.asStateFlow()
    private val _archiveChats = MutableStateFlow<List<TelegramChatItem>>(emptyList())
    val archiveChats: StateFlow<List<TelegramChatItem>> = _archiveChats.asStateFlow()

    init { client.addUpdateHandler(::handle) }

    private fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                is TdApi.AuthorizationStateReady -> {
                    load(TdApi.ChatListMain())
                    load(TdApi.ChatListArchive())
                }
                is TdApi.AuthorizationStateClosed -> { chatsById.clear(); publish() }
            }
            is TdApi.UpdateNewChat -> { chatsById[update.chat.id] = update.chat; publish() }
            is TdApi.UpdateChatTitle -> mutate(update.chatId) { it.title = update.title }
            is TdApi.UpdateChatPhoto -> mutate(update.chatId) { it.photo = update.photo }
            is TdApi.UpdateChatLastMessage -> mutate(update.chatId) { chat ->
                chat.lastMessage = update.lastMessage
                chat.positions = update.positions
            }
            is TdApi.UpdateChatPosition -> mutate(update.chatId) { it.positions = applyChatPosition(it.positions, update.position) }
            is TdApi.UpdateChatReadInbox -> mutate(update.chatId) { it.unreadCount = update.unreadCount }
        }
    }

    private fun load(list: TdApi.ChatList) {
        client.send(TdApi.LoadChats(list, 100)) // 404 means the list is fully loaded; other errors surface via the auth screen later.
    }

    private fun mutate(id: Long, block: (TdApi.Chat) -> Unit) {
        chatsById[id]?.let { block(it); publish() }
    }

    private fun publish() {
        _mainChats.value = mapped(archive = false)
        _archiveChats.value = mapped(archive = true)
    }

    private fun mapped(archive: Boolean): List<TelegramChatItem> = chatsById.values.mapNotNull { chat ->
        val position = chat.positions.firstOrNull { if (archive) it.list is TdApi.ChatListArchive else it.list is TdApi.ChatListMain }
            ?.takeIf { it.order != 0L } ?: return@mapNotNull null
        TelegramChatItem(
            chat.id, chat.title.orEmpty(), messagePreview(chat.lastMessage), chat.lastMessage?.date ?: 0,
            chat.unreadCount, position.isPinned, chat.photo?.small?.local?.path?.takeIf { it.isNotBlank() }, archive
        ) to position.order
    }.sortedWith(compareByDescending<Pair<TelegramChatItem, Long>> { it.second }.thenByDescending { it.first.id }).map { it.first }

    private fun messagePreview(message: TdApi.Message?): String = when (val content = message?.content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Фотография" }
        is TdApi.MessageVideo -> content.caption.text.ifBlank { "Видео" }
        is TdApi.MessageDocument -> content.caption.text.ifBlank { "Документ" }
        is TdApi.MessageVoiceNote -> content.caption.text.ifBlank { "Голосовое сообщение" }
        null -> ""
        else -> "Сообщение"
    }
}

/** The signed-in user's own profile. */
class TelegramUserRepository(private val client: TelegramClient) {
    private val _profile = MutableStateFlow<TelegramUserProfile?>(null)
    val profile: StateFlow<TelegramUserProfile?> = _profile.asStateFlow()

    init {
        client.addUpdateHandler { update ->
            if (update !is TdApi.UpdateAuthorizationState) return@addUpdateHandler
            when (update.authorizationState) {
                is TdApi.AuthorizationStateReady -> client.send(TdApi.GetMe()) { result -> if (result is TdApi.User) _profile.value = result.toProfile() }
                is TdApi.AuthorizationStateClosed -> _profile.value = null
            }
        }
    }

    private fun TdApi.User.toProfile() = TelegramUserProfile(
        id,
        listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
        usernames?.activeUsernames?.firstOrNull(),
        phoneNumber,
        profilePhoto?.small?.local?.path?.takeIf { it.isNotBlank() }
    )
}

/** Network state from TDLib, for the "Соединение…" indicator. */
class TelegramConnectionRepository(client: TelegramClient) {
    private val _state = MutableStateFlow(TelegramConnection.Connecting)
    val state: StateFlow<TelegramConnection> = _state.asStateFlow()

    init {
        client.addUpdateHandler { update ->
            if (update is TdApi.UpdateConnectionState) _state.value = when (update.state) {
                is TdApi.ConnectionStateWaitingForNetwork -> TelegramConnection.WaitingForNetwork
                is TdApi.ConnectionStateConnectingToProxy -> TelegramConnection.ConnectingToProxy
                is TdApi.ConnectionStateConnecting -> TelegramConnection.Connecting
                is TdApi.ConnectionStateUpdating -> TelegramConnection.Updating
                else -> TelegramConnection.Ready
            }
        }
    }
}

/** Process-wide Telegram graph: one client, one instance of each repository. */
class TelegramServices(context: android.content.Context) {
    val client = TelegramClient(context)
    val auth = TelegramAuthRepository(client)
    val chats = TelegramChatRepository(client)
    val users = TelegramUserRepository(client)
    val connection = TelegramConnectionRepository(client)
}
