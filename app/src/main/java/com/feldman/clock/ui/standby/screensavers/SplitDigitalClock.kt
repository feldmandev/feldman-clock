package com.feldman.clock.ui.standby.screensavers

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF as AndroidRectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feldman.motion.feldmanFont
import com.feldman.clock.R
import com.feldman.clock.ui.clock.automaticSplitClockFontWidth
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SplitDigitalClock(
    time: LocalTime,
    hourColor: Color,
    minuteColor: Color,
    dateText: String,
    dateColor: Color,
    showDate: Boolean,
    nextAlarmText: String?,
    nextAlarmColor: Color,
    dateScale: Float,
    dateSpacing: Float,
    clockWeight: Float,
    clockWidth: Float,
    automaticWidth: Boolean,
    clockRoundness: Float,
    timeSpacing: Float,
    is24Hour: Boolean,
    userScale: Float,
    modifier: Modifier = Modifier
) {
    val hourPattern = if (is24Hour) "HH" else "hh"
    val hours = time.format(DateTimeFormatter.ofPattern(hourPattern))
    val minutes = time.format(DateTimeFormatter.ofPattern("mm"))
    val period = time.format(DateTimeFormatter.ofPattern("a"))
    val weightScale = clockWeight.coerceIn(0.7f, 1.3f)
    val roundScale = clockRoundness.coerceIn(0f, 2f)
    fun scaledWeight(base: Int) = (base * weightScale).roundToInt().coerceIn(100, 900)
    val scaledRoundness = (100f * roundScale).coerceIn(0f, 200f)
    val hourWeight = scaledWeight(if (is24Hour) 800 else 600)
    val minuteWeight = scaledWeight(800)
    val density = LocalDensity.current
    val context = LocalContext.current
    val baseTypeface = remember(context) {
        context.resources.getFont(com.feldman.motion.R.font.feldman_font)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val hasSupportingInfo = showDate || !nextAlarmText.isNullOrEmpty()
        val layoutScale = userScale.coerceIn(0.5f, 1.5f)
        val safeMaxWidth = (maxWidth - 12.dp).coerceAtLeast(1.dp)
        val clockHeight = maxHeight * (
            (if (hasSupportingInfo) 0.86f else 0.97f) * layoutScale
        ).coerceAtMost(0.97f)
        val renderedClockWidth = safeMaxWidth * layoutScale.coerceAtMost(1f)
        val dateHeight = maxHeight * 0.10f
        val rowWidthPx = with(density) { renderedClockWidth.toPx() }
        val rowHeightPx = with(density) { clockHeight.toPx() }
        val dateHeightPx = with(density) { dateHeight.toPx() }
        val alarmHeight = dateHeight * 0.52f
        val timeSpacingPx = with(density) {
            (timeSpacing.coerceIn(0f, 24f) + 4f).dp.toPx()
        }
        val selectedFontWidth = remember(
            automaticWidth,
            clockWidth,
            baseTypeface,
            hourWeight,
            minuteWeight,
            scaledRoundness,
            rowWidthPx,
            rowHeightPx,
            timeSpacingPx,
            is24Hour
        ) {
            if (automaticWidth) {
                automaticSplitClockFontWidth(
                    baseTypeface = baseTypeface,
                    hourWeight = hourWeight,
                    minuteWeight = minuteWeight,
                    roundness = scaledRoundness,
                    availableWidthPx = rowWidthPx,
                    availableHeightPx = rowHeightPx,
                    spacingPx = timeSpacingPx,
                    is24Hour = is24Hour
                )
            } else {
                (clockWidth * 100f).coerceIn(25f, 151f)
            }
        }
        val availableWidthPx = (rowWidthPx - timeSpacingPx).coerceAtLeast(0f)
        val hourBoxWidthPx = availableWidthPx * if (is24Hour) 0.50f else 0.60f
        val minuteStartPx = hourBoxWidthPx + timeSpacingPx
        val rightBoxWidthPx = (rowWidthPx - minuteStartPx).coerceAtLeast(1f)
        val hourCapacity = remember(
            hours,
            baseTypeface,
            hourWeight,
            selectedFontWidth,
            scaledRoundness,
            hourBoxWidthPx,
            rowHeightPx
        ) {
            fitClockGlyph(
                text = hours,
                baseTypeface = baseTypeface,
                weight = hourWeight,
                width = selectedFontWidth,
                roundness = scaledRoundness,
                maxWidthPx = hourBoxWidthPx,
                maxHeightPx = rowHeightPx
            )
        }
        val minuteCapacity = remember(
            minutes,
            baseTypeface,
            minuteWeight,
            selectedFontWidth,
            scaledRoundness,
            rightBoxWidthPx,
            rowHeightPx
        ) {
            fitClockGlyph(
                text = minutes,
                baseTypeface = baseTypeface,
                weight = minuteWeight,
                width = selectedFontWidth,
                roundness = scaledRoundness,
                maxWidthPx = rightBoxWidthPx,
                maxHeightPx = rowHeightPx
            )
        }
        val minuteHeightFraction = 0.60f
        val targetHourHeightPx = if (is24Hour) {
            hourCapacity.inkBounds.height()
        } else {
            minOf(
                hourCapacity.inkBounds.height(),
                minuteCapacity.inkBounds.height() / minuteHeightFraction
            )
        }
        val hourGlyph = if (is24Hour) hourCapacity else remember(
            hours,
            baseTypeface,
            hourWeight,
            selectedFontWidth,
            scaledRoundness,
            hourBoxWidthPx,
            targetHourHeightPx
        ) {
            fitClockGlyph(
                text = hours,
                baseTypeface = baseTypeface,
                weight = hourWeight,
                width = selectedFontWidth,
                roundness = scaledRoundness,
                maxWidthPx = hourBoxWidthPx,
                maxHeightPx = targetHourHeightPx
            )
        }
        val minuteSlotHeightPx = if (is24Hour) rowHeightPx else (
            hourGlyph.inkBounds.height() * minuteHeightFraction
        )
        val minuteGlyph = if (is24Hour) minuteCapacity else remember(
            minutes,
            baseTypeface,
            minuteWeight,
            selectedFontWidth,
            scaledRoundness,
            rightBoxWidthPx,
            minuteSlotHeightPx
        ) {
            fitClockGlyph(
                text = minutes,
                baseTypeface = baseTypeface,
                weight = minuteWeight,
                width = selectedFontWidth,
                roundness = scaledRoundness,
                maxWidthPx = rightBoxWidthPx,
                maxHeightPx = minuteSlotHeightPx
            )
        }
        val periodHorizontalPaddingPx = with(density) { 2.dp.toPx() }
        val stackGapPx = if (is24Hour) 0f else hourGlyph.inkBounds.height() * 0.06f
        val periodPillHeightPx = if (is24Hour) 0f else (
            hourGlyph.inkBounds.height() * 0.34f
        ).coerceAtLeast(1f)
        val periodVerticalPaddingPx = periodPillHeightPx * 0.16f
        val periodGlyph = if (is24Hour) null else remember(
            period,
            baseTypeface,
            selectedFontWidth,
            scaledRoundness,
            minuteGlyph.inkBounds.width(),
            periodPillHeightPx,
            periodHorizontalPaddingPx,
            periodVerticalPaddingPx
        ) {
            fitClockGlyph(
                text = period,
                baseTypeface = baseTypeface,
                weight = scaledWeight(850),
                width = selectedFontWidth,
                roundness = scaledRoundness,
                maxWidthPx = (
                    minuteGlyph.inkBounds.width() - periodHorizontalPaddingPx * 2f
                ).coerceAtLeast(1f),
                maxHeightPx = (periodPillHeightPx - periodVerticalPaddingPx * 2f)
                    .coerceAtLeast(1f)
            )
        }
        val periodPillWidthPx = if (periodGlyph == null) 0f else minuteGlyph.inkBounds.width()
        val timeLeftPx = hourBoxWidthPx - hourGlyph.inkBounds.width()
        val timeRightPx = minuteStartPx + maxOf(minuteGlyph.inkBounds.width(), periodPillWidthPx)
        val timeOffsetX = rowWidthPx / 2f - (timeLeftPx + timeRightPx) / 2f
        val requestedDateSpacingPx = with(density) {
            dateSpacing.coerceIn(0f, 24f).dp.toPx()
        }
        val visibleTimeBottomFraction = if (is24Hour) 0.23f else 0.32f
        val dateDockOffsetY = (
            rowHeightPx * visibleTimeBottomFraction +
                requestedDateSpacingPx + dateHeightPx * 0.30f
        ).roundToInt()
        val alarmDockOffsetY = dateDockOffsetY + if (showDate) {
            (dateHeightPx * 0.55f).roundToInt()
        } else {
            0
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.width(renderedClockWidth).height(clockHeight)) {
                val centerY = size.height / 2f
                val hourBounds = hourGlyph.inkBounds
                drawIntoCanvas { canvas ->
                    hourGlyph.paint.color = hourColor.toArgb()
                    canvas.nativeCanvas.drawText(
                        hours,
                        timeOffsetX + hourBoxWidthPx - hourBounds.right,
                        centerY - (hourBounds.top + hourBounds.bottom) / 2f,
                        hourGlyph.paint
                    )
                }

                val minuteBounds = minuteGlyph.inkBounds
                if (is24Hour) {
                    drawIntoCanvas { canvas ->
                        minuteGlyph.paint.color = minuteColor.toArgb()
                        canvas.nativeCanvas.drawText(
                            minutes,
                            timeOffsetX + minuteStartPx - minuteBounds.left,
                            centerY - (minuteBounds.top + minuteBounds.bottom) / 2f,
                            minuteGlyph.paint
                        )
                    }
                } else {
                    val stackTop = centerY - hourBounds.height() / 2f
                    drawIntoCanvas { canvas ->
                        minuteGlyph.paint.color = minuteColor.toArgb()
                        canvas.nativeCanvas.drawText(
                            minutes,
                            timeOffsetX + minuteStartPx - minuteBounds.left,
                            stackTop + minuteSlotHeightPx - minuteBounds.height() - minuteBounds.top,
                            minuteGlyph.paint
                        )
                    }

                    val pillTop = stackTop + minuteSlotHeightPx + stackGapPx
                    val pillRadius = minOf(with(density) { 16.dp.toPx() }, periodPillHeightPx / 2f)
                    drawRoundRect(
                        color = minuteColor.copy(alpha = 0.22f),
                        topLeft = Offset(timeOffsetX + minuteStartPx, pillTop),
                        size = Size(periodPillWidthPx, periodPillHeightPx),
                        cornerRadius = CornerRadius(pillRadius, pillRadius)
                    )
                    periodGlyph?.let { fittedPeriod ->
                        val periodBounds = fittedPeriod.inkBounds
                        drawIntoCanvas { canvas ->
                            fittedPeriod.paint.color = minuteColor.toArgb()
                            canvas.nativeCanvas.drawText(
                                period,
                                timeOffsetX + minuteStartPx +
                                    (periodPillWidthPx - periodBounds.width()) / 2f -
                                    periodBounds.left,
                                pillTop + (periodPillHeightPx - periodBounds.height()) / 2f -
                                    periodBounds.top,
                                fittedPeriod.paint
                            )
                        }
                    }
                }
            }

            if (showDate) {
                Box(
                    modifier = Modifier
                        .width(renderedClockWidth)
                        .height(dateHeight)
                        .offset { IntOffset(0, dateDockOffsetY) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dateText,
                        modifier = Modifier.fillMaxWidth(),
                        color = dateColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontFamily = feldmanFont(
                                weight = scaledWeight(600),
                                width = selectedFontWidth,
                                slant = 0f,
                                round = scaledRoundness
                            ),
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = (72 * userScale * dateScale.coerceIn(0.6f, 1.4f)).sp,
                            stepSize = 1.sp
                        )
                    )
                }
            }

            if (!nextAlarmText.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .width(renderedClockWidth)
                        .height(alarmHeight)
                        .offset { IntOffset(0, alarmDockOffsetY) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_tab_alarm_static),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(nextAlarmColor),
                        modifier = Modifier.height(alarmHeight * 0.32f)
                    )
                    Text(
                        text = nextAlarmText,
                        modifier = Modifier.padding(start = 4.dp),
                        color = nextAlarmColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = TextStyle(
                            fontFamily = feldmanFont(
                                weight = scaledWeight(600),
                                width = selectedFontWidth,
                                slant = 0f,
                                round = scaledRoundness
                            ),
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            fontSize = (24 * userScale * dateScale.coerceIn(0.6f, 1.4f)).sp
                        )
                    )
                }
            }
        }
    }
}

