package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Active stories of the main story list, and on-demand story content for the viewer. View only. */
class TelegramStoryRepository(
    private val client: TelegramClient,
    private val chats: TelegramChatRepository,
    private val users: TelegramUserRepository
) {
    private val active = HashMap<Long, TdApi.ChatActiveStories>()
    private var publishScheduled = false

    private val _chats = MutableStateFlow<List<TelegramStoryChat>>(emptyList())
    val storyChats: StateFlow<List<TelegramStoryChat>> = _chats.asStateFlow()

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                    is TdApi.AuthorizationStateReady -> loadActive(remaining = MAX_PAGES)
                    is TdApi.AuthorizationStateClosed -> if (!client.paused) { active.clear(); _chats.value = emptyList() }
                }
                is TdApi.UpdateChatActiveStories -> {
                    val stories = update.activeStories
                    if (stories.list == null || stories.stories.isEmpty()) active.remove(stories.chatId) else active[stories.chatId] = stories
                    schedulePublish()
                }
                is TdApi.UpdateChatTitle, is TdApi.UpdateChatPhoto -> if (active.isNotEmpty()) schedulePublish()
            }
        }
    }

    /** Posts a 24-hour story to the own profile; [everyone] = false shows it to contacts only. */
    suspend fun postStory(path: String, isVideo: Boolean, durationS: Int, caption: String, everyone: Boolean) {
        val file = TdApi.InputFileLocal(path)
        val content: TdApi.InputStoryContent =
            if (isVideo) TdApi.InputStoryContentVideo(file, IntArray(0), durationS.toDouble(), 0.0, false)
            else TdApi.InputStoryContentPhoto(file, IntArray(0))
        val privacy: TdApi.StoryPrivacySettings =
            if (everyone) TdApi.StoryPrivacySettingsEveryone(LongArray(0)) else TdApi.StoryPrivacySettingsContacts(LongArray(0))
        val text = caption.takeIf { it.isNotBlank() }?.let { TdApi.FormattedText(it, emptyArray()) }
        client.query(TdApi.PostStory(users.myId(), content, null, text, privacy, IntArray(0), STORY_PERIOD_S, null, true, false))
    }

    suspend fun story(chatId: Long, storyId: Int): TelegramStory? =
        runCatching { client.query(TdApi.GetStory(chatId, storyId, false)) }.getOrNull()?.let(::toStory)

    /** Marks the story as viewed while it is on screen. */
    fun open(chatId: Long, storyId: Int) { client.send(TdApi.OpenStory(chatId, storyId)) }
    fun close(chatId: Long, storyId: Int) { client.send(TdApi.CloseStory(chatId, storyId)) }

    /** Active stories arrive as updates; LoadActiveStories pages until TDLib answers 404. */
    private fun loadActive(remaining: Int) {
        if (remaining <= 0) return
        client.send(TdApi.LoadActiveStories(TdApi.StoryListMain())) { result ->
            if (result is TdApi.Ok) loadActive(remaining - 1)
        }
    }

    private fun schedulePublish() {
        if (publishScheduled) return
        publishScheduled = true
        client.post(PUBLISH_DELAY_MS) {
            publishScheduled = false
            val myChatId = users.myId()
            _chats.value = active.values.filter { it.list is TdApi.StoryListMain }.sortedByDescending { it.order }.map { stories ->
                val chat = chats.chat(stories.chatId)
                TelegramStoryChat(
                    chatId = stories.chatId,
                    title = chat?.title.orEmpty(),
                    photo = chat?.photo?.small?.toRef(),
                    storyIds = stories.stories.sortedBy { it.date }.map { it.storyId },
                    hasUnread = stories.stories.any { it.storyId > stories.maxReadStoryId },
                    // A user's private chat id equals their user id.
                    isSelf = stories.chatId == myChatId
                )
            }
        }
    }

    /** Sets or (with null) removes our emoji reaction on a story. */
    suspend fun react(chatId: Long, storyId: Int, emoji: String?) {
        client.query(TdApi.SetStoryReaction(chatId, storyId, emoji?.let { TdApi.ReactionTypeEmoji(it) }, true))
    }

    /** Answers a story in a private message to its poster. */
    suspend fun reply(chatId: Long, storyId: Int, text: String) {
        client.query(TdApi.SendMessage(chatId, null, TdApi.InputMessageReplyToStory(chatId, storyId), null, null,
            TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true)))
    }

    suspend fun delete(chatId: Long, storyId: Int) { client.query(TdApi.DeleteStory(chatId, storyId)) }

    /** Who viewed an own story (and their reactions), newest first, up to [limit]. */
    suspend fun viewers(storyId: Int, limit: Int = 100): List<TelegramStoryViewer> {
        val found = client.query(TdApi.GetStoryInteractions(storyId, "", false, false, true, "", limit))
        return found.interactions.mapNotNull { interaction ->
            val userId = (interaction.actorId as? TdApi.MessageSenderUser)?.userId ?: return@mapNotNull null
            val user = runCatching { client.query(TdApi.GetUser(userId)) }.getOrNull() ?: return@mapNotNull null
            TelegramStoryViewer(
                listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" "),
                user.profilePhoto?.small?.toRef(),
                interaction.interactionDate,
                ((interaction.type as? TdApi.StoryInteractionTypeView)?.chosenReactionType as? TdApi.ReactionTypeEmoji)?.emoji
            )
        }
    }

    private companion object {
        const val STORY_PERIOD_S = 86400
        const val MAX_PAGES = 5
        const val PUBLISH_DELAY_MS = 50L
    }
}

internal fun toStory(story: TdApi.Story): TelegramStory {
    val content = story.content
    val photo = (content as? TdApi.StoryContentPhoto)?.photo?.let(::bestPhoto)
    val video = (content as? TdApi.StoryContentVideo)?.video
    return TelegramStory(
        id = story.id,
        chatId = story.posterChatId,
        date = story.date,
        image = photo?.photo?.toRef() ?: video?.thumbnail?.file?.toRef(),
        aspect = when {
            photo != null && photo.height > 0 -> photo.width.toFloat() / photo.height
            video != null && video.height > 0 -> video.width.toFloat() / video.height
            else -> 9f / 16f
        },
        isVideo = video != null,
        caption = story.caption?.text.orEmpty(),
        video = video?.video?.toRef(),
        durationS = video?.duration ?: 0.0,
        canReply = story.canBeReplied,
        canDelete = story.canBeDeleted,
        canSeeViews = story.canGetInteractions,
        viewCount = story.interactionInfo?.viewCount ?: 0,
        chosenReaction = (story.chosenReactionType as? TdApi.ReactionTypeEmoji)?.emoji
    )
}
