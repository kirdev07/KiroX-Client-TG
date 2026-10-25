package com.kirox.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.kirox.app.KiroXApplication
import com.kirox.app.MainActivity
import com.kirox.app.telegram.TelegramChatRepository
import com.kirox.app.telegram.TelegramClient
import com.kirox.app.telegram.TelegramUserRepository
import com.kirox.app.telegram.displayName
import org.drinkless.tdlib.TdApi

/**
 * New-message notifications from TDLib's notification API. TDLib decides what to notify about
 * (it skips muted chats and drops notifications once messages are read on any device); this class
 * only mirrors its notification groups as Android notifications, one per chat.
 * Nothing is shown while the app is on screen.
 */
class MessageNotifications(
    private val context: Context,
    private val client: TelegramClient,
    private val chats: TelegramChatRepository,
    private val users: TelegramUserRepository
) {
    private class Item(val id: Int, val date: Int, val sender: String, val text: String)
    private class Group(val chatId: Long, val items: MutableList<Item> = mutableListOf())

    // Dispatcher-confined, like the repositories.
    private val groups = HashMap<Int, Group>()

    init {
        ensureChannel(context)
        // Telegram must know when the app goes to the background: it then marks the user offline
        // and TDLib starts notifying about new messages.
        AppVisibility.listener = { visible -> client.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(visible))) }
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                    is TdApi.AuthorizationStateReady -> enable()
                    // A background pause keeps what is already shown; logout and account switch clear it.
                    is TdApi.AuthorizationStateClosed -> { if (!client.paused) groups.keys.forEach(::cancel); groups.clear() }
                }
                is TdApi.UpdateActiveNotifications -> update.groups.forEach { group ->
                    apply(group.id, group.chatId, group.notifications, IntArray(0))
                }
                is TdApi.UpdateNotificationGroup -> apply(update.notificationGroupId, update.chatId, update.addedNotifications, update.removedNotificationIds)
            }
        }
    }

    private fun enable() {
        client.send(TdApi.SetOption("notification_group_count_max", TdApi.OptionValueInteger(MAX_GROUPS)))
        client.send(TdApi.SetOption("notification_group_size_max", TdApi.OptionValueInteger(MAX_PER_GROUP)))
        // A process started by a push has no visible screen: stay offline so Telegram keeps notifying.
        client.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(AppVisibility.visible)))
    }

    private fun apply(groupId: Int, chatId: Long, added: Array<TdApi.Notification>, removed: IntArray) {
        val group = groups.getOrPut(groupId) { Group(chatId) }
        val removedSet = removed.toHashSet()
        group.items.removeAll { it.id in removedSet }
        var fresh = false
        for (notification in added) {
            val type = notification.type as? TdApi.NotificationTypeNewMessage ?: continue
            group.items.removeAll { it.id == notification.id }
            group.items += Item(notification.id, notification.date, senderName(type.message), if (type.showPreview) preview(type.message) else "Новое сообщение")
            if (!notification.isSilent) fresh = true
        }
        group.items.sortBy { it.date }
        while (group.items.size > MAX_PER_GROUP) group.items.removeAt(0)
        if (group.items.isEmpty()) {
            groups.remove(groupId)
            cancel(groupId)
        } else if (!AppVisibility.visible) {
            show(groupId, group, alert = fresh)
        }
    }

    private fun show(groupId: Int, group: Group, alert: Boolean) {
        val title = chats.chat(group.chatId)?.title.orEmpty().ifBlank { "Telegram" }
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Вы").build())
            .setConversationTitle(title)
            .setGroupConversation(group.items.map { it.sender }.distinct().size > 1)
        group.items.forEach { item ->
            style.addMessage(item.text, item.date * 1000L, Person.Builder().setName(item.sender.ifBlank { title }).build())
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, groupId, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0, "Прочитано",
                PendingIntent.getBroadcast(
                    context, groupId,
                    Intent(context, MessageActionReceiver::class.java).setAction(ACTION_READ)
                        .putExtra(EXTRA_GROUP, groupId).putExtra(EXTRA_CHAT, group.chatId)
                        .putExtra(EXTRA_MESSAGES, group.items.map { it.id }.toIntArray())
                        .putExtra(EXTRA_MAX, group.items.maxOf { it.id }),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        // Without the notification permission (Android 13+) messages simply stay in the app.
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        try { NotificationManagerCompat.from(context).notify(TAG, groupId, notification) } catch (_: SecurityException) {}
    }

    private fun cancel(groupId: Int) = NotificationManagerCompat.from(context).cancel(TAG, groupId)

    /** Marks the chat read: TDLib then removes the group everywhere. */
    fun markRead(groupId: Int, chatId: Long, maxNotificationId: Int) = client.post {
        client.send(TdApi.RemoveNotificationGroup(groupId, maxNotificationId))
        chats.chat(chatId)?.lastMessage?.id?.let { last -> client.send(TdApi.ViewMessages(chatId, longArrayOf(last), null, true)) }
    }

    private fun senderName(message: TdApi.Message): String = when (val sender = message.senderId) {
        is TdApi.MessageSenderUser -> users.user(sender.userId)?.displayName().orEmpty()
        is TdApi.MessageSenderChat -> chats.chat(sender.chatId)?.title.orEmpty()
        else -> ""
    }

    private fun preview(message: TdApi.Message): String = when (val content = message.content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Фото" }
        is TdApi.MessageVideo -> content.caption.text.ifBlank { "Видео" }
        is TdApi.MessageDocument -> content.caption.text.ifBlank { "Файл" }
        is TdApi.MessageVoiceNote -> "Голосовое сообщение"
        is TdApi.MessageVideoNote -> "Видеосообщение"
        is TdApi.MessageSticker -> "${content.sticker.emoji} Стикер".trim()
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageAudio -> "Аудио"
        is TdApi.MessageContact -> "Контакт"
        is TdApi.MessageLocation -> "Местоположение"
        is TdApi.MessagePoll -> "Опрос"
        is TdApi.MessageCall -> "Звонок"
        else -> "Сообщение"
    }

    companion object {
        private const val CHANNEL = "messages"
        private const val TAG = "kirox-messages"
        private const val MAX_GROUPS = 10L
        private const val MAX_PER_GROUP = 6L
        internal const val ACTION_READ = "com.kirox.app.notify.READ"
        internal const val EXTRA_GROUP = "group"
        internal const val EXTRA_CHAT = "chat"
        internal const val EXTRA_MESSAGES = "messages"
        internal const val EXTRA_MAX = "max"

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Новые сообщения в чатах"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

/** "Прочитано" in a message notification. */
class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MessageNotifications.ACTION_READ) return
        val notifications = (context.applicationContext as KiroXApplication).telegram.notifications
        notifications.markRead(
            intent.getIntExtra(MessageNotifications.EXTRA_GROUP, 0),
            intent.getLongExtra(MessageNotifications.EXTRA_CHAT, 0),
            intent.getIntExtra(MessageNotifications.EXTRA_MAX, 0)
        )
    }
}

/** Whether any activity is started; notifications are not shown over the open app. */
object AppVisibility {
    @Volatile var visible = false
        internal set(value) {
            if (field == value) return
            field = value
            listener?.invoke(value)
        }
    @Volatile internal var listener: ((Boolean) -> Unit)? = null
}
