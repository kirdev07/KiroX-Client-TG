package com.kirox.app.core.demo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoDatabaseTest {
    private lateinit var db: DemoDatabase
    private lateinit var dao: DemoDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DemoDatabase::class.java).allowMainThreadQueries().build()
        dao = db.demoDao()
        runBlocking { dao.initializeOnce() }
    }

    @After fun close() = db.close()

    @Test fun sendEditDeleteUpdatesChatPreviewAndPersistsHistory() = runBlocking {
        val chat = dao.observeChats(1).first().first { !it.archived }
        val id = dao.send(DemoMessage(accountId = 1, chatId = chat.chatId, author = "Вы", outgoing = true, text = "persistent", sentAt = Long.MAX_VALUE - 1))
        assertEquals("persistent", dao.chat(chat.chatId)?.lastMessage)
        assertTrue(dao.observeMessages(chat.chatId).first().any { it.messageId == id })
        dao.editAndRefresh(1, id, chat.chatId, "edited")
        assertEquals("edited", dao.chat(chat.chatId)?.lastMessage)
        dao.deleteAndRefresh(1, id, chat.chatId)
        assertTrue(dao.observeMessages(chat.chatId).first().none { it.messageId == id })
    }

    @Test fun archiveReadDraftReactionAndSeedAreDurable() = runBlocking {
        val chat = dao.observeChats(1).first().first()
        dao.setArchived(1, chat.chatId, true)
        dao.setDraft(1, chat.chatId, "draft")
        dao.markRead(1, chat.chatId)
        val message = dao.observeMessages(chat.chatId).first().first()
        dao.upsertReaction(DemoReaction(message.messageId, "like", 1, 1, true))
        dao.initializeOnce()
        assertEquals(2, dao.accountCount())
        val changed = dao.chat(chat.chatId)!!
        assertTrue(changed.archived)
        assertEquals("draft", changed.draft)
        assertEquals(0, changed.unreadCount)
        assertEquals(1, dao.observeDetailedMessages(chat.chatId).first().first { it.message.messageId == message.messageId }.reactions.size)
    }

    @Test fun contactsMapToOneChatAndAccountsAreIsolated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DemoRepository(context, db)
        val contact = repository.addContact(1, "Уникальный контакт")
        val first = repository.getOrCreateChat(1, contact.contactId)
        val second = repository.getOrCreateChat(1, contact.contactId)
        assertEquals(first.chatId, second.chatId)
        val accountOne = dao.observeChats(1).first().map { it.chatId }.toSet()
        val accountTwo = dao.observeChats(2).first().map { it.chatId }.toSet()
        assertNotEquals(accountOne, accountTwo)
        assertTrue(accountOne.intersect(accountTwo).isEmpty())
        assertNotNull(dao.contact(1, contact.contactId))
    }

    @Test fun activeAccountSwitchChangesAllObservedCollections() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DemoRepository(context, db)
        repository.setActiveAccount(1)
        val first = repository.snapshot.first { it.ready && it.activeAccount?.accountId == 1L }
        repository.setActiveAccount(2)
        val second = repository.snapshot.first { it.ready && it.activeAccount?.accountId == 2L }
        assertNotEquals(first.activeAccount?.name, second.activeAccount?.name)
        assertTrue(first.contacts.map { it.contactId }.intersect(second.contacts.map { it.contactId }.toSet()).isEmpty())
        assertTrue(first.chats.map { it.chatId }.intersect(second.chats.map { it.chatId }.toSet()).isEmpty())
        assertTrue(first.calls.map { it.callId }.intersect(second.calls.map { it.callId }.toSet()).isEmpty())
        repository.setActiveAccount(1)
    }

    @Test fun fileDatabaseSurvivesCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restart-${System.nanoTime()}.db"
        var fileDb = Room.databaseBuilder(context, DemoDatabase::class.java, name).build()
        fileDb.demoDao().initializeOnce()
        val chat = fileDb.demoDao().observeChats(1).first().first()
        fileDb.demoDao().send(DemoMessage(accountId = 1, chatId = chat.chatId, author = "Вы", outgoing = true, text = "after restart", sentAt = Long.MAX_VALUE))
        fileDb.close()
        fileDb = Room.databaseBuilder(context, DemoDatabase::class.java, name).build()
        assertEquals("after restart", fileDb.demoDao().chat(chat.chatId)?.lastMessage)
        fileDb.close()
        context.deleteDatabase(name)
        Unit
    }
}
