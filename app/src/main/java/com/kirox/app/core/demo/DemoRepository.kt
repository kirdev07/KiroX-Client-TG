package com.kirox.app.core.demo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

private val Context.demoPreferences by preferencesDataStore("kirox_demo_settings")

data class DemoSnapshot(
    val accounts: List<DemoAccount> = emptyList(),
    val activeAccount: DemoAccount? = null,
    val contacts: List<DemoContact> = emptyList(),
    val chats: List<DemoChat> = emptyList(),
    val calls: List<DemoCall> = emptyList(),
    val settings: DemoSettings = DemoSettings(),
    val ready: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DemoRepository internal constructor(
    private val context: Context,
    private val database: DemoDatabase
) {
    private val dao = database.demoDao()
    private val ids = AtomicLong(System.currentTimeMillis() * 100)
    private object Keys {
        val activeAccount = longPreferencesKey("active_account_id")
        val badge = booleanPreferencesKey("show_kirox_badge")
        val animations = booleanPreferencesKey("animations_enabled")
        val chatSection = stringPreferencesKey("chat_section")
        val contactsSection = stringPreferencesKey("contacts_section")
        val callFilter = stringPreferencesKey("call_filter")
    }

    val settings: Flow<DemoSettings> = context.demoPreferences.data.map { p ->
        DemoSettings(
            activeAccountId = p[Keys.activeAccount] ?: DemoSeed.ACCOUNT_PRIMARY,
            showKiroXBadge = p[Keys.badge] ?: true,
            animationsEnabled = p[Keys.animations] ?: true,
            chatSection = p[Keys.chatSection] ?: "Messages",
            contactsSection = p[Keys.contactsSection] ?: "Contacts",
            callFilter = p[Keys.callFilter] ?: "Все"
        )
    }.distinctUntilChanged()

    val snapshot: Flow<DemoSnapshot> = settings.flatMapLatest { prefs ->
        combine(
            dao.observeAccounts(), dao.observeContacts(prefs.activeAccountId),
            dao.observeChats(prefs.activeAccountId), dao.observeCalls(prefs.activeAccountId)
        ) { accounts, contacts, chats, calls ->
            DemoSnapshot(accounts, accounts.firstOrNull { it.accountId == prefs.activeAccountId }, contacts, chats, calls, prefs, true)
        }
    }

    suspend fun initialize() = dao.initializeOnce()
    fun messages(chatId: Long): Flow<List<DemoMessageWithReactions>> = dao.observeDetailedMessages(chatId)

    suspend fun setActiveAccount(id: Long) { context.demoPreferences.edit { it[Keys.activeAccount] = id } }
    suspend fun setBadge(enabled: Boolean) { context.demoPreferences.edit { it[Keys.badge] = enabled } }
    suspend fun setAnimations(enabled: Boolean) { context.demoPreferences.edit { it[Keys.animations] = enabled } }
    suspend fun setChatSection(value: String) { context.demoPreferences.edit { it[Keys.chatSection] = value } }
    suspend fun setContactsSection(value: String) { context.demoPreferences.edit { it[Keys.contactsSection] = value } }
    suspend fun setCallFilter(value: String) { context.demoPreferences.edit { it[Keys.callFilter] = value } }

    suspend fun sendMessage(accountId: Long, chatId: Long, text: String, quote: DemoMessage? = null): Long =
        dao.send(DemoMessage(accountId = accountId, chatId = chatId, author = "Вы", outgoing = true, text = text.trim(), sentAt = System.currentTimeMillis(), quoteAuthor = quote?.author, quoteText = quote?.text))

    suspend fun editMessage(accountId: Long, chatId: Long, messageId: Long, text: String) = dao.editAndRefresh(accountId, messageId, chatId, text.trim())
    suspend fun deleteMessage(accountId: Long, chatId: Long, messageId: Long) = dao.deleteAndRefresh(accountId, messageId, chatId)
    suspend fun clearHistory(accountId: Long, chatId: Long) = dao.clearAndRefresh(accountId, chatId)
    suspend fun setMessagePinned(accountId: Long, messageId: Long, pinned: Boolean) = dao.setMessagePinned(accountId, messageId, pinned)
    suspend fun archive(accountId: Long, chatId: Long, archived: Boolean) = dao.setArchived(accountId, chatId, archived)
    suspend fun markRead(accountId: Long, chatId: Long) = dao.markRead(accountId, chatId)
    suspend fun markSectionRead(accountId: Long, archived: Boolean) = dao.markSectionRead(accountId, archived)
    suspend fun saveDraft(accountId: Long, chatId: Long, draft: String) = dao.setDraft(accountId, chatId, draft)
    suspend fun setMuted(accountId: Long, chatId: Long, muted: Boolean) = dao.setMuted(accountId, chatId, muted)

    suspend fun toggleReaction(accountId: Long, messageId: Long, emoji: String, selected: Boolean, currentCount: Int) {
        dao.upsertReaction(DemoReaction(messageId, emoji, accountId, (currentCount + if (selected) 1 else -1).coerceAtLeast(0), selected))
    }

    suspend fun addContact(accountId: Long, name: String): DemoContact {
        val normalized = name.trim().lowercase(Locale.getDefault())
        require(normalized.isNotEmpty())
        val contact = DemoContact(ids.incrementAndGet(), accountId, name.trim(), normalized, null, "был(а) недавно", name.trim().take(1).uppercase())
        dao.insertContact(contact)
        return contact
    }

    suspend fun getOrCreateChat(accountId: Long, contactId: Long): DemoChat = database.withTransaction {
        dao.chatForContact(accountId, contactId) ?: run {
            val contact = requireNotNull(dao.contact(accountId, contactId))
            val chat = DemoChat(ids.incrementAndGet(), accountId, contactId, contact.name, contact.initial, contact.status)
            dao.insertChat(chat)
            chat
        }
    }

    suspend fun addCall(accountId: Long, contact: DemoContact, kind: String) = dao.insertCall(DemoCall(accountId = accountId, contactId = contact.contactId, name = contact.name, occurredAt = System.currentTimeMillis(), kind = kind))

    companion object {
        @Volatile private var instance: DemoRepository? = null
        fun get(context: Context): DemoRepository = instance ?: synchronized(this) {
            instance ?: DemoRepository(context.applicationContext, DemoDatabase.get(context)).also { instance = it }
        }
    }
}
