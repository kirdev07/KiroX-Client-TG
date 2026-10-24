package com.kirox.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirox.app.telegram.TelegramChatHistory
import com.kirox.app.telegram.TelegramDelivery
import com.kirox.app.telegram.TelegramMessage
import com.kirox.app.telegram.TelegramMessageKind
import com.kirox.app.telegram.TelegramPeerKind
import com.kirox.app.ui.chat.TelegramChatActions
import com.kirox.app.ui.chat.TelegramChatScreen
import com.kirox.app.ui.components.FusionBottomBar
import com.kirox.app.ui.components.FusionNavItem
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.FusionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI checks on fake data: no TDLib, no network, no account. */
@RunWith(AndroidJUnit4::class)
class FusionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bottomBarSelectsTappedTab() {
        compose.setContent {
            FusionTheme {
                var selected by remember { mutableIntStateOf(1) }
                FusionBottomBar(
                    listOf(FusionNavItem("Контакты", KiroXIcon.Contacts), FusionNavItem("Чаты", KiroXIcon.Chat), FusionNavItem("Настройки", KiroXIcon.Settings)),
                    selected, { selected = it }
                )
            }
        }
        compose.onNodeWithText("Чаты").assertIsSelected()
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Настройки").assertIsSelected()
        compose.onNodeWithText("Чаты").assertIsNotSelected()
    }

    @Test fun chatScreenShowsMessagesAndSendsText() {
        val sent = mutableListOf<String>()
        val actions = object : TelegramChatActions {
            override fun loadOlder() {}
            override fun sendText(text: String, replyToMessageId: Long?) { sent += text }
            override fun sendPhoto(path: String, caption: String) {}
            override fun sendDocument(path: String, caption: String) {}
            override fun edit(messageId: Long, text: String) {}
            override fun delete(messageIds: List<Long>, forEveryone: Boolean) {}
            override fun forward(toChatId: Long, messageIds: List<Long>) {}
        }
        val history = TelegramChatHistory(
            chatId = 1, title = "Тестовый чат", peerKind = TelegramPeerKind.User, reachedStart = true,
            messages = listOf(
                TelegramMessage(1, 1, "Анна", false, TelegramMessageKind.Text, "Привет из теста", 1_790_000_000, false, TelegramDelivery.None),
                TelegramMessage(2, 1, "Вы", true, TelegramMessageKind.Text, "Ответ из теста", 1_790_000_060, false, TelegramDelivery.Read)
            )
        )
        compose.setContent { FusionTheme { TelegramChatScreen(history, null, {}, emptyList(), actions, onBack = {}) } }
        compose.onNodeWithText("Тестовый чат").assertExists()
        compose.onNodeWithText("Привет из теста").assertExists()
        compose.onNodeWithText("Ответ из теста").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Новое сообщение")
        compose.onNode(hasContentDescription("Отправить")).performClick()
        compose.runOnIdle { assertEquals(listOf("Новое сообщение"), sent) }
    }
}
