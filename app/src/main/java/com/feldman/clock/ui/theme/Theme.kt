package com.feldman.clock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun isDarkTheme(): Boolean {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    return systemDark

}