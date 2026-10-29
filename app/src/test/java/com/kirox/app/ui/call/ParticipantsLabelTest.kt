package com.kirox.app.ui.call

import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantsLabelTest {
    @Test fun participantCountsUseRussianPlurals() {
        assertEquals("1 участник", participantsLabel(1))
        assertEquals("2 участника", participantsLabel(2))
        assertEquals("5 участников", participantsLabel(5))
        assertEquals("111 участников", participantsLabel(111))
    }
}
