package com.feldman.clock.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.feldman.clock.R
import com.feldman.clock.core.util.AlarmUtils
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Builds the [RemoteViews] for the clock widgets from the per-instance settings in [WidgetPrefs].
 *
 * Every label is drawn to a bitmap by [WidgetTextRenderer] so it can use the rounded Feldman font,
 * which the launcher cannot load itself. The TextClock views still present in the layouts are only
 * there so the widget picker has something to show in its generated preview; at runtime they are
 * always hidden in favour of the bitmaps.
 *
 * Text is sized to fill the widget rather than using fixed sp values, so it grows when the widget
 * is resized. Each visible element gets a share of the available height according to [Weights].
 *
 * Only methods annotated `@RemotableViewMethod` can be driven through RemoteViews: the dial and
 * hands are set with `setIcon`, the bitmaps with `setImageViewBitmap`.
 */
object ClockWidgetRenderer {

    private const val DEFAULT_ANALOG_DATE_FORMAT = "EEE d"
    private const val DEFAULT_DIGITAL_DATE_FORMAT = "EEE, MMM d"

    /**
     * Padding baked into the digital layouts. These are per-edge values, so twice each is taken
     * off before sizing the text.
     */
    private const val DIGITAL_PADDING_H_DP = 6
    private const val DIGITAL_PADDING_V_DP = 4

    /**
     * How far out the second hand's dot sits, as a share of the dial's radius. Taken from
     * material_you_analog_clock_second.xml: the dot is centred at y=50 in a 380 unit viewport
     * whose middle is 190, so it orbits at 140/190 of the radius. This leaves the dot and its
     * opposing date label clear of the dial edge.
     */
    private const val SECOND_DOT_ORBIT = 140f / 190f

    /** Trim on the geometric width, so text never sits right up against the arc. */
    private const val ROW_WIDTH_SAFETY = 0.94f

    /**
     * Share of a row's slot the glyphs actually cover, mirroring WidgetTextRenderer's own fill.
     * The containment test needs the ink's extent, not the slot's, or it measures the corner of a
     * rectangle that is taller than anything being drawn.
     */
    private const val ROW_INK_FILL = 0.82f

    /** Share of the corner radius kept clear above and below the content. */
    private const val CORNER_VERTICAL_INSET = 0.16f

    /** Corner radius of each background, matching the drawables the shapes point at. */
    private fun WidgetPrefs.BackgroundShape.cornerRadiusPx(
        density: Float,
        widgetWidthPx: Int,
        widgetHeightPx: Int
    ): Float {
        val nominal = when (this) {
            // The pill drawable uses a radius larger than any widget, so it is always fully round.
            WidgetPrefs.BackgroundShape.PILL -> Float.MAX_VALUE
            WidgetPrefs.BackgroundShape.ROUNDED -> 28f * density
            WidgetPrefs.BackgroundShape.SQUARE -> 8f * density
        }
        return minOf(nominal, widgetWidthPx / 2f, widgetHeightPx / 2f)
    }

    /** Relative shares of the widget's height. Only the visible elements are counted. */
    private object Weights {
        const val DATE = 1.0f
        const val STACKED_DIGIT = 2.6f
        const val ONE_LINE_TIME = 3.0f
        const val SPLIT_TIME = 4.2f
        const val NEXT_ALARM = 0.9f
    }

    fun analog(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_analog_clock)
        views.setOnClickPendingIntent(R.id.analog_widget_root, openAppPendingIntent(context))

        views.setIcon(
            R.id.analog_widget_clock,
            "setDial",
            Icon.createWithResource(context, WidgetPrefs.dialShape(context, appWidgetId).drawableRes)
        )

        val secondHand = if (WidgetPrefs.showSecondHand(context, appWidgetId)) {
            R.drawable.material_you_analog_clock_second
        } else {
            R.drawable.widget_analog_hand_none
        }

        // The date rides the same orbit as the second hand's dot, on the far side of the dial.
        val showDate = WidgetPrefs.showDate(context, appWidgetId)
        if (!showDate) {
            views.setIcon(
                R.id.analog_widget_clock,
                "setSecondHand",
                Icon.createWithResource(context, secondHand)
            )
            views.setViewVisibility(R.id.analog_widget_date, View.GONE)
            views.setViewVisibility(R.id.analog_widget_date_image, View.GONE)
            return views
        }

