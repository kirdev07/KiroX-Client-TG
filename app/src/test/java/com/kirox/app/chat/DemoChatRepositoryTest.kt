package com.kirox.app.chat

import com.kirox.app.core.demo.DemoSeed
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoChatRepositoryTest {
    @Test fun seededHistoryCoversIncomingAndOutgoingMessages() {
        assertTrue(DemoSeed.messages.any { !it.outgoing })
        assertTrue(DemoSeed.messages.any { it.outgoing })
    }

    @Test fun everySeededMessageBelongsToItsAccountChat() {
        assertTrue(DemoSeed.messages.all { message -> DemoSeed.chats.any { it.chatId == message.chatId && it.accountId == message.accountId } })
    }
}
