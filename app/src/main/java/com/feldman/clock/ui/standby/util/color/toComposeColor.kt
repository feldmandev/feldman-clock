package com.feldman.clock.ui.standby.util.color

import androidx.compose.ui.graphics.Color


fun Int.toComposeColor(): Color {
    val a = (this shr 24 and 0xFF) / 255f
    val r = (this shr 16 and 0xFF) / 255f
    val g = (this shr 8 and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    return Color(r, g, b, a)
}
