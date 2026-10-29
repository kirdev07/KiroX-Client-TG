package com.kirox.app.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kirox.app.KiroXApplication
import com.kirox.app.telegram.TelegramClient
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import java.util.UUID

/**
 * Push wake-up so incoming calls and messages reach the app when it is not running.
 * Two channels are registered with Telegram: Firebase Cloud Messaging (when google-services.json
 * is configured) and UnifiedPush (when a distributor such as ntfy is installed). A push only
 * wakes TDLib; the call itself then arrives as a normal UpdateCall.
 */
class KiroxPush(private val context: Context, private val client: TelegramClient) {
    private val prefs = context.getSharedPreferences("kirox_push", Context.MODE_PRIVATE)
    private val peek = com.kirox.app.notify.AccountPeek(context)

    init {
        client.addUpdateHandler { update ->
            if (update is TdApi.UpdateAuthorizationState && update.authorizationState is TdApi.AuthorizationStateReady) registerAll()
        }
    }

    private fun registerAll() {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener(::registerFirebase) }
        }
        prefs.getString(KEY_ENDPOINT, null)?.let(::registerWebPush)
        UnifiedPush.register(context, unifiedToken())
    }

    /**
     * Telegram rejects FCM for this api_id (APP_PUSH_APIKEY_MISSING); after a rejection it is not
     * retried until the next app version.
     */
    fun registerFirebase(token: String) {
        if (prefs.getInt(KEY_FCM_REJECTED, 0) == com.kirox.app.BuildConfig.VERSION_CODE) return
        client.send(TdApi.RegisterDevice(TdApi.DeviceTokenFirebaseCloudMessaging(token, false), LongArray(0))) { result ->
            log("FCM register: $result")
            if (result is TdApi.Error && result.message.contains("APIKEY", ignoreCase = true)) {
                prefs.edit().putInt(KEY_FCM_REJECTED, com.kirox.app.BuildConfig.VERSION_CODE).apply()
            }
        }
    }

    fun onUnifiedEndpoint(endpoint: String) {
        if (prefs.getString(KEY_ENDPOINT, null) == endpoint) return
        prefs.edit().putString(KEY_ENDPOINT, endpoint).apply()
        registerWebPush(endpoint)
    }

    /** The distributor dropped us (uninstalled, user action): forget the endpoint so it is not re-registered. */
    fun onUnifiedUnregistered() {
        prefs.edit().remove(KEY_ENDPOINT).apply()
    }

    /**
     * The UnifiedPush endpoint is registered as a Web Push subscription. Telegram encrypts the payload
     * for these keys; the app does not need to read it, any push just wakes TDLib.
     */
    private fun registerWebPush(endpoint: String) {
        val (publicKey, auth) = webPushKeys()
        client.send(TdApi.RegisterDevice(TdApi.DeviceTokenWebPush(endpoint, publicKey, auth), LongArray(0))) { log("UnifiedPush register: $it") }
    }

    /** P-256 public key (uncompressed point) and auth secret, base64url, generated once. */
    private fun webPushKeys(): Pair<String, String> {
        val saved = prefs.getString(KEY_WEBPUSH_KEY, null) to prefs.getString(KEY_WEBPUSH_AUTH, null)
        val savedKey = saved.first
        val savedAuth = saved.second
        if (savedKey != null && savedAuth != null) return savedKey to savedAuth
        val generator = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }
        val point = (generator.generateKeyPair().public as java.security.interfaces.ECPublicKey).w
        fun coord(value: java.math.BigInteger) = value.toByteArray().takeLast(32).toByteArray().let { ByteArray(32 - it.size) + it }
        val uncompressed = byteArrayOf(4) + coord(point.affineX) + coord(point.affineY)
        val auth = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        val keys = android.util.Base64.encodeToString(uncompressed, flags) to android.util.Base64.encodeToString(auth, flags)
        prefs.edit().putString(KEY_WEBPUSH_KEY, keys.first).putString(KEY_WEBPUSH_AUTH, keys.second).apply()
        return keys
    }

    /**
     * Starts TDLib if the process was woken by a push, and hands it the payload when there is one.
     * [done] runs once TDLib had time to sync and show notifications, so the caller can let the
     * process go; Android may kill a background process right after a broadcast returns.
     */
    fun wake(payload: String?, done: () -> Unit = {}) {
        log("push received, payload=${payload?.length ?: 0} chars")
        client.start()
        if (!payload.isNullOrBlank()) client.send(TdApi.ProcessPushNotification(payload))
        // Other signed-in accounts get their notifications too (see AccountPeek).
        val others = client.accounts.accounts.value.filter { it.session != client.activeSession }
        if (others.isNotEmpty()) peek.peek(others, SYNC_WINDOW_MS - 500)
        client.post(SYNC_WINDOW_MS) { client.pauseIfIdle(); done() }
    }

    fun unifiedToken(): String = prefs.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_TOKEN, it).apply() }

    private fun log(message: String) { if (com.kirox.app.BuildConfig.DEBUG) android.util.Log.d("KiroxPush", message) }

    private companion object {
        const val KEY_TOKEN = "unified_token"
        const val KEY_ENDPOINT = "unified_endpoint"
        const val KEY_FCM_REJECTED = "fcm_rejected_version"
        const val KEY_WEBPUSH_KEY = "webpush_p256dh"
        const val KEY_WEBPUSH_AUTH = "webpush_auth"
        /** Broadcast receivers get about 10 s with goAsync(); TDLib connects and fetches within that. */
        const val SYNC_WINDOW_MS = 8_500L
    }
}

