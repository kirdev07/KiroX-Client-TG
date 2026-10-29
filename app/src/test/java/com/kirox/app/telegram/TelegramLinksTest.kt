package com.kirox.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramLinksTest {
    @Test fun publicNamesOpenInTheApp() {
        assertEquals(TelegramLink.Username("durov"), telegramLinkOf("https://t.me/durov"))
        assertEquals(TelegramLink.Username("kirox_news"), telegramLinkOf("t.me/kirox_news/42"))
        assertEquals(TelegramLink.Username("durov"), telegramLinkOf("tg://resolve?domain=durov"))
    }

    @Test fun mentionsByIdOpenThePrivateChat() {
        assertEquals(TelegramLink.User(12345), telegramLinkOf("tg://user?id=12345"))
    }

    @Test fun invitesStickersAndOtherSitesGoToTheBrowser() {
        assertNull(telegramLinkOf("https://t.me/joinchat/AbCdEf"))
        assertNull(telegramLinkOf("https://t.me/+AbCdEf"))
        assertNull(telegramLinkOf("https://t.me/addstickers/Animals"))
        assertNull(telegramLinkOf("https://example.com/durov"))
        assertNull(telegramLinkOf("https://t.me/ab"))
    }
}
