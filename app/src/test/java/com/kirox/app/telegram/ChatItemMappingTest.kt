package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatItemMappingTest {
    private fun chat(outgoing: Boolean, lastId: Long, readOutbox: Long, muteFor: Int = 0, useDefault: Boolean = true) = TdApi.Chat().apply {
        id = 7; title = "Чат"; unreadCount = 3; lastReadOutboxMessageId = readOutbox
        lastMessage = TdApi.Message().apply { id = lastId; isOutgoing = outgoing; date = 100; content = TdApi.MessageText().apply { text = TdApi.FormattedText("привет", emptyArray()) } }
        notificationSettings = TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = useDefault; this.muteFor = muteFor }
    }
    private val position = TdApi.ChatPosition(TdApi.ChatListMain(), 1, true, null)

    @Test fun outgoingReadStateFollowsReadOutbox() {
        assertEquals(LastMessageState.Read, toChatItem(chat(true, 10, 10), position, false).outgoingState)
        assertEquals(LastMessageState.Sent, toChatItem(chat(true, 11, 10), position, false).outgoingState)
        assertEquals(LastMessageState.None, toChatItem(chat(false, 11, 10), position, false).outgoingState)
    }

    @Test fun muteAndPinAndPreview() {
        val item = toChatItem(chat(false, 1, 0, muteFor = 3600, useDefault = false), position, false)
        assertTrue(item.isMuted)
        assertTrue(item.isPinned)
        assertEquals("привет", item.preview)
        assertFalse(toChatItem(chat(false, 1, 0, muteFor = 3600, useDefault = true), position, false).isMuted)
    }

    @Test fun textDraftIsExposed() {
        val withDraft = chat(false, 1, 0).apply {
            draftMessage = TdApi.DraftMessage().apply { date = 200; content = TdApi.DraftMessageContentText().apply { text = TdApi.FormattedText("черновик", emptyArray()) } }
        }
        val item = toChatItem(withDraft, position, false)
        assertEquals("черновик", item.draft)
        assertEquals(200, item.timestamp)
    }
}