private val Context.push: KiroxPush get() = (applicationContext as KiroXApplication).push

/** Firebase Cloud Messaging entry point. */
class KiroxFirebaseService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = push.registerFirebase(token)

    override fun onMessageReceived(message: RemoteMessage) {
        // FCM allows about 20 s here; block until TDLib had its sync window.
        val latch = java.util.concurrent.CountDownLatch(1)
        push.wake(JSONObject(message.data as Map<*, *>).toString()) { latch.countDown() }
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
    }
}

/**
 * UnifiedPush connector, written against the open UnifiedPush Android spec: the app asks a
 * distributor for an endpoint and receives messages as broadcasts.
 */
object UnifiedPush {
    private const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"

    fun register(context: Context, token: String) {
        val distributor = context.packageManager.queryBroadcastReceivers(Intent(ACTION_REGISTER), 0)
            .map { it.activityInfo.packageName }.firstOrNull { it != context.packageName } ?: return
        // The PendingIntent lets the distributor verify which app is registering.
        val identity = PendingIntent.getBroadcast(context, 0, Intent("com.kirox.app.push.IDENTITY").setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
        context.sendBroadcast(
            Intent(ACTION_REGISTER).setPackage(distributor)
                .putExtra("token", token)
                .putExtra("application", context.packageName)
                .putExtra("pi", identity)
        )
    }
}

class UnifiedPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val push = context.push
        // The receiver is exported, so any app can send to it: only broadcasts carrying the secret
        // token we gave the distributor are trusted. Others could redirect pushes or drain the battery.
        if (intent.getStringExtra("token") != push.unifiedToken()) return
        when (intent.action) {
            "org.unifiedpush.android.connector.NEW_ENDPOINT" -> intent.getStringExtra("endpoint")
                ?.takeIf { it.startsWith("https://") }?.let(push::onUnifiedEndpoint)
            "org.unifiedpush.android.connector.UNREGISTERED" -> push.onUnifiedUnregistered()
            "org.unifiedpush.android.connector.MESSAGE" -> {
                // Keep the process alive while TDLib syncs; returning at once lets Android kill it.
                val pending = goAsync()
                push.wake(null) { pending.finish() }
            }
        }
    }
}
