package com.feldman.clock.app.navigation

import com.feldman.clock.R

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DestBackStack(start: Dest) {

    private val initialStack = when (start) {
        Dest.SetupNotifications -> listOf(Dest.SetupWelcome, Dest.SetupNotifications)
        Dest.SetupExactAlarms -> listOf(
            Dest.SetupWelcome,
            Dest.SetupNotifications,
            Dest.SetupExactAlarms
        )
        Dest.SetupOverlay -> listOf(
            Dest.SetupWelcome,
            Dest.SetupNotifications,
            Dest.SetupExactAlarms,
            Dest.SetupOverlay
        )
        Dest.SetupFullScreenAlarms -> listOf(
            Dest.SetupWelcome,
            Dest.SetupNotifications,
            Dest.SetupExactAlarms,
            Dest.SetupOverlay,
            Dest.SetupFullScreenAlarms
        )
        Dest.SetupDone -> listOf(
            Dest.SetupWelcome,
            Dest.SetupNotifications,
            Dest.SetupExactAlarms,
            Dest.SetupOverlay,
            Dest.SetupFullScreenAlarms,
            Dest.SetupDone
        )
        else -> listOf(start)
    }

    // Helper map to keep stacks for each root (if using bottom nav with separate stacks)
    // For now, simpler implementation if just one stack? 
    // The user snippet uses a linkedMapOf for multi-stack support.
    private var stacks = linkedMapOf(
        initialStack.first() to mutableStateListOf(*initialStack.toTypedArray())
    )

    var currentTop by mutableStateOf(initialStack.first())
        private set

    var usesFadeTransition by mutableStateOf(false)
        private set

    // The single list that NavDisplay observes
    val backStack = mutableStateListOf(*initialStack.toTypedArray())

    private fun sync() {
        backStack.apply {
            clear()
            // In a flat backstack, we might just show the current stack.
            // But if we want to support the user's snippet logic:
            addAll(stacks.flatMap { it.value })
            // Wait, flatMap over all stacks would show ALL stacks? That implies a specific UI logic.
            // The user's snippet logic: `addAll(stacks.flatMap { it.value })`
            // This suggests the backStack contains EVERYTHING? 
            // Or maybe existing logic is for a specific behavior.
            // Let's stick to the user's snippet precisely.
        }
    }
    
    // Simpler sync for standard nav: just show current top stack?
    // But user snippet says: `addAll(stacks.flatMap { it.value })`
    // I will trust the snippet.

    fun navigateTop(dest: Dest) {
        usesFadeTransition = false
        if (stacks[dest] == null) {
            stacks[dest] = mutableStateListOf(dest)
        } else {
            // Re-order to end? Or just switch focus?
            // User snippet:
            // val existing = stacks.remove(dest)!!
            // stacks[dest] = existing
            
            // This effectively moves the stack 'dest' to the end of the map (linked map), making it "top"
            if (stacks.containsKey(dest)) {
                val existing = stacks.remove(dest)!!
                stacks[dest] = existing
            }
        }
        currentTop = dest
        sync()
    }

    fun navigate(dest: Dest) {
        usesFadeTransition = false
        stacks[currentTop]?.add(dest)
        backStack.add(dest)
    }

    fun reset(dest: Dest, useFadeTransition: Boolean = false) {
        usesFadeTransition = useFadeTransition
        stacks = linkedMapOf(dest to mutableStateListOf(dest))
        currentTop = dest
        backStack.clear()
        backStack.add(dest)
    }

    fun pop() {
        usesFadeTransition = false
        val stack = stacks[currentTop] ?: return
        when {
            stack.size > 1 -> {
                stack.removeLast()
                backStack.removeLast()
            }
            stacks.size > 1 -> {
                stacks.remove(currentTop)
                currentTop = stacks.keys.last()
                backStack.removeLast()
            }
        }
    }
}
