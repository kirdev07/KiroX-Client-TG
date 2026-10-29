package com.kirox.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLocksTest {
    @Test fun freeSessionRunsAtOnce() {
        var ran = false
        assertTrue(SessionLocks.whenFree("free-${System.nanoTime()}") { ran = true })
        assertTrue(ran)
    }

    @Test fun heldSessionAsksHolderToCloseAndRunsAfterRelease() {
        val session = "held-${System.nanoTime()}"
        var closeAsked = 0
        var ran = false
        assertTrue(SessionLocks.acquire(session) { closeAsked++ })
        assertFalse(SessionLocks.acquire(session) {})
        assertFalse(SessionLocks.whenFree(session) { ran = true })
        assertEquals(1, closeAsked)
        assertFalse(ran)
        SessionLocks.release(session)
        assertTrue(ran)
        assertFalse(SessionLocks.isHeld(session))
    }
}
