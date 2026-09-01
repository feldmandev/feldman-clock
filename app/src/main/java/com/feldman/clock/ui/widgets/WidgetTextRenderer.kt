package com.feldman.clock.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.feldman.clock.R
import com.feldman.clock.ui.clock.automaticSplitClockFontWidth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws widget text into bitmaps using the rounded Feldman font.
 *
 * App widgets are inflated by the launcher, which has no access to fonts inside this APK, so
 * `android:fontFamily` on a RemoteViews TextView silently falls back to the system font. Drawing
 * the text here - in our own process, where the font is available - and shipping the result as a
 * bitmap is the only way to get a custom typeface onto the home screen.
 *
 * Rendering in our own process also means font variation axes work normally, which is how the
 * wide text option is applied; see [WIDTH_WIDE].
 */
object WidgetTextRenderer {

    /**
     * feldman_rounded.ttf is a partial instance of the motion library's feldman_font.ttf with the
     * ROND axis pinned to 100 and wght pinned to 500. The wdth axis (25-151) is deliberately left
     * variable so it can still be set per widget. Regenerate after updating the motion library:
     *
     * ```
     * python -c "from fontTools.ttLib import TTFont; from fontTools.varLib import instancer; \
     *   instancer.instantiateVariableFont(TTFont('feldman_font.ttf'), \
     *   {'ROND':100,'wght':500}, inplace=False).save('feldman_rounded.ttf')"
     * ```
     */
    private const val WIDTH_NORMAL = 100
    private const val WIDTH_WIDE = 135

    /**
     * How far the width axis may be condensed to make long text fit its box before the text size
     * itself is reduced. The font allows 25-151; this stays well inside legible territory.
     */
    private const val WIDTH_MIN = 72

    /** Keeps a single bitmap well inside the Binder limit that RemoteViews are sent over. */
    private const val MAX_TEXT_PX = 400f
    private const val MAX_SPLIT_WIDTH_PX = 520f
    private const val MAX_SPLIT_HEIGHT_PX = 320f

    /**
     * Share of its slot the ink occupies; the rest becomes padding inside the bitmap.
     *
     * Sizing to ink bounds removes the font's internal leading, so without this the rows would sit
     * flush against each other. Baking the gap into the bitmap rather than using a fixed layout
     * margin keeps it proportional as the widget is resized.
     */
    private const val CONTENT_FILL = 0.90f

    private const val REFERENCE_SIZE = 100f

    /** Size of a dial label, as a share of the dial's diameter. */
    private const val DIAL_LABEL_WIDTH = 0.42f
    private const val DIAL_LABEL_HEIGHT = 0.11f

    /** The outcome of fitting a string to a box: the axis and size that get it there. */
    private class Fit(val textSize: Float, val inkWidth: Int, val inkHeight: Int)

    private class SplitGlyph(val paint: TextPaint, val inkBounds: RectF)

    private fun typeface(context: Context): Typeface? =
        ResourcesCompat.getFont(context, R.font.feldman_rounded)

