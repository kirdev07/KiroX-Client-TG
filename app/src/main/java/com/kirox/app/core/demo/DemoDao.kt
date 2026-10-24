package com.kirox.app.core.demo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DemoDao {
    @Query("SELECT * FROM demo_accounts ORDER BY accountId") fun observeAccounts(): Flow<List<DemoAccount>>
    @Query("SELECT * FROM demo_contacts WHERE accountId=:accountId ORDER BY name COLLATE NOCASE") fun observeContacts(accountId: Long): Flow<List<DemoContact>>
    @Query("SELECT * FROM demo_chats WHERE accountId=:accountId ORDER BY pinned DESC, lastMessageAt DESC") fun observeChats(accountId: Long): Flow<List<DemoChat>>
    @Query("SELECT * FROM demo_calls WHERE accountId=:accountId ORDER BY occurredAt DESC") fun observeCalls(accountId: Long): Flow<List<DemoCall>>
    @Query("SELECT * FROM demo_messages WHERE chatId=:chatId ORDER BY sentAt, messageId") fun observeMessages(chatId: Long): Flow<List<DemoMessage>>
    @Transaction @Query("SELECT * FROM demo_messages WHERE chatId=:chatId ORDER BY sentAt, messageId") fun observeDetailedMessages(chatId: Long): Flow<List<DemoMessageWithReactions>>
    @Query("SELECT * FROM demo_reactions WHERE messageId IN (:messageIds)") fun observeReactions(messageIds: List<Long>): Flow<List<DemoReaction>>
    @Query("SELECT * FROM demo_messages WHERE chatId=:chatId ORDER BY sentAt DESC, messageId DESC LIMIT 1") suspend fun latestMessage(chatId: Long): DemoMessage?
    @Query("SELECT * FROM demo_chats WHERE chatId=:chatId") suspend fun chat(chatId: Long): DemoChat?
    @Query("SELECT * FROM demo_chats WHERE accountId=:accountId AND contactId=:contactId LIMIT 1") suspend fun chatForContact(accountId: Long, contactId: Long): DemoChat?
    @Query("SELECT * FROM demo_contacts WHERE contactId=:contactId AND accountId=:accountId") suspend fun contact(accountId: Long, contactId: Long): DemoContact?
    @Query("SELECT * FROM demo_meta WHERE `key`=:key") suspend fun meta(key: String): DemoMeta?
    @Query("SELECT COUNT(*) FROM demo_accounts") suspend fun accountCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccounts(items: List<DemoAccount>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertContacts(items: List<DemoContact>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertChats(items: List<DemoChat>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMessages(items: List<DemoMessage>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCalls(items: List<DemoCall>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMeta(item: DemoMeta)
    @Insert suspend fun insertMessage(item: DemoMessage): Long
    @Insert suspend fun insertContact(item: DemoContact)
    @Insert suspend fun insertChat(item: DemoChat)
    @Insert suspend fun insertCall(item: DemoCall): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertReaction(item: DemoReaction)

    @Query("UPDATE demo_messages SET text=:text WHERE messageId=:messageId AND accountId=:accountId AND outgoing=1") suspend fun editMessage(accountId: Long, messageId: Long, text: String)
    @Query("DELETE FROM demo_messages WHERE messageId=:messageId AND accountId=:accountId") suspend fun deleteMessage(accountId: Long, messageId: Long)
    @Query("DELETE FROM demo_messages WHERE chatId=:chatId AND accountId=:accountId") suspend fun clearMessages(accountId: Long, chatId: Long)
    @Query("UPDATE demo_messages SET pinned=:pinned WHERE messageId=:messageId AND accountId=:accountId") suspend fun setMessagePinned(accountId: Long, messageId: Long, pinned: Boolean)
    @Query("UPDATE demo_chats SET archived=:archived WHERE chatId=:chatId AND accountId=:accountId") suspend fun setArchived(accountId: Long, chatId: Long, archived: Boolean)
    @Query("UPDATE demo_chats SET unreadCount=0 WHERE chatId=:chatId AND accountId=:accountId") suspend fun markRead(accountId: Long, chatId: Long)
    @Query("UPDATE demo_chats SET unreadCount=0 WHERE accountId=:accountId AND archived=:archived") suspend fun markSectionRead(accountId: Long, archived: Boolean)
    @Query("UPDATE demo_chats SET draft=:draft WHERE chatId=:chatId AND accountId=:accountId") suspend fun setDraft(accountId: Long, chatId: Long, draft: String)
    @Query("UPDATE demo_chats SET muted=:muted WHERE chatId=:chatId AND accountId=:accountId") suspend fun setMuted(accountId: Long, chatId: Long, muted: Boolean)
    @Query("UPDATE demo_chats SET lastMessage=:preview,lastMessageAt=:at WHERE chatId=:chatId") suspend fun updatePreview(chatId: Long, preview: String, at: Long)

    @Transaction suspend fun initializeOnce() {
        if (meta(DemoSeed.SEED_KEY) != null) return
        insertAccounts(DemoSeed.accounts)
        insertContacts(DemoSeed.contacts)
        insertChats(DemoSeed.chats)
        insertMessages(DemoSeed.messages)
        insertCalls(DemoSeed.calls)
        insertMeta(DemoMeta(DemoSeed.SEED_KEY, DemoSeed.VERSION))
    }

    @Transaction suspend fun send(item: DemoMessage): Long {
        val id = insertMessage(item)
        updatePreview(item.chatId, item.text, item.sentAt)
        return id
    }

    @Transaction suspend fun editAndRefresh(accountId: Long, messageId: Long, chatId: Long, text: String) {
        editMessage(accountId, messageId, text)
        refreshPreview(chatId)
    }

    @Transaction suspend fun deleteAndRefresh(accountId: Long, messageId: Long, chatId: Long) {
        deleteMessage(accountId, messageId)
        refreshPreview(chatId)
    }

    @Transaction suspend fun clearAndRefresh(accountId: Long, chatId: Long) {
        clearMessages(accountId, chatId)
        refreshPreview(chatId)
    }

    suspend fun refreshPreview(chatId: Long) {
        val last = latestMessage(chatId)
        updatePreview(chatId, last?.text.orEmpty(), last?.sentAt ?: 0)
    }
}
