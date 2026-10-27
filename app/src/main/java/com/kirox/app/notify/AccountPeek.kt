package com.kirox.app.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kirox.app.BuildConfig
import com.kirox.app.MainActivity
import com.kirox.app.telegram.TelegramAccount
import com.kirox.app.telegram.TelegramSessionManager
import com.kirox.app.telegram.messagePreview
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.Locale

/**
 * Notifications of signed-in accounts that are not open right now. Telegram keeps pushing for every
 * account that registered this device; on a push KiroX briefly opens each inactive account's own
 * TDLib database (one client per account, never two for the same one), lets it sync, shows its new
 * messages labelled with the account, and closes it again. The open account is handled as before.
 */
class AccountPeek(context: Context) {
    private val appContext = context.applicationContext

    /** Peeks [accounts] (all but the open one) for [windowMs]; each closes itself afterwards. */
    fun peek(accounts: List<TelegramAccount>, windowMs: Long) {
        // With KiroX on screen the user may switch accounts at any moment; peeks are for the background.
        if (AppVisibility.visible) return
        accounts.forEach { start(it, windowMs) }
    }

    private fun start(account: TelegramAccount, windowMs: Long) {
        val session = TelegramSessionManager(appContext, account.session)
        // Only encrypted, signed-in sessions are peeked; anything else waits for the user.
        val key = session.keyStore.load() ?: return
        if (!session.hasDatabase) return
        val titles = HashMap<Long, String>()
        var client: Client? = null
        val handler = Client.ResultHandler { update ->
            when (update) {
                is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> client?.send(parameters(session, key), null)
                    is TdApi.AuthorizationStateReady -> {
                        client?.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(false)), null)
                        client?.send(TdApi.SetOption("notification_group_count_max", TdApi.OptionValueInteger(GROUPS)), null)
                        client?.send(TdApi.SetOption("notification_group_size_max", TdApi.OptionValueInteger(GROUP_SIZE)), null)
                    }
                    is TdApi.AuthorizationStateClosed -> com.kirox.app.telegram.SessionLocks.release(account.session)
                    is TdApi.AuthorizationStateWaitPhoneNumber, is TdApi.AuthorizationStateLoggingOut -> client?.send(TdApi.Close(), null)
                    else -> Unit
                }
                is TdApi.UpdateNewChat -> titles[update.chat.id] = update.chat.title
                is TdApi.UpdateChatTitle -> titles[update.chatId] = update.title
                is TdApi.UpdateNotificationGroup -> update.addedNotifications.forEach { notification ->
                    val type = notification.type as? TdApi.NotificationTypeNewMessage ?: return@forEach
                    if (notification.isSilent) return@forEach
                    show(account, update.notificationGroupId, titles[update.chatId].orEmpty(), if (type.showPreview) messagePreview(type.message) else "Новое сообщение")
                }
                else -> Unit
            }
        }
        val closeRequested = java.util.concurrent.atomic.AtomicBoolean(false)
        val close = { if (closeRequested.compareAndSet(false, true)) client?.send(TdApi.Close(), null) }
        // The main client may be opening this account (the user switched to it): leave it alone then.
        if (!com.kirox.app.telegram.SessionLocks.acquire(account.session) { close() }) return
        client = runCatching { Client.create(handler, null, null) }.getOrNull()
        if (client == null) { com.kirox.app.telegram.SessionLocks.release(account.session); return }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ close() }, windowMs)
    }

    private fun parameters(session: TelegramSessionManager, key: ByteArray) = TdApi.SetTdlibParameters().apply {
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

    private fun show(account: TelegramAccount, groupId: Int, chatTitle: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val accountLabel = account.title.ifBlank { account.phone.ifBlank { "Другой аккаунт" } }
        val tag = "kirox-account-${account.session}"
        val open = PendingIntent.getActivity(
            appContext, (account.session.hashCode() xor groupId),
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_SESSION, account.session),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, MessageNotifications.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(chatTitle.ifBlank { "Telegram" })
            .setContentText(text)
            .setSubText(accountLabel)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(tag, groupId, notification) }
    }

    companion object {
        /** Intent extra: the account to open when the notification is tapped. */
        const val EXTRA_SESSION = "kirox.session"
        private const val GROUPS = 10
        private const val GROUP_SIZE = 5
    }
}
