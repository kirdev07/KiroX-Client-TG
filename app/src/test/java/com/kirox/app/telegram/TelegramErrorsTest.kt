package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramErrorsTest {
    @Test fun floodWaitShowsRetryTime() {
        assertEquals("Слишком много попыток. Повторите через 30 с", humanError(TdApi.Error(429, "Too Many Requests: retry after 30")))
        assertEquals("Слишком много попыток. Повторите через 2 мин", humanError(TdApi.Error(420, "FLOOD_WAIT_90")))
        assertEquals("Слишком много попыток. Повторите через 2 ч", humanError(TdApi.Error(429, "Too Many Requests: retry after 3700")))
    }

    @Test fun knownCodesAreLocalized() {
        assertEquals("Неверный код подтверждения", humanError(TdApi.Error(400, "PHONE_CODE_INVALID")))
        assertEquals("Этот номер заблокирован в Telegram", humanError(TdApi.Error(400, "PHONE_NUMBER_BANNED")))
    }

    @Test fun unknownErrorsNeverLeakRawMessage() {
        assertEquals("Не удалось выполнить запрос", humanError(TdApi.Error(400, "SOME_INTERNAL_THING")))
        assertEquals("Telegram временно недоступен. Проверьте соединение", humanError(TdApi.Error(500, "Internal")))
    }
}
