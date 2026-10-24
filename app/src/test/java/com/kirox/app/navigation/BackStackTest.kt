package com.kirox.app.navigation

import com.kirox.app.ui.navigation.BackStack
import com.kirox.app.ui.navigation.NavGraph
import com.kirox.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackTest {
    @Test fun pushAndPopFollowStackOrder() {
        val stack = BackStack(listOf(Screen.Phone))
        stack.push(Screen.Countries)
        assertTrue(stack.forward)
        assertTrue(stack.pop())
        assertFalse(stack.forward)
        assertEquals(Screen.Phone, stack.current)
        assertFalse(stack.pop())
    }

    @Test fun switchingToMainGraphDropsAuthHistory() {
        val stack = BackStack(listOf(Screen.Phone, Screen.AuthStep))
        stack.resetTo(Screen.Chats)
        assertEquals(NavGraph.Main, stack.current.graph)
        assertFalse(stack.canPop)
    }

    @Test fun replaceTopKeepsHistoryBelow() {
        val stack = BackStack(listOf(Screen.Phone, Screen.Countries))
        stack.replaceTop(Screen.AuthStep)
        assertEquals(listOf(Screen.Phone, Screen.AuthStep), stack.snapshot())
    }

    @Test fun removedScreensAreDroppedOnRestore() {
        val restored = BackStack(listOf("Phone", "DemoHome").mapNotNull { name -> Screen.entries.firstOrNull { it.name == name } })
        assertEquals(listOf(Screen.Phone), restored.snapshot())
    }

    @Test fun emptyRestoreFallsBackToPhone() {
        assertEquals(Screen.Phone, BackStack(emptyList()).current)
    }
}
