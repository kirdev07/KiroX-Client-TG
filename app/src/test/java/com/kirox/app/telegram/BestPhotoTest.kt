package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class BestPhotoTest {
    private fun size(width: Int) = TdApi.PhotoSize("x", TdApi.File(), width, width, IntArray(0))
    private fun photo(vararg widths: Int) = TdApi.Photo(false, null, widths.map(::size).toTypedArray())

    @Test fun picksLargestUpTo1280() {
        assertEquals(1280, bestPhoto(photo(90, 320, 800, 1280, 2560))?.width)
    }

    @Test fun fallsBackToSmallestWhenAllAreHuge() {
        assertEquals(2000, bestPhoto(photo(4000, 2000))?.width)
    }
}
