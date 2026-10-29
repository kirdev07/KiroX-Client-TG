package com.kirox.app.telegram

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One signed-in (or signing-in) Telegram account: its own TDLib database under tdlib/<session>. */
data class TelegramAccount(val session: String, val title: String, val phone: String)

/**
 * Accounts known on this device and which one TDLib runs now. Only the active account is online;
 * the others wake up when switched to. The first account keeps the original "session-primary" folder.
 */
class TelegramAccounts(context: Context) {
    private val prefs = context.getSharedPreferences("kirox_accounts", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<TelegramAccount>> = _accounts.asStateFlow()

    var active: String
        get() = prefs.getString(KEY_ACTIVE, null)?.takeIf { s -> _accounts.value.any { it.session == s } } ?: PRIMARY
        set(value) { prefs.edit().putString(KEY_ACTIVE, value).apply() }

    /** New empty slot; returns its session name. It stays "pending" until signed in. */
    fun create(): String {
        val session = "session-${System.currentTimeMillis()}"
        prefs.edit().putBoolean("pending_$session", true).apply()
        save(_accounts.value + TelegramAccount(session, "", ""))
        return session
    }

    /** True for a slot added with [create] that has never been signed in. */
    fun isPending(session: String): Boolean = prefs.getBoolean("pending_$session", false)

    /** The slot is signed in: it is a real account now and is never discarded as an abandoned sign-in. */
    fun confirm(session: String) {
        if (isPending(session)) prefs.edit().remove("pending_$session").apply()
    }

    fun remove(session: String) {
        if (session == PRIMARY && _accounts.value.size == 1) return
        prefs.edit().remove("pending_$session").apply()
        save(_accounts.value.filterNot { it.session == session })
    }

    /** Remembers who is signed in to [session], for the switcher while that account is offline. */
    fun describe(session: String, title: String, phone: String) {
        val list = _accounts.value
        if (list.none { it.session == session && (it.title != title || it.phone != phone) }) return
        save(list.map { if (it.session == session) it.copy(title = title, phone = phone) else it })
    }

    private fun load(): List<TelegramAccount> {
        val sessions = prefs.getString(KEY_LIST, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty().ifEmpty { listOf(PRIMARY) }
        return sessions.map { TelegramAccount(it, prefs.getString("title_$it", "").orEmpty(), prefs.getString("phone_$it", "").orEmpty()) }
    }

    private fun save(list: List<TelegramAccount>) {
        val editor = prefs.edit().putString(KEY_LIST, list.joinToString(",") { it.session })
        list.forEach { editor.putString("title_${it.session}", it.title).putString("phone_${it.session}", it.phone) }
        editor.apply()
        _accounts.value = list
    }

    companion object {
        const val PRIMARY = "session-primary"
        private const val KEY_LIST = "sessions"
        private const val KEY_ACTIVE = "active"
    }
}
