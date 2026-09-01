package com.feldman.clock.ui.standby.util.color

import androidx.compose.ui.graphics.Color

fun blend(a: Color, b: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    val inv = 1f - r
    return Color(
        red = a.red * inv + b.red * r,
        green = a.green * inv + b.green * r,
        blue = a.blue * inv + b.blue * r,
        alpha = a.alpha * inv + b.alpha * r
    )
}
