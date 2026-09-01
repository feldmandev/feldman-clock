package com.feldman.clock.ui.standby.screensavers

import com.feldman.clock.R

import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooth seconds-of-minute (0..60) that never goes backward.
 * - Locks ms fraction to wall-clock second boundary
 * - Increments internal second when ms wraps (even if `time` updates late)
 * - Resyncs if your external `time` jumps a lot
 */
@Composable
private fun rememberSmoothSecondOfMinute(time: LocalTime): Float {
    if (LocalInspectionMode.current) return time.second.toFloat()

    var baseSecond by remember { mutableIntStateOf(time.second) }
    var msFrac by remember { mutableFloatStateOf(0f) }
    var lastMs by remember { mutableIntStateOf(0) }

    // If external time changes wildly (timezone/manual set), resync.
    LaunchedEffect(time.hour, time.minute, time.second) {
        // only hard-resync when it's clearly not just normal ticking
        val diff = ((time.second - baseSecond + 60) % 60)
        if (diff != 0 && diff != 1 && diff != 59) {
            baseSecond = time.second
        }
    }

    LaunchedEffect(Unit) {
        // Align elapsedRealtime to wall-clock ms so %1000 matches real second boundaries
        val offset = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        fun wallMs(): Int = (((SystemClock.elapsedRealtime() + offset) % 1000L).toInt())

        lastMs = wallMs()
        while (true) {
            kotlinx.coroutines.android.awaitFrame()
            val ms = wallMs()

            // If ms wrapped (e.g. 998 -> 3), we've crossed a real second boundary
            if (ms < lastMs) {
                baseSecond = (baseSecond + 1) % 60
            }

            lastMs = ms
            msFrac = ms / 1000f
        }
    }

    return baseSecond + msFrac
}

private fun dialBlobPath(
    center: Offset,
    faceRadius: Float
): android.graphics.Path {
    val scallops = 12
    val outer = faceRadius
    val inner = faceRadius * 0.93f

    val poly = RoundedPolygon.star(
        numVerticesPerRadius = scallops,
        radius = outer,
        innerRadius = inner,
        rounding = CornerRounding(faceRadius * 0.28f, smoothing = 0.6f),
        innerRounding = CornerRounding(faceRadius * 0.20f, smoothing = 0.6f),
        centerX = center.x,
        centerY = center.y
    )

    val p = poly.toPath()
    val m = android.graphics.Matrix().apply { setRotate(-90f, center.x, center.y) }
    p.transform(m)
    return p
}


@Composable
fun AnalogClock(
    time: LocalTime,
    scheme: com.feldman.clock.core.data.standby.UiColors,
    showSeconds: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 500.dp
) {
    val smoothSec = rememberSmoothSecondOfMinute(time)

    Canvas(modifier = modifier.size(sizeDp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        fun toRad(deg: Float) = ((deg - 90f) * PI / 180f).toFloat() // 0° at 12
        fun pointAt(angleDeg: Float, length: Float): Offset {
            val r = toRad(angleDeg)
            return Offset(center.x + cos(r) * length, center.y + sin(r) * length)
        }

        val faceColor = scheme.surfaceVariant
        val shadowColor = Color.Black.copy(alpha = 0.35f)

        val hourColor = lerp(scheme.primary, Color.White, 0.25f)
        val minuteColor = lerp(scheme.primary, Color.White, 0.70f)
        val secondDotColor = scheme.tertiary

        val faceRadius = radius * 0.88f

        // graphics-shapes blob
        val blobPath = dialBlobPath(center, faceRadius)

        // Shadow + fill
        drawIntoCanvas { canvas ->
            val pShadow = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.FILL
                color = faceColor.toArgb()
                setShadowLayer(24f, 0f, 10f, shadowColor.toArgb())
            }
            canvas.nativeCanvas.drawPath(blobPath, pShadow)

            // Draw again without shadow so edge is crisp
            val pFill = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.FILL
                color = faceColor.toArgb()
                clearShadowLayer()
            }
            canvas.nativeCanvas.drawPath(blobPath, pFill)
        }

        // Angles
        val secAngle = (smoothSec / 60f) * 360f
        val minAngle = ((time.minute + smoothSec / 60f) / 60f) * 360f
        val hourAngle = (((time.hour % 12) + (time.minute / 60f) + (smoothSec / 3600f)) / 12f) * 360f

        // Hands
        val hourLen = faceRadius * 0.40f
        val minLen = faceRadius * 0.60f

        val hourStroke = 32.dp.toPx()
        val minStroke = 32.dp.toPx()

        drawShadowedLine(
            start = center,
            end = pointAt(hourAngle, hourLen),
            color = hourColor,
            strokePx = hourStroke,
            shadowColor = Color.Black.copy(alpha = 0.25f),
            shadowBlurPx = 18f,
            shadowDyPx = 6f
        )

        drawShadowedLine(
            start = center,
            end = pointAt(minAngle, minLen),
            color = minuteColor,
            strokePx = minStroke,
            shadowColor = Color.Black.copy(alpha = 0.22f),
            shadowBlurPx = 16f,
            shadowDyPx = 5f
        )

        if (showSeconds) {
            // Seconds dot (now truly smooth + never backward mid-second)
            val dotOrbit = faceRadius * 0.82f
            val dotPos = pointAt(secAngle, dotOrbit)
            drawCircle(color = secondDotColor, radius = 16.dp.toPx(), center = dotPos)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShadowedLine(
    start: Offset,
    end: Offset,
    color: Color,
    strokePx: Float,
    shadowColor: Color,
    shadowBlurPx: Float,
    shadowDxPx: Float = 0f,
    shadowDyPx: Float = 0f
) {
    drawIntoCanvas { canvas ->
        val p = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            this.color = color.toArgb()
            strokeWidth = strokePx
            strokeCap = AndroidPaint.Cap.ROUND
            setShadowLayer(shadowBlurPx, shadowDxPx, shadowDyPx, shadowColor.toArgb())
        }
        canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, p)
    }
}