        val (widthPx, heightPx) = widgetSizePx(context, appWidgetId, 110, 110)
        // AnalogClock keeps its square drawable centred, so the dial is the smaller dimension.
        val dialSizePx = min(widthPx, heightPx)
        // AnalogClock draws its hands in the dial drawable's intrinsic coordinate space, then
        // scales that canvas to the widget. Match the hand's intrinsic size here so the date has
        // the same orbit and scale as the original second-hand dot.
        val handSizePx = context.getDrawable(secondHand)
            ?.intrinsicWidth
            ?.takeIf { it > 0 }
            ?: dialSizePx

        val label = WidgetTextRenderer.renderOnDial(
            context = context,
            text = formatNow(
                WidgetPrefs.dateFormat(context, appWidgetId, DEFAULT_ANALOG_DATE_FORMAT)
            ),
            dialSizePx = handSizePx,
            orbitFraction = SECOND_DOT_ORBIT,
            angleDegrees = 180f,
            colorRes = R.color.widget_text_color,
            wide = WidgetPrefs.wideText(context, appWidgetId)
        )

        if (label == null) {
            views.setIcon(
                R.id.analog_widget_clock,
                "setSecondHand",
                Icon.createWithResource(context, secondHand)
            )
            // Fall back to the plain TextClock if the font could not be loaded.
            views.setViewVisibility(R.id.analog_widget_date_image, View.GONE)
            views.setViewVisibility(R.id.analog_widget_date, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.analog_widget_date, View.GONE)
            views.setViewVisibility(R.id.analog_widget_date_image, View.GONE)
            views.setIcon(
                R.id.analog_widget_clock,
                "setSecondHand",
                Icon.createWithBitmap(secondHandWithDate(context, secondHand, handSizePx, label))
            )
        }