private data class FittedClockGlyph(
    val paint: AndroidPaint,
    val inkBounds: AndroidRectF
)

private fun fitClockGlyph(
    text: String,
    baseTypeface: Typeface,
    weight: Int,
    width: Float,
    roundness: Float,
    maxWidthPx: Float,
    maxHeightPx: Float
): FittedClockGlyph {
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = baseTypeface
        fontVariationSettings = "'wght' $weight, 'wdth' $width, 'ROND' $roundness, 'slnt' 0"
        fontFeatureSettings = "'pnum' 1, 'tnum' 0"
    }

    fun measure(fontSizePx: Float): AndroidRectF {
        paint.textSize = fontSizePx
        val path = AndroidPath()
        paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        return AndroidRectF().also { bounds -> path.computeBounds(bounds, true) }
    }

    val targetWidth = maxWidthPx.coerceAtLeast(1f)
    val targetHeight = maxHeightPx.coerceAtLeast(1f)
    var low = 1f
    var high = 4096f
    repeat(18) {
        val middle = (low + high) / 2f
        val bounds = measure(middle)
        if (bounds.width() <= targetWidth && bounds.height() <= targetHeight) {
            low = middle
        } else {
            high = middle
        }
    }
    return FittedClockGlyph(paint, measure(low))
}
