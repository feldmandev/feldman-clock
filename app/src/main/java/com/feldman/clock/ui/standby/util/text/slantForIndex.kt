package com.feldman.clock.ui.standby.util.text

import androidx.compose.ui.util.lerp


fun slantForIndex(
    index: Int,
    count: Int,
    maxSlant: Float
): Float {
    if (count <= 1) return 0f
    val t = index.toFloat() / (count - 1)
    return lerp(-maxSlant, maxSlant, t)
}
