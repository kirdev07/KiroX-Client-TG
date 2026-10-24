package com.kirox.app.core.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedTest {
    @Test fun stableIdsAreUniqueAndRelationshipsAreValid() {
        assertEquals(DemoSeed.accounts.size, DemoSeed.accounts.map { it.accountId }.toSet().size)
        assertEquals(DemoSeed.contacts.size, DemoSeed.contacts.map { it.contactId }.toSet().size)
        assertEquals(DemoSeed.chats.size, DemoSeed.chats.map { it.chatId }.toSet().size)
        val accountIds = DemoSeed.accounts.map { it.accountId }.toSet()
        assertTrue(DemoSeed.contacts.all { it.accountId in accountIds })
        assertTrue(DemoSeed.chats.all { chat -> chat.accountId in accountIds && (chat.contactId == null || DemoSeed.contacts.any { it.contactId == chat.contactId && it.accountId == chat.accountId }) })
        assertTrue(DemoSeed.messages.all { message -> DemoSeed.chats.any { it.chatId == message.chatId && it.accountId == message.accountId } })
    }

    @Test fun accountsHaveIndependentDataAndDemoLayerHasNoTdLibTypes() {
        val firstChats = DemoSeed.chats.filter { it.accountId == DemoSeed.ACCOUNT_PRIMARY }.map { it.chatId }.toSet()
        val secondChats = DemoSeed.chats.filter { it.accountId == DemoSeed.ACCOUNT_SECONDARY }.map { it.chatId }.toSet()
        assertTrue(firstChats.isNotEmpty() && secondChats.isNotEmpty())
        assertTrue(firstChats.intersect(secondChats).isEmpty())
        assertTrue(DemoRepository::class.java.declaredFields.none { it.type.name.contains("tdlib", ignoreCase = true) })
    }
}
