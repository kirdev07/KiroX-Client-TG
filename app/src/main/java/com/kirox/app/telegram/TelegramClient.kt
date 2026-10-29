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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdException(val error: TdApi.Error) : Exception(error.message)

/**
 * Sole owner of the native TDLib client. Handles database parameters, encryption and
 * re-creation after `Closed`; feature repositories subscribe to its updates.
 * Updates, results and failures are all delivered on one single-threaded dispatcher, in order.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TelegramClient(context: Context) {
    private val appContext = context.applicationContext
    val accounts = TelegramAccounts(appContext)
    private var session = TelegramSessionManager(appContext, accounts.active)
    @Volatile private var authorized = false
    private val _ready = MutableStateFlow(false)
    /** True while an account is signed in and TDLib is running (AuthorizationStateReady). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private var loggingOut = false
    /**
     * True while TDLib is closed because the app is in the background. Android freezes cached
     * processes, and an open but frozen connection makes Telegram skip push notifications.
     */
    @Volatile var paused = false
        private set
    /** Set by the app: whether nothing needs TDLib right now (no screen, no call). */
    @Volatile var isIdle: () -> Boolean = { false }
    /** Slot of an abandoned sign-in, deleted once its client has closed. */
    private var discardOnClose: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val updateHandlers = CopyOnWriteArrayList<(TdApi.Object) -> Unit>()
    private val failureHandlers = CopyOnWriteArrayList<(String) -> Unit>()
    @Volatile private var client: Client? = null
    private var pendingDatabaseEncryption = false

    private val _blocked = MutableStateFlow<String?>(null)
    /** Non-null when TDLib cannot run at all (missing API keys, native library, storage). */
    val blocked: StateFlow<String?> = _blocked.asStateFlow()

    fun addUpdateHandler(handler: (TdApi.Object) -> Unit) { updateHandlers += handler }
    fun addFailureHandler(handler: (String) -> Unit) { failureHandlers += handler }

    /**
     * Starts TDLib once. Called from the UI, from push wake-ups and after a logout closes the client,
     * possibly at the same time: the lock keeps it to one client per database.
     */
    @Synchronized
    fun start() {
        paused = false
        if (client != null) return
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _blocked.value = "API_ID и API_HASH не настроены в kirox.properties"
            return
        }
        session = TelegramSessionManager(appContext, accounts.active)
        // One client per database: a notification peek may have this account open; let it close
        // first, then start again. The lock is released on Closed.
        if (!SessionLocks.acquire(session.name) {}) { SessionLocks.whenFree(session.name) { start() }; return }
        try {
            Client.execute<TdApi.Ok>(TdApi.SetLogVerbosityLevel(1))
            Client.setLogMessageHandler(1) { _, _ -> /* Never persist TDLib logs in the app. */ }
            client = Client.create(
                { update -> scope.launch { dispatch(update) } },
                { throwable -> scope.launch { fail(throwable.message ?: "Ошибка TDLib") } },
                { throwable -> scope.launch { fail(throwable.message ?: "Ошибка TDLib") } }
            )
        } catch (error: Throwable) {
            SessionLocks.release(session.name)
            _blocked.value = "Native TDLib не загрузилась: ${error.javaClass.simpleName}"
        }
    }

    /** Sends [function]; [onResult] runs on the TDLib dispatcher. Returns false when no client is running. */
    fun send(function: TdApi.Function<*>, onResult: (TdApi.Object) -> Unit = {}): Boolean {
        val activeClient = client ?: return false
        activeClient.send(function) { result -> scope.launch { onResult(result) } }
        return true
    }

    /** Runs [block] on the TDLib dispatcher after [delayMs]; repositories use it to coalesce work. */
    fun post(delayMs: Long = 0, block: () -> Unit) {
        scope.launch { if (delayMs > 0) kotlinx.coroutines.delay(delayMs); block() }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : TdApi.Object> query(function: TdApi.Function<T>): T = suspendCancellableCoroutine { continuation ->
        val sent = send(function) { result ->
            if (result is TdApi.Error) continuation.resumeWithException(TdException(result)) else continuation.resume(result as T)
        }
        if (!sent) continuation.resumeWithException(TdException(TdApi.Error(503, "TDLib ещё не готова")))
    }

    /** Closes TDLib while the app is idle in the background; the next [start] or push opens it again. */
    @Synchronized
    fun pauseIfIdle() {
        // Also before Ready: a half-open connection in a frozen process blocks pushes just the same.
        if (!isIdle() || client == null) return
        paused = true
        send(TdApi.Close())
    }

    /** Active account's session folder name. */
    val activeSession: String get() = session.name

    /** Closes the running account and opens [sessionName]; repositories reset on `Closed` as after a logout. */
    fun switchTo(sessionName: String) {
        if (sessionName == session.name && client != null) return
        accounts.active = sessionName
        if (!send(TdApi.Close())) start()
    }

    /** Opens a fresh slot, which starts at the phone number screen. */
    fun addAccount() = switchTo(accounts.create())

    /** Leaves a sign-in that was not finished and goes back to a signed-in account. */
    fun cancelNewAccount() {
        if (authorized) return
        val abandoned = session.name
        val back = accounts.accounts.value.firstOrNull { it.session != abandoned } ?: return
        accounts.remove(abandoned)
        discardOnClose = abandoned
        switchTo(back.session)
    }

    private fun dispatch(update: TdApi.Object) {
        if (update is TdApi.UpdateAuthorizationState) handleLifecycle(update.authorizationState)
        updateHandlers.forEach { it(update) }
        if (update is TdApi.UpdateAuthorizationState && update.authorizationState is TdApi.AuthorizationStateClosed) {
            client = null
            SessionLocks.release(session.name)
            discardOnClose?.let { TelegramSessionManager(appContext, it).reset() }
            discardOnClose = null
            // A logged-out extra account leaves the list; the next one opens instead of an empty sign-in.
            if (loggingOut && accounts.accounts.value.size > 1) {
                val gone = session.name
                accounts.remove(gone)
                session.reset()
                accounts.active = accounts.accounts.value.first().session
            }
            loggingOut = false
            if (!paused) start()
        }
    }

    private fun fail(message: String) = failureHandlers.forEach { it(message) }

    private fun handleLifecycle(state: TdApi.AuthorizationState) {
        authorized = state is TdApi.AuthorizationStateReady
        _ready.value = authorized
        if (state is TdApi.AuthorizationStateLoggingOut) loggingOut = true
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateReady -> encryptLegacyDatabase()
        }
    }

    /** Returns the key for SetTdlibParameters; an empty key only for a pre-encryption session. */
    private fun databaseKey(): ByteArray {
        pendingDatabaseEncryption = false
        val keyStore = session.keyStore
        keyStore.load()?.let { return it }
        if (keyStore.exists) session.reset() // Keystore lost the wrapping key: the database is unreadable.
        if (session.hasDatabase) {
            pendingDatabaseEncryption = true
            return byteArrayOf()
        }
        return keyStore.generate().also(keyStore::save)
    }

    private fun encryptLegacyDatabase() {
        if (!pendingDatabaseEncryption) return
        pendingDatabaseEncryption = false
        val key = session.keyStore.generate()
        send(TdApi.SetDatabaseEncryptionKey(key)) { result -> if (result is TdApi.Ok) session.keyStore.save(key) }
    }

    private fun sendParameters() {
        val key = try {
            databaseKey()
        } catch (error: Exception) {
            _blocked.value = "Не удалось подготовить защищённое хранилище: ${error.javaClass.simpleName}"
            return
        }
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = BuildConfig.TELEGRAM_USE_TEST_DC
            databaseDirectory = session.databaseDirectory
            filesDirectory = session.filesDirectory
            databaseEncryptionKey = key
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
        send(parameters) { result -> if (result is TdApi.Error) fail(humanError(result)) }
    }
}
