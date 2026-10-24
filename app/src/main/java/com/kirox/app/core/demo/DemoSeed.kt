package com.kirox.app.core.demo

object DemoSeed {
    const val ACCOUNT_PRIMARY = 1L
    const val ACCOUNT_SECONDARY = 2L
    const val SEED_KEY = "demo_seed_version"
    const val VERSION = "1"
    private const val baseTime = 1_790_000_000_000L

    val accounts = listOf(
        DemoAccount(ACCOUNT_PRIMARY, "Кирилл", "@username", "К", "Пользователь KiroX"),
        DemoAccount(ACCOUNT_SECONDARY, "Алексей Demo", "@alex_demo", "А", "Второй изолированный аккаунт")
    )

    val contacts = listOf(
        contact(101, 1, "Алексей", "@alexey", "в сети"), contact(102, 1, "Алина", "@alina", "был(а) недавно"),
        contact(103, 1, "Дмитрий", "@dmitry", "в сети"), contact(104, 1, "Мария", "@maria", "был(а) недавно"),
        contact(105, 1, "Богдан", null, "был(а) вчера"),
        contact(201, 2, "Наталья", "@nat_demo", "в сети"), contact(202, 2, "Команда KiroX", null, "3 участника")
    )

    val chats = listOf(
        chat(10001, 1, null, "Избранное", "И", "Сохранённые сообщения", 8, pinned = true),
        chat(10002, 1, null, "KirDev Studio", "K", "Новости и обновления", 7, pinned = true),
        chat(10101, 1, 101, "Алексей", "А", "До встречи", 6),
        chat(10102, 1, 102, "Алина", "А", "Фото", 5, unread = 2),
        chat(10103, 1, 103, "Дмитрий", "Д", "Документы готовы", 4),
        chat(10104, 1, 104, "Мария", "М", "Спасибо!", 3, archived = true),
        chat(10105, 1, 105, "Богдан", "Б", "На связи", 2, archived = true),
        chat(20201, 2, 201, "Наталья", "Н", "Второй аккаунт", 4, unread = 1),
        chat(20202, 2, 202, "Команда KiroX", "К", "Отдельные DEMO-данные", 3)
    )

    val messages = chats.flatMap { chat ->
        listOf(
            DemoMessage(chat.chatId * 100 + 1, chat.accountId, chat.chatId, chat.title, false, text = "Привет! Это изолированная DEMO-переписка.", sentAt = chat.lastMessageAt - 60_000),
            DemoMessage(chat.chatId * 100 + 2, chat.accountId, chat.chatId, "Вы", true, text = chat.lastMessage, sentAt = chat.lastMessageAt)
        )
    }

    val calls = listOf(
        call(1, 1, 101, "Алексей", "incoming", 8), call(2, 1, 104, "Мария", "missed", 7),
        call(3, 1, 103, "Дмитрий", "outgoing", 6), call(4, 2, 201, "Наталья", "incoming", 5)
    )

    private fun contact(id: Long, account: Long, name: String, username: String?, status: String) =
        DemoContact(id, account, name, name.lowercase(), username, status, name.take(1).uppercase())

    private fun chat(id: Long, account: Long, contact: Long?, title: String, initial: String, preview: String, order: Int, unread: Int = 0, pinned: Boolean = false, archived: Boolean = false) =
        DemoChat(id, account, contact, title, initial, lastMessage = preview, lastMessageAt = baseTime + order * 60_000, unreadCount = unread, pinned = pinned, archived = archived)

    private fun call(id: Long, account: Long, contact: Long?, name: String, kind: String, order: Int) =
        DemoCall(id, account, contact, name, baseTime + order * 60_000, kind)
}
