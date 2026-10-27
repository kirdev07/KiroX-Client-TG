package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentMappingTest {
    private fun file(id: Int, size: Long) = TdApi.File().apply { this.id = id; this.size = size; local = TdApi.LocalFile(); remote = TdApi.RemoteFile() }

    @Test fun everyMediaKindHasItsOwnKind() {
        assertEquals(TelegramMessageKind.Video, kindOf(TdApi.MessageVideo()))
        assertEquals(TelegramMessageKind.Animation, kindOf(TdApi.MessageAnimation()))
        assertEquals(TelegramMessageKind.VideoNote, kindOf(TdApi.MessageVideoNote()))
        assertEquals(TelegramMessageKind.Audio, kindOf(TdApi.MessageAudio()))
        assertEquals(TelegramMessageKind.Sticker, kindOf(TdApi.MessageSticker()))
        assertEquals(TelegramMessageKind.Other, kindOf(TdApi.MessagePoll()))
    }

    @Test fun documentKeepsNameSizeAndType() {
        val content = TdApi.MessageDocument(TdApi.Document("отчёт.pdf", "application/pdf", null, null, file(7, 2048)), TdApi.FormattedText("", emptyArray()))
        val (media, thumbnail) = mediaOf(content)
        assertEquals(7, media!!.file.id)
        assertEquals("отчёт.pdf", media.title)
        assertEquals(2048L, media.size)
        assertEquals("application/pdf", media.mimeType)
        assertNull(thumbnail)
    }

    @Test fun animatedStickersAreMarked() {
        val tgs = TdApi.MessageSticker(TdApi.Sticker().apply { sticker = file(1, 10); format = TdApi.StickerFormatTgs(); emoji = "😀" }, false)
        val webp = TdApi.MessageSticker(TdApi.Sticker().apply { sticker = file(2, 10); format = TdApi.StickerFormatWebp(); emoji = "😀" }, false)
        assertTrue(mediaOf(tgs).first!!.animatedSticker)
        assertEquals(false, mediaOf(webp).first!!.animatedSticker)
    }

    @Test fun formattingIsKeptAndUnknownEntitiesSkipped() {
        val text = TdApi.FormattedText("жирный ссылка эмодзи", arrayOf(
            TdApi.TextEntity(0, 6, TdApi.TextEntityTypeBold()),
            TdApi.TextEntity(7, 6, TdApi.TextEntityTypeTextUrl("https://kirox.app")),
            TdApi.TextEntity(14, 6, TdApi.TextEntityTypeCustomEmoji(1))
        ))
        val entities = entitiesOf(text)
        assertEquals(2, entities.size)
        assertEquals(TelegramEntityType.Bold, entities[0].type)
        assertEquals("https://kirox.app", entities[1].url)
    }
}
