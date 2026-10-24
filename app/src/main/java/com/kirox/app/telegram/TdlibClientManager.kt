package com.kirox.app.telegram

import android.content.Context
import android.os.Build
import com.kirox.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TdlibClientManager(context: Context) {
    private val appContext = context.applicationContext
    private val session = TelegramSessionManager(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var client: Client? = null
    private val chatsById = ConcurrentHashMap<Long, TdApi.Chat>()

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Initializing)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    private val _mainChats = MutableStateFlow<List<TelegramChatItem>>(emptyList())
    val mainChats: StateFlow<List<TelegramChatItem>> = _mainChats.asStateFlow()
    private val _archiveChats = MutableStateFlow<List<TelegramChatItem>>(emptyList())
    val archiveChats: StateFlow<List<TelegramChatItem>> = _archiveChats.asStateFlow()
    private val _profile = MutableStateFlow<TelegramUserProfile?>(null)
    val profile: StateFlow<TelegramUserProfile?> = _profile.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start() {
        if (client != null) return
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _authState.value = TelegramAuthState.Blocked("API_ID и API_HASH не настроены в kirox.properties")
            return
        }
        try {
            Client.setLogMessageHandler(1) { _, _ -> /* Never persist TDLib logs in the app. */ }
            client = Client.create(
                { update -> scope.launch { handleUpdate(update) } },
                { throwable -> scope.launch { setFailure(throwable.message ?: "Ошибка TDLib") } },
                { throwable -> scope.launch { setFailure(throwable.message ?: "Ошибка TDLib") } }
            )
        } catch (error: Throwable) {
            _authState.value = TelegramAuthState.Blocked("Native TDLib не загрузилась: ${error.javaClass.simpleName}")
        }
    }

    fun submitPhone(number: String) = sendAuth(TdApi.SetAuthenticationPhoneNumber().apply {
        phoneNumber = number
        settings = TdApi.PhoneNumberAuthenticationSettings()
    })

    fun submitCode(code: String) = sendAuth(TdApi.CheckAuthenticationCode(code))
    fun submitPassword(password: String) = sendAuth(TdApi.CheckAuthenticationPassword(password))
    fun submitEmail(email: String) = sendAuth(TdApi.SetAuthenticationEmailAddress(email))
    fun submitEmailCode(code: String) = sendAuth(TdApi.CheckAuthenticationEmailCode().apply {
        this.code = TdApi.EmailAddressAuthenticationCode(code)
    })
    fun register(firstName: String, lastName: String) = sendAuth(TdApi.RegisterUser(firstName, lastName, false))
    fun resendCode() = sendAuth(TdApi.ResendAuthenticationCode(null))
    fun clearError() { _error.value = null }

    private fun sendAuth(function: TdApi.Function<*>) {
        if (_busy.value) return
        val activeClient = client ?: return setFailure("TDLib ещё не готова")
        _busy.value = true
        _error.value = null
        activeClient.send(function) { result ->
            scope.launch {
                _busy.value = false
                if (result is TdApi.Error) setFailure(humanError(result))
            }
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> applyAuthorizationState(update.authorizationState)
            is TdApi.UpdateNewChat -> { chatsById[update.chat.id] = update.chat; publishChats() }
            is TdApi.UpdateChatTitle -> mutateChat(update.chatId) { it.title = update.title }
            is TdApi.UpdateChatPhoto -> mutateChat(update.chatId) { it.photo = update.photo }
            is TdApi.UpdateChatLastMessage -> mutateChat(update.chatId) { chat ->
                chat.lastMessage = update.lastMessage
                chat.positions = update.positions
            }
            is TdApi.UpdateChatPosition -> mutateChat(update.chatId) { chat ->
                chat.positions = chat.positions.filterNot { sameList(it.list, update.position.list) }.toTypedArray() + update.position
            }
            is TdApi.UpdateChatReadInbox -> mutateChat(update.chatId) { it.unreadCount = update.unreadCount }
        }
    }

    private fun applyAuthorizationState(state: TdApi.AuthorizationState) {
        _busy.value = false
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramAuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> _authState.value = TelegramAuthState.WaitCode(
                state.codeInfo.phoneNumber.orEmpty(), codeLength(state.codeInfo.type), state.codeInfo.nextType != null, state.codeInfo.timeout
            )
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = TelegramAuthState.WaitPassword(state.passwordHint.orEmpty())
            is TdApi.AuthorizationStateWaitEmailAddress -> _authState.value = TelegramAuthState.WaitEmailAddress
            is TdApi.AuthorizationStateWaitEmailCode -> _authState.value = TelegramAuthState.WaitEmailCode(state.codeInfo.length)
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> _authState.value = TelegramAuthState.WaitOtherDevice(state.link)
            is TdApi.AuthorizationStateWaitRegistration -> _authState.value = TelegramAuthState.WaitRegistration
            is TdApi.AuthorizationStateReady -> {
                _authState.value = TelegramAuthState.Ready
                loadAccount()
            }
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> _authState.value = TelegramAuthState.Closing
            is TdApi.AuthorizationStateClosed -> {
                client = null
                chatsById.clear()
                publishChats()
                _authState.value = TelegramAuthState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitPremiumPurchase -> _authState.value = TelegramAuthState.Blocked("Telegram требует завершить покупку Premium в официальном приложении")
        }
    }

    private fun sendParameters() {
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = session.databaseDirectory
            filesDirectory = session.filesDirectory
            databaseEncryptionKey = byteArrayOf()
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = true
            apiId = BuildConfig.TELEGRAM_API_ID
            apiHash = BuildConfig.TELEGRAM_API_HASH
            systemLanguageCode = Locale.getDefault().toLanguageTag().ifBlank { "ru" }
            deviceModel = Build.MODEL.ifBlank { "Android" }
            systemVersion = Build.VERSION.RELEASE.orEmpty()
            applicationVersion = BuildConfig.VERSION_NAME
        }
        sendAuth(parameters)
    }

    private fun loadAccount() {
        val activeClient = client ?: return
        activeClient.send(TdApi.GetMe()) { result ->
            scope.launch {
                if (result is TdApi.User) _profile.value = TelegramUserProfile(
                    result.id,
                    listOf(result.firstName, result.lastName).filter { it.isNotBlank() }.joinToString(" "),
                    result.usernames?.activeUsernames?.firstOrNull(),
                    result.phoneNumber,
                    result.profilePhoto?.small?.local?.path?.takeIf { it.isNotBlank() }
                )
            }
        }
        activeClient.send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) { result -> if (result is TdApi.Error && result.code != 404) scope.launch { setFailure(humanError(result)) } }
        activeClient.send(TdApi.LoadChats(TdApi.ChatListArchive(), 100)) { result -> if (result is TdApi.Error && result.code != 404) scope.launch { setFailure(humanError(result)) } }
    }

    private fun mutateChat(id: Long, block: (TdApi.Chat) -> Unit) {
        chatsById[id]?.let { block(it); publishChats() }
    }

    private fun publishChats() {
        _mainChats.value = mappedChats(false)
        _archiveChats.value = mappedChats(true)
    }

    private fun mappedChats(archive: Boolean): List<TelegramChatItem> = chatsById.values.mapNotNull { chat ->
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

    private fun sameList(a: TdApi.ChatList, b: TdApi.ChatList): Boolean = a.javaClass == b.javaClass
    private fun codeLength(type: TdApi.AuthenticationCodeType): Int = when (type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length
        is TdApi.AuthenticationCodeTypeSms -> type.length
        is TdApi.AuthenticationCodeTypeCall -> type.length
        is TdApi.AuthenticationCodeTypeFragment -> type.length
        else -> 0
    }
    private fun setFailure(message: String) { _busy.value = false; _error.value = message }
    private fun humanError(error: TdApi.Error): String = when (error.message) {
        "PHONE_CODE_INVALID" -> "Неверный код подтверждения"
        "PHONE_NUMBER_INVALID" -> "Неверный номер телефона"
        "PASSWORD_HASH_INVALID" -> "Неверный пароль"
        else -> if (error.code >= 500) "Telegram временно недоступен" else error.message.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
    }
}

