package com.kirox.app.format

import com.kirox.app.telegram.TelegramUserStatus
import com.kirox.app.ui.format.TelegramFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TelegramFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_790_000_000_000L // 2026-09-21 14:13 UTC, Monday
    private val nowSeconds = (now / 1000).toInt()

    @Test fun chatTimeTodayYesterdayAndOlder() {
        assertEquals("14:03", TelegramFormat.chatTime(nowSeconds - 600, now, utc))
        assertEquals("вс", TelegramFormat.chatTime(nowSeconds - 86_400, now, utc))
        assertEquals("01.09.26", TelegramFormat.chatTime(nowSeconds - 20 * 86_400, now, utc))
        assertEquals("", TelegramFormat.chatTime(0, now, utc))
    }

    @Test fun statusTexts() {
        assertEquals("в сети", TelegramFormat.status(TelegramUserStatus.Online, now, utc))
        assertEquals("был(а) в 14:03", TelegramFormat.status(TelegramUserStatus.Offline(nowSeconds - 600), now, utc))
        assertEquals("был(а) вчера в 14:13", TelegramFormat.status(TelegramUserStatus.Offline(nowSeconds - 86_400), now, utc))
        assertEquals("был(а) недавно", TelegramFormat.status(TelegramUserStatus.Recently, now, utc))
    }

    @Test fun phoneGetsPlus() {
        assertEquals("+77001234567", TelegramFormat.phone("77001234567"))
        assertEquals("", TelegramFormat.phone(""))
    }
}
