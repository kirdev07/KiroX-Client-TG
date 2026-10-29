package com.kirox.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageWordTest {
    @Test fun russianPluralForms() {
        assertEquals("сообщение", messageWord(1))
        assertEquals("сообщение", messageWord(21))
        assertEquals("сообщения", messageWord(3))
        assertEquals("сообщений", messageWord(5))
        assertEquals("сообщений", messageWord(11))
        assertEquals("сообщений", messageWord(112))
    }
}
