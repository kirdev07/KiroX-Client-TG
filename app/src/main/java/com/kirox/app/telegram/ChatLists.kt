package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi

/** TDLib chat lists are equal when they are the same kind and, for folders, the same folder id. */
internal fun sameChatList(a: TdApi.ChatList, b: TdApi.ChatList): Boolean = when (a) {
    is TdApi.ChatListFolder -> b is TdApi.ChatListFolder && a.chatFolderId == b.chatFolderId
    else -> a.javaClass == b.javaClass
}

/** Replaces the position for [position]'s list; order 0 means the chat left that list. */
internal fun applyChatPosition(positions: Array<TdApi.ChatPosition>, position: TdApi.ChatPosition): Array<TdApi.ChatPosition> {
    val others = positions.filterNot { sameChatList(it.list, position.list) }
    return (if (position.order == 0L) others else others + position).toTypedArray()
}
