package com.kirox.app.telegram

/**
 * Which account databases are open outside the main client (notification peeks), so the main
 * client never opens the same TDLib database at the same time. Thread-safe.
 */
object SessionLocks {
    private class Holder(val close: () -> Unit, val waiting: MutableList<() -> Unit> = mutableListOf())
    private val holders = HashMap<String, Holder>()

    @Synchronized fun isHeld(session: String): Boolean = holders.containsKey(session)

    @Synchronized fun acquire(session: String, close: () -> Unit): Boolean {
        if (holders.containsKey(session)) return false
        holders[session] = Holder(close)
        return true
    }

    /** Called by the holder once its client has closed; runs whatever waited for the session. */
    fun release(session: String) {
        val waiting = synchronized(this) { holders.remove(session)?.waiting?.toList().orEmpty() }
        waiting.forEach { it() }
    }

    /**
     * Runs [block] now when nobody else holds [session]; otherwise asks the holder to close and runs
     * [block] after it did. Returns true when [block] ran immediately.
     */
    fun whenFree(session: String, block: () -> Unit): Boolean {
        val holder = synchronized(this) { holders[session]?.also { it.waiting += block } }
        if (holder == null) { block(); return true }
        holder.close()
        return false
    }
}
