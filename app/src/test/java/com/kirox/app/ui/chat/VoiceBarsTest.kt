package com.kirox.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceBarsTest {
    @Test fun missingWaveformIsAFlatLine() {
        val bars = voiceBars(emptyList(), count = 5)
        assertEquals(5, bars.size)
        assertEquals(true, bars.all { it == 0.1f })
    }

    @Test fun barsAreScaledToTheLoudestPeak() {
        val bars = voiceBars(listOf(0, 10, 20, 31), count = 4)
        assertEquals(listOf(0f, 10f / 31, 20f / 31, 1f), bars)
    }
}
