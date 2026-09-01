package com.feldman.clock.core.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    return currentWindowAdaptiveInfoV2().windowSizeClass
}

/**
 * Helper to determine if the screen is Wide (Tablet / Landscape)
 */
val WindowSizeClass.isWide: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/**
 * Helper for Medium screens (Foldables / Large Phones)
 */
val WindowSizeClass.isMedium: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

@Composable
fun isScreenWide(): Boolean{
    return rememberWindowSizeClass().isWide
}

@Composable
fun isScreenMedium(): Boolean{
    return rememberWindowSizeClass().isMedium
}
