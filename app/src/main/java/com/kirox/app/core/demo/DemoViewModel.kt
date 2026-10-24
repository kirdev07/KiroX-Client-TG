package com.kirox.app.core.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DemoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DemoRepository.get(application)
    val state: StateFlow<DemoSnapshot> = repository.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DemoSnapshot())

    init { viewModelScope.launch { repository.initialize() } }

    fun messages(chatId: Long) = repository.messages(chatId)
    fun switchAccount(id: Long) = launch { repository.setActiveAccount(id) }
    fun setBadge(enabled: Boolean) = launch { repository.setBadge(enabled) }
    fun setAnimations(enabled: Boolean) = launch { repository.setAnimations(enabled) }
    fun setChatSection(value: String) = launch { repository.setChatSection(value) }
    fun setContactsSection(value: String) = launch { repository.setContactsSection(value) }
    fun setCallFilter(value: String) = launch { repository.setCallFilter(value) }
    fun send(chatId: Long, text: String, quote: DemoMessage? = null) = withAccount { repository.sendMessage(it, chatId, text, quote) }
    fun edit(chatId: Long, messageId: Long, text: String) = withAccount { repository.editMessage(it, chatId, messageId, text) }
    fun delete(chatId: Long, messageId: Long) = withAccount { repository.deleteMessage(it, chatId, messageId) }
    fun clear(chatId: Long) = withAccount { repository.clearHistory(it, chatId) }
    fun pin(messageId: Long, pinned: Boolean) = withAccount { repository.setMessagePinned(it, messageId, pinned) }
    fun archive(chatId: Long, archived: Boolean) = withAccount { repository.archive(it, chatId, archived) }
    fun markRead(chatId: Long) = withAccount { repository.markRead(it, chatId) }
    fun markSectionRead(archived: Boolean) = withAccount { repository.markSectionRead(it, archived) }
    fun saveDraft(chatId: Long, draft: String) = withAccount { repository.saveDraft(it, chatId, draft) }
    fun setMuted(chatId: Long, muted: Boolean) = withAccount { repository.setMuted(it, chatId, muted) }
    fun react(messageId: Long, emoji: String, selected: Boolean, count: Int) = withAccount { repository.toggleReaction(it, messageId, emoji, selected, count) }
    suspend fun addContact(name: String): DemoContact = repository.addContact(requireAccount(), name)
    suspend fun chatFor(contactId: Long): DemoChat = repository.getOrCreateChat(requireAccount(), contactId)
    fun addCall(contact: DemoContact, kind: String = "outgoing") = withAccount { repository.addCall(it, contact, kind) }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
    private fun withAccount(block: suspend (Long) -> Unit) = launch { block(requireAccount()) }
    private fun requireAccount(): Long = state.value.activeAccount?.accountId ?: state.value.settings.activeAccountId
}
