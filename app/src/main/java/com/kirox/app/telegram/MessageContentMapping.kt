package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi

/** Kind of a TDLib message content; everything not drawn by KiroX yet is [TelegramMessageKind.Other]. */
internal fun kindOf(content: TdApi.MessageContent?): TelegramMessageKind = when (content) {
    is TdApi.MessageText -> TelegramMessageKind.Text
    is TdApi.MessagePhoto -> TelegramMessageKind.Photo
    is TdApi.MessageVideo -> TelegramMessageKind.Video
    is TdApi.MessageAnimation -> TelegramMessageKind.Animation
    is TdApi.MessageVideoNote -> TelegramMessageKind.VideoNote
    is TdApi.MessageVoiceNote -> TelegramMessageKind.Voice
    is TdApi.MessageAudio -> TelegramMessageKind.Audio
    is TdApi.MessageDocument -> TelegramMessageKind.Document
    is TdApi.MessageSticker -> TelegramMessageKind.Sticker
    else -> TelegramMessageKind.Other
}

/** Caption or text with its formatting; null for contents without text. */
internal fun formattedTextOf(content: TdApi.MessageContent?): TdApi.FormattedText? = when (content) {
    is TdApi.MessageText -> content.text
    is TdApi.MessagePhoto -> content.caption
    is TdApi.MessageVideo -> content.caption
    is TdApi.MessageAnimation -> content.caption
    is TdApi.MessageAudio -> content.caption
    is TdApi.MessageDocument -> content.caption
    is TdApi.MessageVoiceNote -> content.caption
    else -> null
}

/** Main file of the message with its metadata, and a preview image when there is one. */
internal fun mediaOf(content: TdApi.MessageContent?): Pair<TelegramMedia?, TelegramFileRef?> = when (content) {
    is TdApi.MessageVideo -> content.video.let { v ->
        TelegramMedia(v.video.toRef(), v.video.sizeOrExpected(), v.duration, v.width, v.height, v.mimeType.orEmpty(), v.fileName.orEmpty()) to v.thumbnail?.file?.toRef()
    }
    is TdApi.MessageAnimation -> content.animation.let { a ->
        TelegramMedia(a.animation.toRef(), a.animation.sizeOrExpected(), a.duration, a.width, a.height, a.mimeType.orEmpty(), a.fileName.orEmpty()) to a.thumbnail?.file?.toRef()
    }
    is TdApi.MessageVideoNote -> content.videoNote.let { n ->
        TelegramMedia(n.video.toRef(), n.video.sizeOrExpected(), n.duration, n.length, n.length, "video/mp4") to n.thumbnail?.file?.toRef()
    }
    is TdApi.MessageAudio -> content.audio.let { a ->
        TelegramMedia(
            a.audio.toRef(), a.audio.sizeOrExpected(), a.duration, mimeType = a.mimeType.orEmpty(),
            title = a.title.orEmpty().ifBlank { a.fileName.orEmpty() }, performer = a.performer.orEmpty()
        ) to a.albumCoverThumbnail?.file?.toRef()
    }
    is TdApi.MessageDocument -> content.document.let { d ->
        TelegramMedia(d.document.toRef(), d.document.sizeOrExpected(), mimeType = d.mimeType.orEmpty(), title = d.fileName.orEmpty()) to d.thumbnail?.file?.toRef()
    }
    is TdApi.MessageSticker -> stickerMedia(content.sticker)
    is TdApi.MessageVoiceNote -> content.voiceNote.let { v ->
        TelegramMedia(v.voice.toRef(), v.voice.sizeOrExpected(), v.duration, mimeType = v.mimeType.orEmpty(), waveform = decodeWaveform(v.waveform)) to null
    }
    else -> null to null
}

/** Sticker as message media; also used by the sticker panel. */
internal fun stickerMedia(s: TdApi.Sticker): Pair<TelegramMedia, TelegramFileRef?> = TelegramMedia(
    s.sticker.toRef(), s.sticker.sizeOrExpected(), width = s.width, height = s.height,
    title = s.emoji.orEmpty(), animatedSticker = s.format !is TdApi.StickerFormatWebp, lottieSticker = s.format is TdApi.StickerFormatTgs
) to s.thumbnail?.file?.toRef()

/** Telegram's voice waveform: 5-bit values packed little-endian into bytes. */
internal fun decodeWaveform(bytes: ByteArray?): List<Int> {
    if (bytes == null || bytes.isEmpty()) return emptyList()
    val count = bytes.size * 8 / 5
    return List(count) { i ->
        val bit = i * 5
        val index = bit / 8
        val low = bytes[index].toInt() and 0xFF
        val high = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else 0
        ((low or (high shl 8)) shr (bit % 8)) and 0x1F
    }
}

