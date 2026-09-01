package com.feldman.clock.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.feldman.clock.ui.theme.expressiveChromeColor
import com.feldman.clock.core.util.isScreenWide
import com.feldman.motion.MotionNavigationPage
import com.feldman.motion.MotionNavigationState

@Composable
fun AppNavHost(
    backStack: DestBackStack,
    onNavigate: (Dest) -> Unit,
    navigationState: MotionNavigationState<Dest>,
    modifier: Modifier = Modifier,
) {
    val isWide = isScreenWide()
    val chromeColor = expressiveChromeColor(
        if (isWide) NavigationBarDefaults.containerColor else MaterialTheme.colorScheme.background
    )

    NavDisplay(
        backStack = backStack.backStack,
        modifier = modifier,
        onBack = { backStack.pop() },
        transitionSpec = {
            if (backStack.usesFadeTransition) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
            } else {
                navigationState.transitionSpec().invoke(this)
            }
        },
        popTransitionSpec = navigationState.popTransitionSpec(),
        predictivePopTransitionSpec = navigationState.predictivePopTransitionSpec(),
        entryProvider = entryProvider {
            for (clazz in destinationClasses) {
                addEntryProvider(
                    clazz = clazz,
                    clazzContentKey = { it }
                ) { dest ->
                    val content: @Composable () -> Unit = {
                        dest.Content(
                            onNavigate = onNavigate,
                            onBack = { backStack.pop() }
                        )
                    }
                    if (backStack.usesFadeTransition) {
                        content()
                    } else {
                        MotionNavigationPage(
                            state = navigationState,
                            destination = dest,
                            backgroundColor = if (
                                listOf(Dest.Alarm, Dest.Clock, Dest.Timer, Dest.Stopwatch)
                                    .any { it::class == dest::class }
                            ) {
                                chromeColor
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                            content = content
                        )
                    }
                }
            }
        }
    )
}
