package com.kirox.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class PollLabelTest {
    @Test fun voterCountsUseRussianPlurals() {
        assertEquals("Нет голосов", pollVotersLabel(0))
        assertEquals("1 голос", pollVotersLabel(1))
        assertEquals("4 голоса", pollVotersLabel(4))
        assertEquals("12 голосов", pollVotersLabel(12))
    }
}
