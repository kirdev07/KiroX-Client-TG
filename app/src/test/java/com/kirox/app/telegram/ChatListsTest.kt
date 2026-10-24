package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListsTest {
    @Test fun foldersWithDifferentIdsAreDifferentLists() {
        assertFalse(sameChatList(TdApi.ChatListFolder(1), TdApi.ChatListFolder(2)))
        assertTrue(sameChatList(TdApi.ChatListFolder(3), TdApi.ChatListFolder(3)))
        assertTrue(sameChatList(TdApi.ChatListMain(), TdApi.ChatListMain()))
        assertFalse(sameChatList(TdApi.ChatListMain(), TdApi.ChatListArchive()))
    }

    @Test fun positionUpdateKeepsOtherFoldersAndRemovesOnZeroOrder() {
        val start = arrayOf(position(TdApi.ChatListMain(), 10), position(TdApi.ChatListFolder(1), 20), position(TdApi.ChatListFolder(2), 30))
        val updated = applyChatPosition(start, position(TdApi.ChatListFolder(1), 25))
        assertEquals(3, updated.size)
        assertEquals(25L, updated.first { (it.list as? TdApi.ChatListFolder)?.chatFolderId == 1 }.order)
        val removed = applyChatPosition(updated, position(TdApi.ChatListMain(), 0))
        assertEquals(2, removed.size)
        assertTrue(removed.none { it.list is TdApi.ChatListMain })
    }

    private fun position(list: TdApi.ChatList, order: Long) = TdApi.ChatPosition(list, order, false, null)
}
