package com.kirox.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {
    @Test fun emptyWaveformDecodesToNothing() {
        assertEquals(emptyList<Int>(), decodeWaveform(null))
        assertEquals(emptyList<Int>(), decodeWaveform(ByteArray(0)))
    }

    @Test fun fiveBitValuesAreUnpackedLittleEndian() {
        // 31, 0, 17 packed: bits 0-4 = 11111, 5-9 = 00000, 10-14 = 10001.
        val packed = 31 or (0 shl 5) or (17 shl 10)
        val bytes = byteArrayOf((packed and 0xFF).toByte(), (packed shr 8 and 0xFF).toByte())
        assertEquals(listOf(31, 0, 17), decodeWaveform(bytes))
    }
}