private fun TdApi.File.sizeOrExpected(): Long = if (size > 0) size else expectedSize

/** KiroX entities for the formatting KiroX draws; others (custom emoji, dates…) stay plain text. */
internal fun entitiesOf(text: TdApi.FormattedText?): List<TelegramTextEntity> =
    text?.entities.orEmpty().mapNotNull { entity ->
        val type: TelegramEntityType = when (entity.type) {
            is TdApi.TextEntityTypeBold -> TelegramEntityType.Bold
            is TdApi.TextEntityTypeItalic -> TelegramEntityType.Italic
            is TdApi.TextEntityTypeUnderline -> TelegramEntityType.Underline
            is TdApi.TextEntityTypeStrikethrough -> TelegramEntityType.Strike
            is TdApi.TextEntityTypeCode -> TelegramEntityType.Code
            is TdApi.TextEntityTypePre, is TdApi.TextEntityTypePreCode -> TelegramEntityType.Pre
            is TdApi.TextEntityTypeSpoiler -> TelegramEntityType.Spoiler
            is TdApi.TextEntityTypeUrl -> TelegramEntityType.Url
            is TdApi.TextEntityTypeTextUrl -> TelegramEntityType.TextUrl
            is TdApi.TextEntityTypeMention -> TelegramEntityType.Mention
            is TdApi.TextEntityTypeMentionName -> TelegramEntityType.MentionName
            is TdApi.TextEntityTypeHashtag -> TelegramEntityType.Hashtag
            is TdApi.TextEntityTypeEmailAddress -> TelegramEntityType.Email
            is TdApi.TextEntityTypePhoneNumber -> TelegramEntityType.Phone
            is TdApi.TextEntityTypeBotCommand -> TelegramEntityType.BotCommand
            is TdApi.TextEntityTypeBlockQuote, is TdApi.TextEntityTypeExpandableBlockQuote -> TelegramEntityType.Quote
            else -> return@mapNotNull null
        }
        val url = when (val t = entity.type) {
            is TdApi.TextEntityTypeTextUrl -> t.url
            is TdApi.TextEntityTypeMentionName -> "tg://user?id=${t.userId}"
            else -> null
        }
        TelegramTextEntity(entity.offset, entity.length, type, url)
    }


internal fun pollOf(content: TdApi.MessageContent?): TelegramPoll? {
    val poll = (content as? TdApi.MessagePoll)?.poll ?: return null
    val quiz = poll.type as? TdApi.PollTypeQuiz
    val voted = poll.options.any { it.isChosen }
    return TelegramPoll(
        question = poll.question?.text.orEmpty(),
        options = poll.options.mapIndexed { index, option ->
            TelegramPollOption(option.text?.text.orEmpty(), option.votePercentage, option.voterCount, option.isChosen, quiz?.correctOptionIds?.contains(index) == true)
        },
        totalVoters = poll.totalVoterCount,
        isClosed = poll.isClosed,
        multipleAnswers = poll.allowsMultipleAnswers,
        isQuiz = quiz != null,
        isAnonymous = poll.isAnonymous,
        canVote = !poll.isClosed && poll.voteRestrictionReason == null && (!voted || poll.allowsRevoting),
        voted = voted
    )
}

/** Inline keyboard of a bot message; callback and URL buttons work, others are shown disabled. */
internal fun inlineButtonsOf(markup: TdApi.ReplyMarkup?): List<List<TelegramButton>> =
    (markup as? TdApi.ReplyMarkupInlineKeyboard)?.rows.orEmpty().map { row ->
        row.map { button ->
            when (val type = button.type) {
                is TdApi.InlineKeyboardButtonTypeCallback -> TelegramButton(button.text, TelegramButton.Kind.Callback, data = type.data)
                is TdApi.InlineKeyboardButtonTypeUrl -> TelegramButton(button.text, TelegramButton.Kind.Url, url = type.url)
                else -> TelegramButton(button.text, TelegramButton.Kind.Other)
            }
        }
    }.filter { it.isNotEmpty() }

/** Texts of a custom reply keyboard. */
internal fun replyKeyboardOf(markup: TdApi.ReplyMarkup?): List<List<String>>? = when (markup) {
    is TdApi.ReplyMarkupShowKeyboard -> markup.rows.map { row -> row.map { it.text } }.filter { it.isNotEmpty() }
    is TdApi.ReplyMarkupRemoveKeyboard -> emptyList()
    else -> null
}
