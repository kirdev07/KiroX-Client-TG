package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftTextTest {
    @Test fun chatWithoutDraftHasEmptyText() {
        assertEquals("", draftText(TdApi.Chat()))
    }

    @Test fun textDraftIsReturnedAsIs() {
        val chat = TdApi.Chat().apply {
            draftMessage = TdApi.DraftMessage(null, 0, TdApi.DraftMessageContentText(TdApi.FormattedText("привет ", emptyArray()), null), 0, null)
        }
        assertEquals("привет ", draftText(chat))
    }

    @Test fun threadDraftIsReadFromItsOwnDraftMessage() {
        assertEquals("", draftText(null as TdApi.DraftMessage?))
        val draft = TdApi.DraftMessage(null, 0, TdApi.DraftMessageContentText(TdApi.FormattedText("в теме", emptyArray()), null), 0, null)
        assertEquals("в теме", draftText(draft))
    }
}
