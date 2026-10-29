package com.kirox.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentsLabelTest {
    @Test fun labelsFollowRussianPlurals() {
        assertEquals("Оставить комментарий", commentsLabel(0))
        assertEquals("1 комментарий", commentsLabel(1))
        assertEquals("3 комментария", commentsLabel(3))
        assertEquals("11 комментариев", commentsLabel(11))
        assertEquals("25 комментариев", commentsLabel(25))
    }
}
