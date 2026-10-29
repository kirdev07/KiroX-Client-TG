package com.kirox.app.ui.chat

import com.kirox.app.telegram.TelegramEntityType
import com.kirox.app.telegram.TelegramTextEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageFormattingTest {
    @Test fun linksPointWhereTelegramWould() {
        val text = "@durov kirox.app mail@kirox.app +7 700 000 00 00"
        assertEquals("https://t.me/durov", entityLink(text, TelegramTextEntity(0, 6, TelegramEntityType.Mention)))
        assertEquals("https://kirox.app", entityLink(text, TelegramTextEntity(7, 9, TelegramEntityType.Url)))
        assertEquals("mailto:mail@kirox.app", entityLink(text, TelegramTextEntity(17, 14, TelegramEntityType.Email)))
        assertEquals("tel:+77000000000", entityLink(text, TelegramTextEntity(32, 16, TelegramEntityType.Phone)))
    }

    @Test fun styleOnlyEntitiesAndBrokenRangesAreNotLinks() {
        assertNull(entityLink("жирный", TelegramTextEntity(0, 6, TelegramEntityType.Bold)))
        assertNull(entityLink("коротко", TelegramTextEntity(3, 20, TelegramEntityType.Url)))
    }

    @Test fun fileSizesAreHumanReadable() {
        assertEquals("", formatFileSize(0))
        assertEquals("512 Б", formatFileSize(512))
        assertEquals("1,5 КБ", formatFileSize(1536))
        assertEquals("2,0 МБ", formatFileSize(2L * 1024 * 1024))
    }
}
