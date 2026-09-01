package com.feldman.clock.ui.clock

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** Selects the split clock's width axis from its actual drawable area and font outlines. */
internal fun automaticSplitClockFontWidth(
    baseTypeface: Typeface,
    hourWeight: Int,
    minuteWeight: Int,
    roundness: Float,
    availableWidthPx: Float,
    availableHeightPx: Float,
    spacingPx: Float,
    is24Hour: Boolean
): Float {
    fun glyphAspect(weight: Int, width: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = baseTypeface
            textSize = 1_000f
            fontVariationSettings = "'wght' $weight, 'wdth' $width, 'ROND' $roundness, 'slnt' 0"
            fontFeatureSettings = "'pnum' 1, 'tnum' 0"
        }
        val path = Path()
        paint.getTextPath("88", 0, 2, 0f, 0f, path)
        val bounds = RectF().also { path.computeBounds(it, true) }
        return bounds.width() / bounds.height().coerceAtLeast(1f)
    }

    val availableTimeWidth = (availableWidthPx - spacingPx).coerceAtLeast(1f)
    val hourBoxWidth = availableTimeWidth * if (is24Hour) 0.50f else 0.60f
    val minuteBoxWidth = (availableTimeWidth - hourBoxWidth).coerceAtLeast(1f)

    fun fitsAtFullHeight(width: Float): Boolean {
        val hourWidth = availableHeightPx * glyphAspect(hourWeight, width)
        val minuteHeight = availableHeightPx * if (is24Hour) 1f else 0.60f
        val minuteWidth = minuteHeight * glyphAspect(minuteWeight, width)
        return hourWidth <= hourBoxWidth && minuteWidth <= minuteBoxWidth
    }

    var minimum = 25f
    var maximum = 151f
    if (!fitsAtFullHeight(minimum)) return minimum
    if (fitsAtFullHeight(maximum)) return maximum

    repeat(14) {
        val middle = (minimum + maximum) / 2f
        if (fitsAtFullHeight(middle)) minimum = middle else maximum = middle
    }
    return minimum
}
