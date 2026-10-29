package com.kirox.app.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kirox.app.phone.PhoneEntryState
import com.kirox.app.settings.BubbleShape
import com.kirox.app.settings.ChatDensity
import com.kirox.app.settings.ChatTextSize
import com.kirox.app.settings.ChatWallpaper
import com.kirox.app.settings.EffectScene
import com.kirox.app.settings.EffectsQuality
import com.kirox.app.settings.MotionLevel
import com.kirox.app.telegram.CallPhase
import com.kirox.app.telegram.LastMessageState
import com.kirox.app.telegram.TelegramActiveCall
import com.kirox.app.telegram.TelegramCallsState
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramChatListKey
import com.kirox.app.telegram.TelegramChatListState
import com.kirox.app.telegram.TelegramChatLists
import com.kirox.app.telegram.TelegramConnection
import com.kirox.app.telegram.TelegramFolder
import com.kirox.app.telegram.TelegramStoryChat
import com.kirox.app.telegram.TelegramUserProfile
import com.kirox.app.ui.call.CallActions
import com.kirox.app.ui.call.CallScreen
import com.kirox.app.ui.chat.ChatAppearance
import com.kirox.app.ui.chat.ChatComposer
import com.kirox.app.ui.chat.MessageDirection
import com.kirox.app.ui.chat.MessageRow
import com.kirox.app.ui.chat.MessageUi
import com.kirox.app.ui.home.TelegramChatListActions
import com.kirox.app.ui.home.TelegramMainScreen
import com.kirox.app.ui.home.TelegramStoryActions
import com.kirox.app.ui.start.PhoneLoginScreen
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionPalette
import com.kirox.app.ui.theme.FusionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens with sample data to PNG files in build/screenshots (Robolectric, no
 * device). Only a look at the layout: no Telegram connection, photos or real data.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private val chats = listOf(
        chat(1, "Избранное", "Список дел на завтра", 0, pinned = true, saved = true),
        chat(2, "Анна", "Увидимся в 7, я возьму билеты", 2, pinned = true),
        chat(3, "Команда KiroX", "Максим: сборка прошла, проверяйте APK", 14),
        chat(4, "Мама", "Позвони, когда будешь дома", 0, state = LastMessageState.Read),
        chat(5, "Дизайн", "Новые иконки уже в папке", 0, muted = true),
        chat(6, "Telegram", "Код для входа: ●●●●●", 1),
        chat(7, "Илья", "Ок 👍", 0, state = LastMessageState.Sent),
        chat(8, "Новости", "Обновление Android 17 уже доступно", 37, muted = true)
    )

    private fun chat(id: Long, title: String, preview: String, unread: Int, pinned: Boolean = false, saved: Boolean = false, muted: Boolean = false, state: LastMessageState = LastMessageState.None) =
        TelegramChatItem(id, title, preview, now - id.toInt() * 1700, unread, isPinned = pinned, isMuted = muted, outgoingState = state, photo = null, archived = false, isSaved = saved)

    private val profile = TelegramUserProfile(1, "Кирилл", "kirdev07", "77011234567", bio = "Android-разработчик", photo = null, firstName = "Кирилл")

    private val chatActions = object : TelegramChatListActions {
        override fun loadMore(key: TelegramChatListKey) {}
        override fun setArchived(chatId: Long, archived: Boolean) {}
        override fun setPinned(key: TelegramChatListKey, chatId: Long, pinned: Boolean) {}
        override fun markRead(chatId: Long) {}
        override fun markUnread(chatId: Long) {}
        override suspend fun search(query: String) = emptyList<TelegramChatItem>()
    }

    private val storyActions = object : TelegramStoryActions {
        override suspend fun story(chatId: Long, storyId: Int) = null
        override fun open(chatId: Long, storyId: Int) {}
        override fun close(chatId: Long, storyId: Int) {}
    }

    @Composable
    private fun Main(tab: Int = 1) = TelegramMainScreen(
        lists = TelegramChatLists(
            main = TelegramChatListState(chats, complete = true),
            archive = TelegramChatListState(complete = true),
            folders = listOf(TelegramFolder(1, "Работа"), TelegramFolder(2, "Семья"))
        ),
        chatActions = chatActions, chatError = null, onChatErrorShown = {},
        contacts = emptyList(), contactsLoading = false, profile = profile, connection = TelegramConnection.Ready,
        onOpenChat = {}, onOpenContact = {}, calls = TelegramCallsState(complete = true), onLoadCalls = {},
        storyChats = listOf(TelegramStoryChat(2, "Анна", null, listOf(1), hasUnread = true, isSelf = false)),
        storyActions = storyActions, palette = FusionPalette.AmoledBlack, onPalette = {}, onOpenSavedMessages = {},
        seasonal = false, onSeasonal = {},
        bubbleShape = BubbleShape.entries.first(), onBubbleShape = {}, motion = MotionLevel.entries.first(), onMotion = {},
        chatTextSize = ChatTextSize.entries.first(), onChatTextSize = {}, chatWallpaper = ChatWallpaper.entries.first(), onChatWallpaper = {},
        chatDensity = ChatDensity.entries.first(), onChatDensity = {}, effectScene = EffectScene.Main, onEffectScene = {},
        effectsQuality = EffectsQuality.entries.first(), onEffectsQuality = {}, weakDevice = false, onLogOut = {},
        initialTab = tab
    )

    /** Draws [content] for a moment (animations are stepped, not run to the end) and saves it. */
    private fun shot(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        // Only in the screenshots workflow; the normal unit test run skips it.
        org.junit.Assume.assumeTrue(System.getenv("KIROX_SCREENSHOTS") == "true")
        rule.mainClock.autoAdvance = false
        rule.setContent { FusionTheme(FusionPalette.AmoledBlack) { content() } }
        rule.mainClock.advanceTimeBy(1_000)
        before()
        rule.mainClock.advanceTimeBy(1_000)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun chats() = shot("01_chats") { Main() }

    @Test fun settings() = shot("02_settings") { Main(tab = 2) }

    @Test fun profile() = shot("03_profile") { Main(tab = 3) }

    @Test fun contacts() = shot("04_contacts") { Main(tab = 0) }

    @Test fun conversation() = shot("05_chat") {
        val appearance = ChatAppearance()
        val messages = listOf(
            MessageUi(1, "Анна", MessageDirection.Incoming, text = "Привет! Ты уже посмотрел новую версию?", time = "18:40"),
            MessageUi(2, "Вы", MessageDirection.Outgoing, text = "Да, Material 3 выглядит отлично 🔥", time = "18:41"),
            MessageUi(3, "Вы", MessageDirection.Outgoing, text = "Навигация с пилюлей и новые кнопки", time = "18:41"),
            MessageUi(4, "Анна", MessageDirection.Incoming, text = "А звонки работают?", time = "18:43"),
            MessageUi(5, "Вы", MessageDirection.Outgoing, text = "Да, и видео тоже. Вечером созвонимся", time = "18:44")
        )
        Column(Modifier.fillMaxSize().background(Fusion.colors.background)) {
            com.kirox.app.ui.home.BackTitle("Анна") {}
            LazyColumn(Modifier.weight(1f)) {
                items(messages) { MessageRow(it, grouped = false, appearance = appearance, onLongClick = {}, onReply = {}) }
            }
            ChatComposer(TextFieldValue(""), null, null, {}, {}, {}, {}, {}, {}, appearance)
        }
    }

    @Test fun login() = shot("06_login") {
        PhoneLoginScreen(PhoneEntryState.create(ApplicationProvider.getApplicationContext()), onChooseCountry = {})
    }

    @Test fun call() = shot("07_call") {
        CallScreen(
            TelegramActiveCall(7, 2, "Анна", null, isOutgoing = true, isVideo = false, phase = CallPhase.Active(System.currentTimeMillis() - 95_000), emojis = listOf("🐶", "🚀", "🍀", "🎧")),
            object : CallActions {
                override fun accept() {}
                override fun hangUp() {}
                override fun dismiss() {}
                override fun setMuted(muted: Boolean) {}
                override fun setCamera(on: Boolean) {}
                override fun switchCamera() {}
                override fun setVideoSinks(remote: org.webrtc.VideoSink?, local: org.webrtc.VideoSink?) {}
            }
        )
    }
}
