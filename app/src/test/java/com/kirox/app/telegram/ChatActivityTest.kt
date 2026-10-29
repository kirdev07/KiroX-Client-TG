package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatActivityTest {
    @Test fun privateChatShowsOnlyTheActivity() {
        assertEquals("печатает…", chatActivityLine(listOf("Анна"), ChatActivity.Typing, isGroup = false))
    }

    @Test fun groupNamesTheMember() {
        assertEquals("Анна записывает голосовое…", chatActivityLine(listOf("Анна"), ChatActivity.RecordingVoice, isGroup = true))
    }

    @Test fun severalMembersAreCounted() {
        assertEquals("Анна и ещё 2 печатают…", chatActivityLine(listOf("Анна", "Борис", "Вера"), ChatActivity.Typing, isGroup = true))
    }

    @Test fun cancelEndsTheActivity() {
        assertNull(TdApi.ChatActionCancel().toActivity())
        assertEquals(ChatActivity.Typing, TdApi.ChatActionTyping().toActivity())
        assertEquals(ChatActivity.SendingPhoto, TdApi.ChatActionUploadingPhoto(40).toActivity())
    }
}
