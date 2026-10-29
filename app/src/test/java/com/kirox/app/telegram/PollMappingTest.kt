package com.kirox.app.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollMappingTest {
    private fun option(text: String, percent: Int, chosen: Boolean) =
        TdApi.PollOption().apply { this.text = TdApi.FormattedText(text, emptyArray()); votePercentage = percent; isChosen = chosen }

    private fun poll(closed: Boolean, chosenFirst: Boolean, quiz: Boolean = false) = TdApi.MessagePoll().apply {
        poll = TdApi.Poll().apply {
            question = TdApi.FormattedText("Куда идём?", emptyArray())
            options = arrayOf(option("Кино", 60, chosenFirst), option("Парк", 40, false))
            totalVoterCount = 5
            isClosed = closed
            type = if (quiz) TdApi.PollTypeQuiz(intArrayOf(1), null, null) else TdApi.PollTypeRegular()
        }
    }

    @Test fun openUnvotedPollCanBeVoted() {
        val mapped = pollOf(poll(closed = false, chosenFirst = false))!!
        assertTrue(mapped.canVote)
        assertFalse(mapped.voted)
        assertEquals(listOf("Кино", "Парк"), mapped.options.map { it.text })
    }

    @Test fun votedOrClosedPollShowsResultsOnly() {
        assertFalse(pollOf(poll(closed = false, chosenFirst = true))!!.canVote)
        assertFalse(pollOf(poll(closed = true, chosenFirst = false))!!.canVote)
    }

    @Test fun quizMarksTheRightAnswer() {
        val mapped = pollOf(poll(closed = true, chosenFirst = true, quiz = true))!!
        assertTrue(mapped.isQuiz)
        assertEquals(listOf(false, true), mapped.options.map { it.correct })
    }

    @Test fun otherContentHasNoPoll() {
        assertNull(pollOf(TdApi.MessageText()))
    }
}
