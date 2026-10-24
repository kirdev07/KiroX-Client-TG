package com.kirox.app.core.demo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

@Entity(tableName = "demo_accounts")
data class DemoAccount(
    @PrimaryKey val accountId: Long,
    val name: String,
    val username: String?,
    val initial: String,
    val bio: String = "",
    val premium: Boolean = false,
    val verified: Boolean = false,
    val emojiStatus: String? = null
)

@Entity(
    tableName = "demo_contacts",
    foreignKeys = [ForeignKey(entity = DemoAccount::class, parentColumns = ["accountId"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId")]
)
data class DemoContact(
    @PrimaryKey val contactId: Long,
    val accountId: Long,
    val name: String,
    val normalizedName: String,
    val username: String?,
    val status: String,
    val initial: String
)

@Entity(
    tableName = "demo_chats",
    foreignKeys = [
        ForeignKey(entity = DemoAccount::class, parentColumns = ["accountId"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DemoContact::class, parentColumns = ["contactId"], childColumns = ["contactId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("accountId"), Index("contactId"), Index(value = ["accountId", "contactId"], unique = true)]
)
data class DemoChat(
    @PrimaryKey val chatId: Long,
    val accountId: Long,
    val contactId: Long?,
    val title: String,
    val initial: String,
    val status: String = "был(а) недавно",
    val lastMessage: String = "",
    val lastMessageAt: Long = 0,
    val unreadCount: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val draft: String = "",
    val muted: Boolean = false
)

@Entity(
    tableName = "demo_messages",
    foreignKeys = [ForeignKey(entity = DemoChat::class, parentColumns = ["chatId"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId"), Index("accountId")]
)
data class DemoMessage(
    @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
    val accountId: Long,
    val chatId: Long,
    val author: String,
    val outgoing: Boolean,
    val kind: String = "Text",
    val text: String,
    val sentAt: Long,
    val delivery: String = "Read",
    val quoteAuthor: String? = null,
    val quoteText: String? = null,
    val forwardedFrom: String? = null,
    val pinned: Boolean = false
)

@Entity(
    tableName = "demo_reactions",
    primaryKeys = ["messageId", "emoji"],
    foreignKeys = [ForeignKey(entity = DemoMessage::class, parentColumns = ["messageId"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("messageId"), Index("accountId")]
)
data class DemoReaction(
    val messageId: Long,
    val emoji: String,
    val accountId: Long,
    val count: Int,
    val selected: Boolean
)

data class DemoMessageWithReactions(
    @Embedded val message: DemoMessage,
    @Relation(parentColumn = "messageId", entityColumn = "messageId") val reactions: List<DemoReaction>
)

@Entity(
    tableName = "demo_calls",
    foreignKeys = [ForeignKey(entity = DemoAccount::class, parentColumns = ["accountId"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId"), Index("contactId")]
)
data class DemoCall(
    @PrimaryKey(autoGenerate = true) val callId: Long = 0,
    val accountId: Long,
    val contactId: Long?,
    val name: String,
    val occurredAt: Long,
    val kind: String
)

@Entity(tableName = "demo_meta")
data class DemoMeta(@PrimaryKey val key: String, val value: String)

data class DemoSettings(
    val activeAccountId: Long = DemoSeed.ACCOUNT_PRIMARY,
    val showKiroXBadge: Boolean = true,
    val animationsEnabled: Boolean = true,
    val chatSection: String = "Messages",
    val contactsSection: String = "Contacts",
    val callFilter: String = "Все"
)
