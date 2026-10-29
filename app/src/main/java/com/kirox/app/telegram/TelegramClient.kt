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
    // A bug in one update handler must not take the whole app down with it: it is logged and shown.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1) +
            kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
                com.kirox.app.CrashLog.record(appContext, "Ошибка в обработчике TDLib", error)
                fail("Внутренняя ошибка: ${error.javaClass.simpleName}")
            }
    )
    private val updateHandlers = CopyOnWriteArrayList<(TdApi.Object) -> Unit>()
    private val failureHandlers = CopyOnWriteArrayList<(String) -> Unit>()
    @Volatile private var client: Client? = null
    private var pendingDatabaseEncryption = false
    /** Keys to try for this database, in order (see [prepareKeys]); dispatcher-confined. */
    private val keyCandidates = ArrayDeque<Pair<ByteArray, KeyKind>>()
    private var usedKey: KeyKind? = null
    /** The stored key is known lost: if no other key opens the database, it is unreadable. */
    private var storedKeyLost = false
    private var waitingParameters = false
    private var keyRetries = 0

    private enum class KeyKind { Stored, Pending, Legacy, Fresh }

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
        _blocked.value = null
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
        // Only a slot that was never signed in: the phone screen also shows while a real account starts.
        if (authorized || !isAddingAccount()) return
        val abandoned = session.name
        val back = accounts.accounts.value.firstOrNull { it.session != abandoned } ?: return
        accounts.remove(abandoned)
        discardOnClose = abandoned
        switchTo(back.session)
    }

    /** The open slot is a new account whose sign-in is not finished (it can be cancelled). */
    fun isAddingAccount(): Boolean = accounts.isPending(session.name)

    private fun dispatch(update: TdApi.Object) {
        if (update is TdApi.UpdateAuthorizationState) handleLifecycle(update.authorizationState)
        updateHandlers.forEach { runCatching { it(update) }.onFailure { error -> com.kirox.app.CrashLog.record(appContext, "Ошибка в обработчике обновлений", error) } }
        if (update is TdApi.UpdateAuthorizationState && update.authorizationState is TdApi.AuthorizationStateClosed) onClosed()
    }

    /**
     * Same lock as [start]: a start from a push or the UI cannot slip in between and open a database
     * that is about to be deleted, or have its lock released under it.
     */
    @Synchronized
    private fun onClosed() {
        val closed = session
        discardOnClose?.let { if (it != closed.name) TelegramSessionManager(appContext, it).reset() }
        discardOnClose = null
        // A logged-out extra account leaves the list; the next one opens instead of an empty sign-in.
        // A new slot being signed into stays: "change number" leads back to its own phone screen.
        val removeClosed = loggingOut && accounts.accounts.value.size > 1 && !accounts.isPending(closed.name)
        if (removeClosed) {
            accounts.remove(closed.name)
            closed.reset()
            accounts.active = accounts.accounts.value.first().session
        }
        loggingOut = false
        client = null
        SessionLocks.release(closed.name)
        if (!paused) start()
    }

    private fun fail(message: String) = failureHandlers.forEach { it(message) }

    private fun handleLifecycle(state: TdApi.AuthorizationState) {
        authorized = state is TdApi.AuthorizationStateReady
        _ready.value = authorized
        waitingParameters = state is TdApi.AuthorizationStateWaitTdlibParameters
        if (state is TdApi.AuthorizationStateLoggingOut) loggingOut = true
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                // The first update can arrive before Client.create returned in start(): wait for it,
                // or the parameters would be sent to no client and TDLib would wait forever.
                synchronized(this) {}
                keyRetries = 0
                if (prepareKeysSafely()) sendParameters()
            }
            is TdApi.AuthorizationStateReady -> {
                accounts.confirm(session.name)
                when (usedKey) {
                    KeyKind.Pending -> runCatching { session.keyStore.promotePending() }
                    KeyKind.Stored, KeyKind.Fresh -> session.keyStore.dropPending()
                    else -> Unit
                }
                usedKey = null
                encryptLegacyDatabase()
            }
        }
    }

    /**
     * Keys that may open this database, most likely first: the stored key, the key of a re-encryption
     * that may or may not have finished, and no key for a database from before encryption.
     * Throws on a temporary Keystore failure; nothing is deleted for that.
     */
    private fun prepareKeys() {
        keyCandidates.clear()
        pendingDatabaseEncryption = false
        usedKey = null
        val keyStore = session.keyStore
        storedKeyLost = false
        val stored = try { keyStore.load() } catch (_: TdlibKeyStore.KeyLostException) { storedKeyLost = true; null }
        stored?.let { keyCandidates += it to KeyKind.Stored }
        keyStore.loadPending()?.let { keyCandidates += it to KeyKind.Pending }
        if (session.hasDatabase) {
            // Without a stored key file the database may still be unencrypted (older versions).
            if (!keyStore.exists || storedKeyLost) keyCandidates += byteArrayOf() to KeyKind.Legacy
        } else if (stored == null) {
            // Nothing to open: a new database with a new key.
            if (storedKeyLost) session.reset()
            keyCandidates.clear()
            keyCandidates += keyStore.generate().also(keyStore::save) to KeyKind.Fresh
        }
        if (keyCandidates.isEmpty()) {
            // The stored key is gone and nothing else fits: the database can never be read again.
            session.reset()
            keyCandidates += keyStore.generate().also(keyStore::save) to KeyKind.Fresh
        }
    }

    /** [prepareKeys], retried a few times on a temporary Keystore failure. */
    private fun prepareKeysSafely(): Boolean = try {
        prepareKeys()
        true
    } catch (error: Exception) {
        _blocked.value = "Не удалось открыть защищённое хранилище: ${error.javaClass.simpleName}. Повторяем…"
        if (keyRetries++ < 5) post(3_000) { if (waitingParameters && client != null && prepareKeysSafely()) { _blocked.value = null; sendParameters() } }
        else _blocked.value = "Не удалось открыть защищённое хранилище: ${error.javaClass.simpleName}. Перезапустите приложение."
        false
    }

    /** A key that did not open the database: the next candidate is tried. */
    private fun isKeyError(error: TdApi.Error): Boolean =
        error.message.contains("encryption key", ignoreCase = true) || error.message.contains("wrong key", ignoreCase = true) ||
            error.message.contains("Wrong password", ignoreCase = true)

    private fun encryptLegacyDatabase() {
        if (!pendingDatabaseEncryption) return
        pendingDatabaseEncryption = false
        val keyStore = session.keyStore
        val key = keyStore.generate()
        // Saved as pending first: if the app dies after TDLib switched keys, the next start still has it.
        if (runCatching { keyStore.savePending(key) }.isFailure) return
        send(TdApi.SetDatabaseEncryptionKey(key)) { result ->
            if (result is TdApi.Ok) runCatching { keyStore.promotePending() } else keyStore.dropPending()
        }
    }

    private fun sendParameters() {
        val (key, kind) = keyCandidates.removeFirstOrNull() ?: return
        usedKey = kind
        pendingDatabaseEncryption = kind == KeyKind.Legacy
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
        send(parameters) { result ->
            if (result !is TdApi.Error) return@send
            when {
                keyCandidates.isNotEmpty() && isKeyError(result) -> sendParameters()
                isKeyError(result) && storedKeyLost -> {
                    // No key opens it and the stored one is gone for good: start over with an empty session.
                    session.reset()
                    keyCandidates.clear()
                    keyCandidates += session.keyStore.generate().also(session.keyStore::save) to KeyKind.Fresh
                    sendParameters()
                }
                else -> {
                    _blocked.value = humanError(result)
                    fail(humanError(result))
                }
            }
        }
    }
}
