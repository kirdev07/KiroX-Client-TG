package com.kirox.app.telegram

/**
 * Which account databases are open right now, by the main client or by a notification peek, so two
 * TDLib clients never open the same database directory. Thread-safe.
 */
object SessionLocks {
    private class Holder(val close: () -> Unit, val waiting: MutableList<() -> Unit> = mutableListOf())
    private val holders = HashMap<String, Holder>()
    private val timer by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    @Synchronized fun isHeld(session: String): Boolean = holders.containsKey(session)

    /**
     * Takes [session] for one client; false when another holds it. [close] is how others ask the
     * holder to let go. With [timeoutMs], the lock is freed by force if the holder never releases it
     * (a client that never reached Closed must not block the account forever).
     */
    fun acquire(session: String, timeoutMs: Long = 0, close: () -> Unit): Boolean = acquireHandle(session, timeoutMs, close) != null

    /**
     * Like [acquire], but returns a handle (null when held by someone else). A holder that may be
     * freed by the timeout releases with [release] and its handle, so a late release never frees
     * whoever took the session after it.
     */
    fun acquireHandle(session: String, timeoutMs: Long = 0, close: () -> Unit): Any? {
        val holder = Holder(close)
        synchronized(this) {
            if (holders.containsKey(session)) return null
            holders[session] = holder
        }
        if (timeoutMs > 0) timer.postDelayed({ release(session, holder) }, timeoutMs)
        return holder
    }

    /** Called by the holder once its client has closed; runs whatever waited for the session. */
    fun release(session: String) = release(session, null)

    /** Releases [session] only if [handle] still holds it. */
    fun release(session: String, handle: Any) = release(session, handle as? Holder ?: return)

    private fun release(session: String, only: Holder?) {
        val waiting = synchronized(this) {
            val current = holders[session] ?: return
            if (only != null && current !== only) return
            holders.remove(session)
            current.waiting.toList()
        }
        waiting.forEach { it() }
    }

    /**
     * Runs [block] now when nobody holds [session]; otherwise asks the holder to close and runs
     * [block] after it did. Returns true when [block] ran immediately.
     */
    fun whenFree(session: String, block: () -> Unit): Boolean {
        val holder = synchronized(this) { holders[session]?.also { it.waiting += block } }
        if (holder == null) { block(); return true }
        holder.close()
        return false
    }
}