        return views
    }

    /** Combines the second hand and opposing date so [android.widget.AnalogClock] rotates both. */
    private fun secondHandWithDate(
        context: Context,
        secondHandRes: Int,
        dialSizePx: Int,
        label: WidgetTextRenderer.DialLabel
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(dialSizePx, dialSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        context.getDrawable(secondHandRes)?.run {
            setBounds(0, 0, dialSizePx, dialSizePx)
            draw(canvas)
        }
        canvas.drawBitmap(
            label.bitmap,
            dialSizePx / 2f + label.offsetXPx - label.bitmap.width / 2f,
            dialSizePx / 2f + label.offsetYPx - label.bitmap.height / 2f,
            null
        )
        return bitmap
    }

    /** One line of the digital layout, paired with the TextClock it replaces. */
    private class Row(
        val textViewId: Int,
        val imageViewId: Int,
        val text: String,
        val weight: Float,
        val colorRes: Int,
        val minimumWidthFraction: Float = 0f,
        val widthSafety: Float = ROW_WIDTH_SAFETY
    )

    /** Everything needed to place one candidate arrangement inside the widget. */
    private class Layout(
        val rows: List<Row>,
        val widgetWidthPx: Int,
        val widgetHeightPx: Int,
        val contentHeightPx: Float,
        val cornerRadiusPx: Float,
        val paddingHPx: Float
    ) {
        val totalWeight = rows.fold(0f) { acc, row -> acc + row.weight }.coerceAtLeast(0.001f)

        /** Box for [index], sized from where the row sits inside the rounded background. */
        fun boxFor(index: Int): Pair<Int, Int> {
            // Rows are stacked from the top of the centred content block.
            var top = -contentHeightPx / 2f
            rows.take(index).forEach { top += contentHeightPx * it.weight / totalWeight }
            val height = contentHeightPx * rows[index].weight / totalWeight
            val bottom = top + height

            // The glyphs sit centred in the slot, so it is the ink's own top and bottom corners
            // that have to stay inside the shape - not the slot's, which is taller.
            val centre = (top + bottom) / 2f
            val inkHalfHeight = height * ROW_INK_FILL / 2f
            val reach = abs(centre) + inkHalfHeight
            val available = 2f * availableHalfWidthPx(
                distanceFromCentre = reach,
                widgetWidthPx = widgetWidthPx.toFloat(),
                widgetHeightPx = widgetHeightPx.toFloat(),
                cornerRadiusPx = cornerRadiusPx
            )
            // A pill approaches a circle as it gets taller. Its inscribed rectangle then becomes
            // narrower, which used to make a split clock shrink when the user added height. The
            // split bitmap has transparent corners, so retain its wide-layout baseline instead.
            val baseMinimumFraction = rows[index].minimumWidthFraction
            val squareProgress = if (baseMinimumFraction > 0f) {
                ((widgetHeightPx / widgetWidthPx.toFloat() - 0.68f) / 0.32f).coerceIn(0f, 1f)
            } else {
                0f
            }
            val minimumWidthFraction = baseMinimumFraction +
                (0.96f - baseMinimumFraction) * squareProgress
            val minimumAvailable = widgetWidthPx * minimumWidthFraction
            val boxWidth = (
                (maxOf(available, minimumAvailable) - paddingHPx) * rows[index].widthSafety
            )
                .roundToInt()
                .coerceAtLeast(1)
            return boxWidth to height.roundToInt().coerceAtLeast(1)
        }
    }

    /**
     * Renders the digital widget.
     *
     * The time is laid out either on one line or stacked as hour over minute. In [ClockLayout.AUTO]
     * both arrangements are measured and the one that renders the time taller wins, so a wide short
     * widget gets a single line and a tall narrow one gets the stack, without the user choosing.
     */
    fun digital(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_digital_clock)
        views.setOnClickPendingIntent(R.id.digital_widget_root, openAppPendingIntent(context))

        val shape = WidgetPrefs.backgroundShape(context, appWidgetId)
        views.setInt(R.id.digital_widget_root, "setBackgroundResource", shape.drawableRes)
        views.setColorStateList(
            R.id.digital_widget_root,
            "setBackgroundTintList",
            ColorStateList.valueOf(context.getColor(R.color.widget_background_color))
        )

        val wide = WidgetPrefs.wideText(context, appWidgetId)
        val automaticWidth = WidgetPrefs.automaticWidth(context, appWidgetId)
        val showDate = WidgetPrefs.showDate(context, appWidgetId)
        val nextAlarm = if (WidgetPrefs.showNextAlarm(context, appWidgetId)) {
            nextAlarmText(context)
        } else {
            null
        }

        val (widgetWidthPx, widgetHeightPx) = widgetSizePx(
            context = context,
            appWidgetId = appWidgetId,
            fallbackWidthDp = 200,
            fallbackHeightDp = 150
        )
        val density = context.resources.displayMetrics.density
        // Both layout paddings apply to two edges each.
        val paddingHPx = DIGITAL_PADDING_H_DP * 2 * density
        val paddingVPx = DIGITAL_PADDING_V_DP * 2 * density

        val cornerRadiusPx = shape.cornerRadiusPx(density, widgetWidthPx, widgetHeightPx)

        // Keep the content clear of the curved top and bottom, so the first and last rows are not
        // squeezed into the narrowest part of the shape.
        val verticalInsetPx = cornerRadiusPx * CORNER_VERTICAL_INSET
        val contentHeightPx = (widgetHeightPx - paddingVPx - verticalInsetPx * 2f)
            .coerceAtLeast(1f)

        fun build(oneLine: Boolean) = Layout(
            rows = buildRows(context, appWidgetId, oneLine, showDate, nextAlarm),
            widgetWidthPx = widgetWidthPx,
            widgetHeightPx = widgetHeightPx,
            contentHeightPx = contentHeightPx,
            cornerRadiusPx = cornerRadiusPx,
            paddingHPx = paddingHPx
        )

        val clockLayout = WidgetPrefs.clockLayout(context, appWidgetId)
        val isSplit = clockLayout == WidgetPrefs.ClockLayout.SPLIT_12 ||
            clockLayout == WidgetPrefs.ClockLayout.SPLIT_24
        val layout = when (clockLayout) {
            WidgetPrefs.ClockLayout.ONE_LINE -> build(oneLine = true)
            WidgetPrefs.ClockLayout.STACKED -> build(oneLine = false)
            WidgetPrefs.ClockLayout.SPLIT_12,
            WidgetPrefs.ClockLayout.SPLIT_24 -> Layout(
                rows = buildSplitRows(context, appWidgetId, showDate, nextAlarm),
                widgetWidthPx = widgetWidthPx,
                widgetHeightPx = widgetHeightPx,
                contentHeightPx = contentHeightPx,
                cornerRadiusPx = cornerRadiusPx,
                paddingHPx = paddingHPx
            )
            WidgetPrefs.ClockLayout.AUTO -> {
                val oneLine = build(oneLine = true)
                val stacked = build(oneLine = false)
                if (timeInkHeight(context, oneLine, wide) >= timeInkHeight(context, stacked, wide)) {
                    oneLine
                } else {
                    stacked
                }
            }
        }

        // Anything this arrangement does not use has to be hidden, or a stale bitmap lingers.
        val used = layout.rows.map { it.imageViewId }.toSet()
        ALL_DIGITAL_VIEWS.forEach { (textViewId, imageViewId) ->
            if (imageViewId !in used) {
                views.setViewVisibility(textViewId, View.GONE)
                views.setViewVisibility(imageViewId, View.GONE)
            }
        }

        layout.rows.forEachIndexed { index, row ->
            val (boxWidth, boxHeight) = layout.boxFor(index)
            val isNextAlarm = row.textViewId == R.id.digital_widget_next_alarm
            val alarmIconSize = (boxHeight * ROW_INK_FILL).roundToInt()
            val textBoxWidth = if (isNextAlarm) {
                views.setViewLayoutWidth(
                    R.id.digital_widget_next_alarm_icon,
                    alarmIconSize.toFloat(),
                    TypedValue.COMPLEX_UNIT_PX
                )
                views.setViewLayoutHeight(
                    R.id.digital_widget_next_alarm_icon,
                    alarmIconSize.toFloat(),
                    TypedValue.COMPLEX_UNIT_PX
                )
                (boxWidth - alarmIconSize - 4f * density).roundToInt().coerceAtLeast(1)
            } else {
                boxWidth
            }
            if (isSplit && row.textViewId == R.id.digital_widget_time) {
                val is24Hour = clockLayout == WidgetPrefs.ClockLayout.SPLIT_24
                val rendered = WidgetTextRenderer.renderSplitClock(
                    context = context,
                    hours = formatNow(if (is24Hour) "HH" else "hh"),
                    minutes = formatNow("mm"),
                    period = if (is24Hour) null else formatNow("a"),
                    boxWidthPx = textBoxWidth,
                    boxHeightPx = boxHeight,
                    hourColorRes = R.color.vertical_digital_widget_hour_color,
                    minuteColorRes = R.color.vertical_digital_widget_minute_color,
                    automaticWidth = automaticWidth,
                    wide = wide
                )
                if (rendered == null) {
                    views.applyText(
                        context = context,
                        textViewId = row.textViewId,
                        imageViewId = row.imageViewId,
                        visible = true,
                        text = row.text,
                        boxWidthPx = textBoxWidth,
                        boxHeightPx = boxHeight,
                        colorRes = row.colorRes,
                        wide = wide
                    )
                } else {
                    views.setViewVisibility(row.textViewId, View.GONE)
                    views.setViewVisibility(row.imageViewId, View.VISIBLE)
                    views.setImageViewBitmap(row.imageViewId, rendered)
                    views.setContentDescription(row.imageViewId, row.text)
                }
            } else {
                views.applyText(
                    context = context,
                    textViewId = row.textViewId,
                    imageViewId = row.imageViewId,
                    visible = true,
                    text = row.text,
                    boxWidthPx = textBoxWidth,
                    boxHeightPx = boxHeight,
                    colorRes = row.colorRes,
                    wide = wide
                )
            }
        }

        if (nextAlarm == null) {
            views.setViewVisibility(R.id.digital_widget_next_alarm_group, View.GONE)
        } else {
            views.setViewVisibility(R.id.digital_widget_next_alarm_group, View.VISIBLE)
            views.setContentDescription(
                R.id.digital_widget_next_alarm_group,
                context.getString(R.string.next_alarm_description, nextAlarm)
            )
        }

        return views
    }

    /** Every text/image pair in the digital layout, so unused rows can be hidden. */
    private val ALL_DIGITAL_VIEWS = listOf(
        R.id.digital_widget_date to R.id.digital_widget_date_image,
        R.id.digital_widget_time to R.id.digital_widget_time_image,
        R.id.digital_widget_hour to R.id.digital_widget_hour_image,
        R.id.digital_widget_minute to R.id.digital_widget_minute_image,
        R.id.digital_widget_next_alarm to R.id.digital_widget_next_alarm_image
    )

    private fun buildRows(
        context: Context,
        appWidgetId: Int,
        oneLine: Boolean,
        showDate: Boolean,
        nextAlarm: String?
    ): List<Row> = buildList {
        if (showDate) {
            add(
                Row(
                    textViewId = R.id.digital_widget_date,
                    imageViewId = R.id.digital_widget_date_image,
                    text = formatNow(
                        WidgetPrefs.dateFormat(context, appWidgetId, DEFAULT_DIGITAL_DATE_FORMAT)
                    ),
                    weight = Weights.DATE,
                    colorRes = R.color.widget_text_color
                )
            )
        }
        if (oneLine) {
            add(
                Row(
                    textViewId = R.id.digital_widget_time,
                    imageViewId = R.id.digital_widget_time_image,
                    text = formatNow(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm"),
                    weight = Weights.ONE_LINE_TIME,
                    colorRes = R.color.vertical_digital_widget_minute_color
                )
            )
        } else {
            add(
                Row(
                    textViewId = R.id.digital_widget_hour,
                    imageViewId = R.id.digital_widget_hour_image,
                    text = formatNow(if (DateFormat.is24HourFormat(context)) "HH" else "hh"),
                    weight = Weights.STACKED_DIGIT,
                    colorRes = R.color.vertical_digital_widget_hour_color
                )
            )
            add(
                Row(
                    textViewId = R.id.digital_widget_minute,
                    imageViewId = R.id.digital_widget_minute_image,
                    text = formatNow("mm"),
                    weight = Weights.STACKED_DIGIT,
                    colorRes = R.color.vertical_digital_widget_minute_color
                )
            )
        }
        if (nextAlarm != null) {
            add(
                Row(
                    textViewId = R.id.digital_widget_next_alarm,
                    imageViewId = R.id.digital_widget_next_alarm_image,
                    text = nextAlarm,
                    weight = Weights.NEXT_ALARM,
                    colorRes = R.color.next_alarm_widget_alarm_color
                )
            )
        }
    }

    private fun buildSplitRows(
        context: Context,
        appWidgetId: Int,
        showDate: Boolean,
        nextAlarm: String?
    ): List<Row> = buildList {
        if (showDate) {
            add(
                Row(
                    textViewId = R.id.digital_widget_date,
                    imageViewId = R.id.digital_widget_date_image,
                    text = formatNow(
                        WidgetPrefs.dateFormat(context, appWidgetId, DEFAULT_DIGITAL_DATE_FORMAT)
                    ),
                    weight = Weights.DATE,
                    colorRes = R.color.widget_text_color
                )
            )
        }
        add(
            Row(
                textViewId = R.id.digital_widget_time,
                imageViewId = R.id.digital_widget_time_image,
                text = formatNow(
                    if (WidgetPrefs.clockLayout(context, appWidgetId) == WidgetPrefs.ClockLayout.SPLIT_24) {
                        "HH:mm"
                    } else {
                        "hh:mm a"
                    }
                ),
                weight = Weights.SPLIT_TIME,
                colorRes = R.color.vertical_digital_widget_minute_color,
                minimumWidthFraction = 0.80f,
                widthSafety = 1f
            )
        )
        if (nextAlarm != null) {
            add(
                Row(
                    textViewId = R.id.digital_widget_next_alarm,
                    imageViewId = R.id.digital_widget_next_alarm_image,
                    text = nextAlarm,
                    weight = Weights.NEXT_ALARM,
                    colorRes = R.color.next_alarm_widget_alarm_color
                )
            )
        }
    }

    /** How tall the clock's glyphs come out in [layout], used to pick the better arrangement. */
    private fun timeInkHeight(context: Context, layout: Layout, wide: Boolean): Int {
        val timeIds = setOf(R.id.digital_widget_time, R.id.digital_widget_hour)
        val index = layout.rows.indexOfFirst { it.textViewId in timeIds }
        if (index < 0) return 0
        val (boxWidth, boxHeight) = layout.boxFor(index)
        return WidgetTextRenderer.measuredInkHeight(
            context = context,
            text = layout.rows[index].text,
            boxWidthPx = boxWidth,
            boxHeightPx = boxHeight,
            wide = wide
        )
    }

    /**
     * Half the width available inside the rounded background at [distanceFromCentre] above or
     * below the middle. Inside the straight section that is simply half the widget; once into a
     * corner it follows the arc, which is what stops text at the top and bottom of a pill from
     * spilling over the curve.
     */
    private fun availableHalfWidthPx(
        distanceFromCentre: Float,
        widgetWidthPx: Float,
        widgetHeightPx: Float,
        cornerRadiusPx: Float
    ): Float {
        val halfWidth = widgetWidthPx / 2f
        val straightHalfHeight = widgetHeightPx / 2f - cornerRadiusPx
        if (distanceFromCentre <= straightHalfHeight) return halfWidth

        val intoCorner = (distanceFromCentre - straightHalfHeight).coerceAtMost(cornerRadiusPx)
        val arc = sqrt((cornerRadiusPx * cornerRadiusPx - intoCorner * intoCorner).coerceAtLeast(0f))
        return (halfWidth - cornerRadiusPx + arc).coerceAtLeast(1f)
    }

    /** Pushes a freshly rendered widget of whichever kind [appWidgetId] belongs to. */
    fun refresh(context: Context, appWidgetId: Int, relayToLawnchair: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider?.className ?: return
        val renderContext = LawnchairWidgetTheme.renderContext(context)

        val views = when (provider) {
            AnalogClockWidgetProvider::class.java.name -> analog(renderContext, appWidgetId)
            DigitalClockWidgetProvider::class.java.name -> digital(renderContext, appWidgetId)
            else -> return
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
        if (relayToLawnchair) {
            LawnchairWidgetRelay.send(context, appWidgetId, views)
        }
    }

    /**
     * The widget's size in pixels. In portrait a widget is MIN_WIDTH wide and MAX_HEIGHT tall;
     * the options bundle is empty until the host has measured it, hence the fallbacks.
     */
    private fun widgetSizePx(
        context: Context,
        appWidgetId: Int,
        fallbackWidthDp: Int,
        fallbackHeightDp: Int
    ): Pair<Int, Int> {
        val options: Bundle? = AppWidgetManager.getInstance(context)
            ?.getAppWidgetOptions(appWidgetId)

        val widthDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            ?.takeIf { it > 0 } ?: fallbackWidthDp
        val heightDp = options
            ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            ?.takeIf { it > 0 } ?: fallbackHeightDp

        val density = context.resources.displayMetrics.density
        return (widthDp * density).roundToInt() to (heightDp * density).roundToInt()
    }

    /**
     * Shows [text] as a bitmap in the Feldman font, hiding the TextClock twin that only exists
     * for the widget picker's preview. Falls back to that TextClock if the font cannot be loaded.
     */
    private fun RemoteViews.applyText(
        context: Context,
        textViewId: Int,
        imageViewId: Int,
        visible: Boolean,
        text: String,
        boxWidthPx: Int,
        boxHeightPx: Int,
        colorRes: Int,
        wide: Boolean
    ) {
        if (!visible) {
            setViewVisibility(textViewId, View.GONE)
            setViewVisibility(imageViewId, View.GONE)
            return
        }

        val rendered = WidgetTextRenderer.render(
            context = context,
            text = text,
            boxWidthPx = boxWidthPx,
            boxHeightPx = boxHeightPx,
            colorRes = colorRes,
            wide = wide
        )

        if (rendered == null) {
            setViewVisibility(imageViewId, View.GONE)
            setViewVisibility(textViewId, View.VISIBLE)
            setTextViewText(textViewId, text)
        } else {
            setViewVisibility(textViewId, View.GONE)
            setViewVisibility(imageViewId, View.VISIBLE)
            setImageViewBitmap(imageViewId, rendered)
        }
    }

    private fun formatNow(pattern: String): String =
        DateFormat.format(pattern, Date()).toString()

    /** Formatted time of the next scheduled alarm, or null when nothing is scheduled. */
    private fun nextAlarmText(context: Context): String? =
        AlarmUtils.getNextAlarmClockText(context)
}
