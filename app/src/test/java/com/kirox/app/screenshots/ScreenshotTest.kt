package com.kirox.app.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.robolectric.ParameterizedRobolectricTestRunner
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
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest(private val palette: FusionPalette) {
    companion object {
        /** Each Fusion UI v2 style gets its own run: one composition per screenshot keeps captures fresh. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun styles(): List<Array<Any>> = listOf(arrayOf(FusionPalette.Expressive), arrayOf(FusionPalette.Glass))
    }

    @get:Rule val rule = createComposeRule()

    private val now = (System.currentTimeMillis() / 1000).toInt()

    private val chats = listOf(
        chat(1, "Избранное", "Список дел на завтра", 0, pinned = true, saved = true),
        chat(2, "Анна", "Увидимся в 7, я возьму билеты", 2, pinned = true),
        chat(3, "Команда KiroX", "Максим: сборка прошла, проверяйте APK", 14, kind = com.kirox.app.telegram.TelegramChatKind.Group),
        chat(4, "Мама", "Позвони, когда будешь дома", 0, state = LastMessageState.Read),
        chat(5, "Дизайн", "Новые иконки уже в папке", 0, muted = true, kind = com.kirox.app.telegram.TelegramChatKind.Group),
        chat(6, "Telegram", "Код для входа: ●●●●●", 1),
        chat(7, "Илья", "Ок 👍", 0, state = LastMessageState.Sent),
        chat(8, "Новости", "Обновление Android 17 уже доступно", 37, muted = true, kind = com.kirox.app.telegram.TelegramChatKind.Channel)
    )

    private fun chat(id: Long, title: String, preview: String, unread: Int, pinned: Boolean = false, saved: Boolean = false, muted: Boolean = false, state: LastMessageState = LastMessageState.None, kind: com.kirox.app.telegram.TelegramChatKind = com.kirox.app.telegram.TelegramChatKind.Private) =
        TelegramChatItem(id, title, preview, now - id.toInt() * 1700, unread, isPinned = pinned, isMuted = muted, outgoingState = state, photo = null, archived = false, isSaved = saved, kind = kind)

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
    private fun Main(tab: Int = 0) = TelegramMainScreen(
        lists = TelegramChatLists(
            main = TelegramChatListState(chats, complete = true),
            archive = TelegramChatListState(listOf(chat(20, "Старый проект", "Архивное сообщение", 3), chat(21, "Рассылка", "Скидки недели", 0)), complete = true),
            folders = listOf(TelegramFolder(1, "Работа"), TelegramFolder(2, "Семья"))
        ),
        chatActions = chatActions, chatError = null, onChatErrorShown = {},
        contacts = listOf(
            com.kirox.app.telegram.TelegramContact(2, "Анна", "anna", com.kirox.app.telegram.TelegramUserStatus.Online, null),
            com.kirox.app.telegram.TelegramContact(4, "Мама", null, com.kirox.app.telegram.TelegramUserStatus.Recently, null),
            com.kirox.app.telegram.TelegramContact(7, "Илья", "ilya", com.kirox.app.telegram.TelegramUserStatus.Offline(now - 3600), null),
            com.kirox.app.telegram.TelegramContact(9, "Максим", "max", com.kirox.app.telegram.TelegramUserStatus.LastWeek, null),
            com.kirox.app.telegram.TelegramContact(10, "Света", null, com.kirox.app.telegram.TelegramUserStatus.Online, null)
        ), contactsLoading = false, profile = profile, connection = TelegramConnection.Ready,
        onOpenChat = {}, onOpenContact = {}, calls = TelegramCallsState(complete = true), onLoadCalls = {},
        storyChats = listOf(
            TelegramStoryChat(2, "Анна", null, listOf(1, 2), hasUnread = true, isSelf = false),
            TelegramStoryChat(9, "Максим", null, listOf(1), hasUnread = false, isSelf = false)
        ),
        storyActions = storyActions, palette = palette, onPalette = {}, onOpenSavedMessages = {},
        seasonal = false, onSeasonal = {},
        bubbleShape = BubbleShape.entries.first(), onBubbleShape = {}, motion = MotionLevel.entries.first(), onMotion = {},
        chatTextSize = ChatTextSize.entries.first(), onChatTextSize = {}, chatWallpaper = ChatWallpaper.entries.first(), onChatWallpaper = {},
        chatDensity = ChatDensity.entries.first(), onChatDensity = {}, effectScene = EffectScene.Main, onEffectScene = {},
        effectsQuality = EffectsQuality.entries.first(), onEffectsQuality = {}, weakDevice = false, onLogOut = {},
        initialTab = tab
    )

    /** Draws [content] in [palette] with animations off, as in the app, and saves `<style>_<name>.png`. */
    private fun shot(name: String, content: @Composable () -> Unit) {
        // Only in the screenshots workflow; the normal unit test run skips it.
        org.junit.Assume.assumeTrue(System.getenv("KIROX_SCREENSHOTS") == "true")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FusionTheme(palette) {
                androidx.compose.runtime.CompositionLocalProvider(com.kirox.app.ui.theme.LocalMotionFactor provides 0f) { content() }
            }
        }
        capture("${palette.name.lowercase()}_$name")
    }

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(1_000)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun chats() = shot("01_chats") { Main(0) }

    @Test fun calls() = shot("02_calls") { Main(1) }

    @Test fun stories() = shot("03_stories") { Main(2) }

    @Test fun settings() = shot("04_settings") { Main(3) }

    @Test fun contacts() = shot("05_contacts") { Main(4) }

    @Test fun profile() = shot("06_profile") { Main(5) }

    @Test fun conversation() = shot("07_chat") {
        val c = Fusion.colors
        fun argb(color: androidx.compose.ui.graphics.Color) = color.toArgbLong()
        val appearance = ChatAppearance(incomingColor = argb(c.incomingBubble), outgoingColor = argb(c.outgoingBubble), inputColor = argb(c.inputBar))
        val messages = listOf(
            MessageUi(1, "Анна", MessageDirection.Incoming, text = "Привет! Ты уже посмотрел новую версию?", time = "18:40"),
            MessageUi(2, "Вы", MessageDirection.Outgoing, text = "Да, Material 3 выглядит отлично 🔥", time = "18:41"),
            MessageUi(3, "Вы", MessageDirection.Outgoing, text = "Навигация с пилюлей и новые кнопки", time = "18:41"),
            MessageUi(4, "Анна", MessageDirection.Incoming, text = "А звонки работают?", time = "18:43"),
            MessageUi(6, "Анна", MessageDirection.Incoming, text = "И видеочаты с показом экрана?", time = "18:43"),
            MessageUi(5, "Вы", MessageDirection.Outgoing, text = "Да, и видео тоже. Вечером созвонимся", time = "18:44")
        )
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        com.kirox.app.ui.components.ChatWallpaperLayer(ChatWallpaper.Theme, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            com.kirox.app.ui.chat.ChatHeader(com.kirox.app.ui.chat.ChatPeerUi(2, "Анна", "в сети", "А", canVoiceCall = true), onBack = {}, onProfile = {}, onOverflow = {})
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(appearance.messageSpacingDp.dp)) {
                item { MessageRow(MessageUi(0, "", MessageDirection.System, text = "Сегодня", time = ""), grouped = false, appearance = appearance, onLongClick = {}, onReply = {}) }
                items(messages.size) { i ->
                    val m = messages[i]
                    val before = messages.getOrNull(i - 1)
                    val after = messages.getOrNull(i + 1)
                    MessageRow(
                        m, grouped = before?.direction == m.direction, appearance = appearance, onLongClick = {}, onReply = {},
                        groupedBelow = after?.direction == m.direction
                    )
                }
            }
            ChatComposer(TextFieldValue(""), null, null, {}, {}, {}, {}, {}, {}, appearance)
        }
        }
    }

    @Test fun login() = shot("08_login") {
        PhoneLoginScreen(PhoneEntryState.create(ApplicationProvider.getApplicationContext()), onChooseCountry = {})
    }

    @Test fun call() = shot("09_call") {
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

private fun androidx.compose.ui.graphics.Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
