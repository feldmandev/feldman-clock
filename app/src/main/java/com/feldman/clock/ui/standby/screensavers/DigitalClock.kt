package com.feldman.clock.ui.standby.screensavers

import com.feldman.clock.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.util.color.derivedPalette
import com.feldman.clock.ui.standby.util.text.slantForIndex
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.feldman.clock.core.data.standby.UiColors
import com.feldman.motion.feldmanFont


@Composable
fun DigitalClock(
    time: LocalTime,
    scheme: UiColors,
    weight: Int,
    showSeconds: Boolean,
    userScale: Float,
    isSingleColorMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember {
        ClockApplication.getDefaultSharedPreferences(context)
    }

    val pattern = if (showSeconds) "HH:mm:ss" else "HH:mm"
    val display = time.format(DateTimeFormatter.ofPattern(pattern))
    val maxSlant = remember {
        SettingsDAO.getStandbyMaxSlant(prefs).toFloat()
    }
    val fontWidth = remember {
        SettingsDAO.getStandbyFontWidth(prefs).toFloat()
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val palette = remember(display, scheme.primary) {
            derivedPalette(scheme.primary, display.length)
        }
        val annotated = buildAnnotatedString {
            display.forEachIndexed { index, ch ->
                val slant = slantForIndex(index, display.length, maxSlant)

                val digitColor = if (isSingleColorMode) {
                    scheme.primary
                } else {
                    palette[index % palette.size]
                }

                pushStyle(
                    SpanStyle(
                        fontFamily = feldmanFont(
                            weight = weight,
                            width = fontWidth,
                            slant = slant
                        ),
                        color = digitColor
                    )
                )
                append(ch)
                pop()
            }
        }



        Text(
            text = annotated,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                fontFamily = feldmanFont,
                fontWeight = FontWeight(weight.coerceIn(100, 900)),
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = (2000 * userScale).sp,
                stepSize = 1.sp
            )
        )
    }

}
