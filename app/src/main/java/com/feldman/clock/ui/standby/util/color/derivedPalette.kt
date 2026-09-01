package com.feldman.clock.ui.standby.util.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.feldman.clock.ui.standby.util.int.clamp01


/** Build N pleasant variations from a base color by shifting hue/sat/light slightly. */
fun derivedPalette(base: Color, count: Int): List<Color> {
    val baseHsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), baseHsl)

    // if base is near gray, bump saturation so variations are visible
    if (baseHsl[1] < 0.25f) baseHsl[1] = 0.25f

    // Nice, subtle hue offsets (degrees). Will cycle if count > offsets.size.
    val hueOffsets = floatArrayOf(-22f, -10f, 0f, 14f, 26f, 38f, -34f, 18f)

    return List(maxOf(1, count)) { i ->
        val h = (baseHsl[0] + hueOffsets[i % hueOffsets.size] + 360f) % 360f
        val s = clamp01(baseHsl[1] * (0.95f + 0.12f * ((i % 3) - 1)))
        val l = clamp01(baseHsl[2] * (0.96f - 0.08f * (i % 2)))
        val out = floatArrayOf(h, s, l)
        Color(ColorUtils.HSLToColor(out))
    }
}
