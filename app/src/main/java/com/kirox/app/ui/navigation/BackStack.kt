package com.kirox.app.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

enum class NavGraph { Auth, Main }

enum class Screen(val graph: NavGraph) {
    Phone(NavGraph.Auth),
    Countries(NavGraph.Auth),
    AuthStep(NavGraph.Auth),
    DemoCode(NavGraph.Auth),
    DemoHome(NavGraph.Auth),
    Chats(NavGraph.Main)
}

/** Minimal saveable back stack. Switching graphs replaces the whole stack, so Back never crosses auth ↔ main. */
@Stable
class BackStack(initial: List<Screen>) {
    private val entries = mutableStateListOf<Screen>().apply { addAll(initial.ifEmpty { listOf(Screen.Phone) }) }

    val current: Screen get() = entries.last()
    val canPop: Boolean get() = entries.size > 1
    /** True when the last change moved forward; drives the transition direction. */
    var forward by mutableStateOf(true)
        private set

    operator fun contains(screen: Screen) = screen in entries

    fun push(screen: Screen) {
        if (current == screen) return
        forward = true
        entries += screen
    }

    fun pop(): Boolean {
        if (!canPop) return false
        forward = false
        entries.removeAt(entries.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        forward = true
        entries[entries.lastIndex] = screen
    }

    fun resetTo(vararg screens: Screen) {
        val target = screens.toList()
        if (target == entries.toList()) return
        forward = target.size >= entries.size || target.last().graph != current.graph
        entries.clear()
        entries.addAll(target)
    }

    fun snapshot(): List<Screen> = entries.toList()

    companion object {
        val Saver = listSaver<BackStack, String>(
            save = { stack -> stack.snapshot().map { it.name } },
            restore = { names -> BackStack(names.mapNotNull { name -> Screen.entries.firstOrNull { it.name == name } }) }
        )
    }
}
