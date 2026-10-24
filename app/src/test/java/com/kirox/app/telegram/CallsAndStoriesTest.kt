package com.kirox.app.telegram

import com.kirox.app.ui.format.TelegramFormat
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsAndStoriesTest {
    private fun callMessage(outgoing: Boolean, reason: TdApi.CallDiscardReason, duration: Int = 0) = TdApi.Message().apply {
        id = 5; chatId = 9; isOutgoing = outgoing; date = 100
        content = TdApi.MessageCall().apply { discardReason = reason; this.duration = duration }
    }

    @Test fun callKinds() {
        assertEquals(TelegramCallKind.Missed, toCall(callMessage(false, TdApi.CallDiscardReasonMissed()), "A", null)?.kind)
        assertEquals(TelegramCallKind.Missed, toCall(callMessage(false, TdApi.CallDiscardReasonDeclined()), "A", null)?.kind)
        assertEquals(TelegramCallKind.Incoming, toCall(callMessage(false, TdApi.CallDiscardReasonHungUp(), 61), "A", null)?.kind)
        assertEquals(TelegramCallKind.Unanswered, toCall(callMessage(true, TdApi.CallDiscardReasonMissed()), "A", null)?.kind)
        assertEquals(TelegramCallKind.Outgoing, toCall(callMessage(true, TdApi.CallDiscardReasonHungUp(), 5), "A", null)?.kind)
        assertNull(toCall(TdApi.Message().apply { content = TdApi.MessageText() }, "A", null))
    }

    @Test fun durationFormat() {
        assertEquals("0:42", TelegramFormat.duration(42))
        assertEquals("3:21", TelegramFormat.duration(201))
        assertEquals("1:02:03", TelegramFormat.duration(3723))
    }

    @Test fun videoStoryUsesThumbnail() {
        val story = TdApi.Story().apply {
            id = 3; posterChatId = 11; date = 50; caption = TdApi.FormattedText("привет", emptyArray())
            content = TdApi.StoryContentVideo().apply {
                video = TdApi.StoryVideo().apply { width = 720; height = 1280; thumbnail = TdApi.Thumbnail().apply { file = TdApi.File().apply { id = 77 } } }
            }
        }
        val mapped = toStory(story)
        assertTrue(mapped.isVideo)
        assertEquals(77, mapped.image?.id)
        assertEquals("привет", mapped.caption)
        assertEquals(720f / 1280f, mapped.aspect, 0.001f)
    }
}
