package com.kirox.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAuthTest {
    @Test fun acceptsOnlyDemoCode() {
        assertTrue(DemoAuth.verify("12345"))
        assertFalse(DemoAuth.verify("12344"))
        assertFalse(DemoAuth.verify(""))
    }

    @Test fun demoLengthIsPassedAsConfiguration() {
        assertEquals(5, DemoAuth.codeLength)
    }
}
