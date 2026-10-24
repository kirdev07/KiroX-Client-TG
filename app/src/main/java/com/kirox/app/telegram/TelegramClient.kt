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
    private val session = TelegramSessionManager(context.applicationContext)
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

    fun start() {
        if (client != null) return
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _blocked.value = "API_ID и API_HASH не настроены в kirox.properties"
            return
        }
        try {
            Client.execute<TdApi.Ok>(TdApi.SetLogVerbosityLevel(1))
            Client.setLogMessageHandler(1) { _, _ -> /* Never persist TDLib logs in the app. */ }
            client = Client.create(
                { update -> scope.launch { dispatch(update) } },
                { throwable -> scope.launch { fail(throwable.message ?: "Ошибка TDLib") } },
                { throwable -> scope.launch { fail(throwable.message ?: "Ошибка TDLib") } }
            )
        } catch (error: Throwable) {
            _blocked.value = "Native TDLib не загрузилась: ${error.javaClass.simpleName}"
        }
    }

    /** Sends [function]; [onResult] runs on the TDLib dispatcher. Returns false when no client is running. */
    fun send(function: TdApi.Function<*>, onResult: (TdApi.Object) -> Unit = {}): Boolean {
        val activeClient = client ?: return false
        activeClient.send(function) { result -> scope.launch { onResult(result) } }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : TdApi.Object> query(function: TdApi.Function<T>): T = suspendCancellableCoroutine { continuation ->
        val sent = send(function) { result ->
            if (result is TdApi.Error) continuation.resumeWithException(TdException(result)) else continuation.resume(result as T)
        }
        if (!sent) continuation.resumeWithException(TdException(TdApi.Error(503, "TDLib ещё не готова")))
    }

    private fun dispatch(update: TdApi.Object) {
        if (update is TdApi.UpdateAuthorizationState) handleLifecycle(update.authorizationState)
        updateHandlers.forEach { it(update) }
        if (update is TdApi.UpdateAuthorizationState && update.authorizationState is TdApi.AuthorizationStateClosed) {
            client = null
            start()
        }
    }

    private fun fail(message: String) = failureHandlers.forEach { it(message) }

    private fun handleLifecycle(state: TdApi.AuthorizationState) {
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
            useTestDc = false
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

internal fun humanError(error: TdApi.Error): String = when (error.message) {
    "PHONE_CODE_INVALID" -> "Неверный код подтверждения"
    "PHONE_NUMBER_INVALID" -> "Неверный номер телефона"
    "PASSWORD_HASH_INVALID" -> "Неверный пароль"
    else -> if (error.code >= 500) "Telegram временно недоступен" else error.message.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
}