    private fun paintFor(context: Context, colorRes: Int?): TextPaint? {
        val font = typeface(context) ?: return null
        return TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            if (colorRes != null) color = ContextCompat.getColor(context, colorRes)
        }
    }

    private fun fitSplitGlyph(
        context: Context,
        text: String,
        colorRes: Int,
        weight: Int,
        width: Float,
        maxWidthPx: Float,
        maxHeightPx: Float
    ): SplitGlyph? {
        val font = ResourcesCompat.getFont(context, com.feldman.motion.R.font.feldman_font)
            ?: return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = font
            color = ContextCompat.getColor(context, colorRes)
            fontVariationSettings = "'wght' $weight, 'wdth' $width, 'ROND' 100, 'slnt' 0"
            fontFeatureSettings = "'pnum' 1, 'tnum' 0"
        }

        fun measure(textSizePx: Float): RectF {
            paint.textSize = textSizePx
            val path = Path()
            paint.getTextPath(text, 0, text.length, 0f, 0f, path)
            return RectF().also { path.computeBounds(it, true) }
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
        return SplitGlyph(paint, measure(low))
    }

    /**
     * Solves for the size that fills the box.
     *
     * The height of the box drives the text size, so the clock grows with the widget. When the
     * text would then be too wide, the font's variable width axis is condensed to bring it back
     * inside the box rather than shrinking the text - so a long time string stays large and still
     * keeps a margin from the widget's edges. Only once the axis bottoms out at [WIDTH_MIN] does
     * the size come down.
     */
    private fun fit(
        paint: TextPaint,
        text: String,
        boxWidthPx: Int,
        boxHeightPx: Int,
        wide: Boolean
    ): Fit? {
        paint.textSize = REFERENCE_SIZE

        val baseWidthAxis = (if (wide) WIDTH_WIDE else WIDTH_NORMAL).toFloat()
        paint.fontVariationSettings = "'wdth' ${baseWidthAxis.roundToInt()}"

        // Measure the ink the glyphs actually cover, not the font's ascent-to-descent line box.
        // Digits use neither the accent space above nor the descender space below, so sizing by
        // the line box would leave dead margin inside every bitmap.
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null

        // The ink only gets part of the slot; the remainder becomes the gap to the next row.
        val contentHeightPx = (boxHeightPx * CONTENT_FILL).coerceAtLeast(1f)

        // If width is the binding constraint, condense so the height can be used instead. Ink
        // widths track the axis closely enough that one correction pass lands within a percent.
        val widthScale = boxWidthPx / bounds.width().toFloat()
        val heightScale = contentHeightPx / bounds.height().toFloat()
        if (widthScale < heightScale) {
            val condensed = (baseWidthAxis * widthScale / heightScale)
                .coerceIn(WIDTH_MIN.toFloat(), baseWidthAxis)
            paint.fontVariationSettings = "'wdth' ${condensed.roundToInt()}"
            paint.getTextBounds(text, 0, text.length, bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return null
        }

        val scale = min(
            boxWidthPx / bounds.width().toFloat(),
            contentHeightPx / bounds.height().toFloat()
        )
        paint.textSize = min(REFERENCE_SIZE * scale, MAX_TEXT_PX)

        paint.getTextBounds(text, 0, text.length, bounds)
        return Fit(paint.textSize, bounds.width(), bounds.height())
    }

    /**
     * The height the glyphs would end up at in this box, without drawing anything.
     *
     * Used to choose between the one-line and stacked arrangements: whichever renders the time
     * taller is the better use of the widget.
     */
    fun measuredInkHeight(
        context: Context,
        text: String,
        boxWidthPx: Int,
        boxHeightPx: Int,
        wide: Boolean
    ): Int {
        if (text.isEmpty() || boxWidthPx <= 0 || boxHeightPx <= 0) return 0
        val paint = paintFor(context, colorRes = null) ?: return 0
        return fit(paint, text, boxWidthPx, boxHeightPx, wide)?.inkHeight ?: 0
    }

    /**
     * A label placed somewhere on the analog dial: the drawn bitmap plus how far its centre sits
     * from the middle of the widget.
     */
    class DialLabel(val bitmap: Bitmap, val offsetXPx: Int, val offsetYPx: Int)

    /**
     * Renders [text] rotated to sit on the dial at [angleDegrees] clockwise from twelve o'clock,
     * [orbitFraction] of the way out to the edge.
     *
     * Only the label itself is drawn, not a dial-sized canvas: a bitmap the size of a large widget
     * would be megabytes and RemoteViews have to cross a Binder transaction. The caller positions
     * the small bitmap with padding instead - see [DialLabel].
     */
    fun renderOnDial(
        context: Context,
        text: String,
        dialSizePx: Int,
        orbitFraction: Float,
        angleDegrees: Float,
        colorRes: Int,
        wide: Boolean
    ): DialLabel? {
        if (text.isEmpty() || dialSizePx <= 0) return null
        val paint = paintFor(context, colorRes) ?: return null

        // Sized against the dial so the label scales with the widget.
        val boxWidth = (dialSizePx * DIAL_LABEL_WIDTH).roundToInt().coerceAtLeast(1)
        val boxHeight = (dialSizePx * DIAL_LABEL_HEIGHT).roundToInt().coerceAtLeast(1)
        fit(paint, text, boxWidth, boxHeight, wide) ?: return null

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val inkWidth = bounds.width().toFloat()
        val inkHeight = bounds.height().toFloat()
        if (inkWidth <= 0f || inkHeight <= 0f) return null

        val radians = Math.toRadians(angleDegrees.toDouble())
        val sin = abs(sin(radians)).toFloat()
        val cos = abs(cos(radians)).toFloat()

        // Bounding box of the rotated ink, plus a pixel of slack for the rounded terminals.
        val width = (inkWidth * cos + inkHeight * sin).roundToInt() + 2
        val height = (inkWidth * sin + inkHeight * cos).roundToInt() + 2
        if (width <= 0 || height <= 0) return null

        // Tangential text on the lower half of the dial would come out upside down, so flip it
        // there. The label keeps its position; only the glyphs turn over.
        val normalised = ((angleDegrees % 360f) + 360f) % 360f
        val textRotation = if (normalised > 90f && normalised < 270f) {
            angleDegrees + 180f
        } else {
            angleDegrees
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.rotate(textRotation, width / 2f, height / 2f)
        // Centre the ink on the bitmap's middle before the rotation is applied.
        canvas.drawText(
            text,
            width / 2f - (bounds.left + bounds.right) / 2f,
            height / 2f - (bounds.top + bounds.bottom) / 2f,
            paint
        )

        val orbitPx = dialSizePx / 2f * orbitFraction
        return DialLabel(
            bitmap = bitmap,
            offsetXPx = (orbitPx * sin(radians)).roundToInt(),
            offsetYPx = (-orbitPx * cos(radians)).roundToInt()
        )
    }

    /** Renders [text] to fill [boxWidthPx] by [boxHeightPx] as fully as it can. */
    fun render(
        context: Context,
        text: String,
        boxWidthPx: Int,
        boxHeightPx: Int,
        colorRes: Int,
        wide: Boolean
    ): Bitmap? {
        if (text.isEmpty() || boxWidthPx <= 0 || boxHeightPx <= 0) return null
        val paint = paintFor(context, colorRes) ?: return null
        fit(paint, text, boxWidthPx, boxHeightPx, wide) ?: return null

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        // Keep a pixel of slack: the rounded terminals of this font overshoot their reported
        // bounds slightly, and a perfectly tight bitmap clips them.
        val inkWidth = bounds.width() + 2
        val inkHeight = bounds.height() + 2
        if (inkWidth <= 0 || inkHeight <= 0) return null

        // Give the bitmap the whole slot and centre the ink in it, so the leftover shows up as
        // even spacing above and below rather than as a shrunken glyph.
        val height = maxOf(boxHeightPx, inkHeight)
        val top = (height - inkHeight) / 2f

        val bitmap = Bitmap.createBitmap(inkWidth, height, Bitmap.Config.ARGB_8888)
        // Offset by the bounds' own origin so the ink lands inside the bitmap.
        Canvas(bitmap).drawText(text, -bounds.left + 1f, top - bounds.top + 1f, paint)
        return bitmap
    }

    /** Renders the complete split clock face into one bitmap for a launcher RemoteViews widget. */
    fun renderSplitClock(
        context: Context,
        hours: String,
        minutes: String,
        period: String?,
        boxWidthPx: Int,
        boxHeightPx: Int,
        hourColorRes: Int,
        minuteColorRes: Int,
        automaticWidth: Boolean,
        wide: Boolean
    ): Bitmap? {
        if (hours.isEmpty() || minutes.isEmpty() || boxWidthPx <= 0 || boxHeightPx <= 0) return null

        val scale = min(
            1f,
            min(MAX_SPLIT_WIDTH_PX / boxWidthPx, MAX_SPLIT_HEIGHT_PX / boxHeightPx)
        )
        val width = (boxWidthPx * scale).roundToInt().coerceAtLeast(1)
        val height = (boxHeightPx * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val densityScale = context.resources.displayMetrics.density * scale
        // The RemoteViews root and row geometry already keep the face clear of the widget shape.
        // Fill this bitmap completely so resizing the widget changes the clock, not another inset.
        val edgePaddingPx = densityScale
        val clockWidthPx = (width - edgePaddingPx * 2f).coerceAtLeast(1f)
        val clockHeightPx = height.toFloat()
        val clockLeft = (width - clockWidthPx) / 2f
        val clockTop = (height - clockHeightPx) / 2f
        val is24Hour = period == null
        val hourWeight = if (is24Hour) 800 else 600
        val minuteWeight = 800
        val timeSpacingPx = 4f * densityScale
        val hourWidthFraction = if (is24Hour) 0.50f else 0.60f
        val widthAxis = if (automaticWidth) {
            val font = ResourcesCompat.getFont(context, com.feldman.motion.R.font.feldman_font)
                ?: return null
            automaticSplitClockFontWidth(
                baseTypeface = font,
                hourWeight = hourWeight,
                minuteWeight = minuteWeight,
                roundness = 100f,
                availableWidthPx = clockWidthPx,
                availableHeightPx = clockHeightPx,
                spacingPx = timeSpacingPx,
                is24Hour = is24Hour
            )
        } else {
            (if (wide) WIDTH_WIDE else WIDTH_NORMAL).toFloat()
        }
        val availableWidthPx = (clockWidthPx - timeSpacingPx).coerceAtLeast(1f)
        val hourBoxWidthPx = availableWidthPx * hourWidthFraction
        val minuteStartPx = hourBoxWidthPx + timeSpacingPx
        val rightBoxWidthPx = (clockWidthPx - minuteStartPx).coerceAtLeast(1f)
        val hourCapacity = fitSplitGlyph(
            context,
            hours,
            hourColorRes,
            hourWeight,
            widthAxis,
            hourBoxWidthPx,
            clockHeightPx
        ) ?: return null
        val minuteCapacity = fitSplitGlyph(
            context,
            minutes,
            minuteColorRes,
            minuteWeight,
            widthAxis,
            rightBoxWidthPx,
            clockHeightPx
        ) ?: return null
        val minuteHeightFraction = 0.60f
        val targetHourHeightPx = if (is24Hour) {
            hourCapacity.inkBounds.height()
        } else {
            min(
                hourCapacity.inkBounds.height(),
                minuteCapacity.inkBounds.height() / minuteHeightFraction
            )
        }
        val hourGlyph = if (is24Hour) {
            hourCapacity
        } else {
            fitSplitGlyph(
                context,
                hours,
                hourColorRes,
                hourWeight,
                widthAxis,
                hourBoxWidthPx,
                targetHourHeightPx
            ) ?: return null
        }
        val minuteSlotHeightPx = if (is24Hour) {
            clockHeightPx
        } else {
            hourGlyph.inkBounds.height() * minuteHeightFraction
        }
        val minuteGlyph = if (is24Hour) {
            minuteCapacity
        } else {
            fitSplitGlyph(
                context,
                minutes,
                minuteColorRes,
                minuteWeight,
                widthAxis,
                rightBoxWidthPx,
                minuteSlotHeightPx
            ) ?: return null
        }
        val periodPillWidthPx = if (is24Hour) 0f else minuteGlyph.inkBounds.width()
        val timeLeftPx = hourBoxWidthPx - hourGlyph.inkBounds.width()
        val timeRightPx = minuteStartPx + maxOf(
            minuteGlyph.inkBounds.width(),
            periodPillWidthPx
        )
        val timeOffsetX = clockWidthPx / 2f - (timeLeftPx + timeRightPx) / 2f
        val centerY = clockTop + clockHeightPx / 2f
        val hourBounds = hourGlyph.inkBounds
        canvas.drawText(
            hours,
            clockLeft + timeOffsetX + hourBoxWidthPx - hourBounds.right,
            centerY - (hourBounds.top + hourBounds.bottom) / 2f,
            hourGlyph.paint
        )

        val minuteBounds = minuteGlyph.inkBounds
        if (is24Hour) {
            canvas.drawText(
                minutes,
                clockLeft + timeOffsetX + minuteStartPx - minuteBounds.left,
                centerY - (minuteBounds.top + minuteBounds.bottom) / 2f,
                minuteGlyph.paint
            )
            return bitmap
        }

        val stackTop = centerY - hourBounds.height() / 2f
        canvas.drawText(
            minutes,
            clockLeft + timeOffsetX + minuteStartPx - minuteBounds.left,
            stackTop + minuteSlotHeightPx - minuteBounds.height() - minuteBounds.top,
            minuteGlyph.paint
        )

        val stackGapPx = hourBounds.height() * 0.06f
        val periodPillHeightPx = hourBounds.height() * 0.34f
        val pillLeft = clockLeft + timeOffsetX + minuteStartPx
        val pillTop = stackTop + minuteSlotHeightPx + stackGapPx
        val cardRect = RectF(
            pillLeft,
            pillTop,
            pillLeft + periodPillWidthPx,
            pillTop + periodPillHeightPx
        )
        val minuteColor = ContextCompat.getColor(context, minuteColorRes)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                64,
                Color.red(minuteColor),
                Color.green(minuteColor),
                Color.blue(minuteColor)
            )
        }
        val pillRadius = min(16f * densityScale, cardRect.height() / 2f)
        canvas.drawRoundRect(cardRect, pillRadius, pillRadius, cardPaint)

        val horizontalPaddingPx = 2f * densityScale
        val verticalPaddingPx = periodPillHeightPx * 0.16f
        val periodGlyph = fitSplitGlyph(
            context,
            period,
            minuteColorRes,
            850,
            widthAxis,
            (periodPillWidthPx - horizontalPaddingPx * 2f).coerceAtLeast(1f),
            (periodPillHeightPx - verticalPaddingPx * 2f).coerceAtLeast(1f)
        ) ?: return null
        val periodBounds = periodGlyph.inkBounds
        canvas.drawText(
            period,
            cardRect.centerX() - (periodBounds.left + periodBounds.right) / 2f,
            cardRect.centerY() - (periodBounds.top + periodBounds.bottom) / 2f,
            periodGlyph.paint
        )
        return bitmap
    }

}
