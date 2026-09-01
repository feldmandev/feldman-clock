package com.feldman.clock.ui.settings.pages

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.R
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.motion.MotionLevel
import com.feldman.motion.MotionScaffold
import com.feldman.motion.MotionSymbols
import com.feldman.motion.MotionThemeRepository
import com.feldman.motion.isDarkTheme
import com.feldman.motion.rememberSymbolPainter
import com.feldman.motion.themeColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current
    val themeRepository = remember(context) { MotionThemeRepository(context) }
    val scope = rememberCoroutineScope()
    val themeMode by themeRepository.themeMode.collectAsState(initial = 0)
    val themeColor by themeRepository.themeColor.collectAsState(initial = 0)
    val dynamicColor by themeRepository.dynamicColor.collectAsState(initial = true)
    val tintPalette by themeRepository.tintPalette.collectAsState(initial = false)
    val motionLevel by themeRepository.motionLevel.collectAsState(initial = MotionLevel.MEDIUM)
    val expressiveDesign by themeRepository.expressiveDesign.collectAsState(initial = true)
    val useDark = isDarkTheme()
    val dynamicColorSeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        colorScheme.primary
    }

    MotionScaffold(
        scaffoldModifier = Modifier.fillMaxSize(),
        topBar = {
            SettingsTopBar(
                title = "Appearance",
                chromeColor = SettingsCategoryColor.THEME.container(useDark),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Theme mode")
        section {
            val options = listOf(
                Triple(0, "System", R.drawable.ic_brightness_auto),
                Triple(1, "Light", R.drawable.ic_light_mode),
                Triple(2, "Dark", R.drawable.ic_dark_mode)
            )

            options.forEach { (id, label, icon) ->
                choiceItem(
                    key = id,
                    title = label,
                    icon = painterResource(icon),
                    selected = themeMode == id,
                    containerColor = if (themeMode == id) colorScheme.primary else colorScheme.surfaceVariant,
                    onClick = { scope.launch { themeRepository.setThemeMode(id) } }
                )
            }
        }

        title("Theme color")
        section {
            colorPickerItem(
                colors = themeColors.map { pair -> if (useDark) pair.dark else pair.light },
                selectedIndex = if (dynamicColor) -1 else themeColor,
                onSelected = { index ->
                    scope.launch {
                        themeRepository.setThemeColor(index)
                        themeRepository.setDynamicColor(false)
                    }
                },
                dynamicColor = dynamicColorSeed,
                dynamicColorSelected = dynamicColor,
                onDynamicColorSelected = {
                    scope.launch { themeRepository.setDynamicColor(true) }
                },
                dynamicColorIcon = rememberSymbolPainter(MotionSymbols.ic_hdr_auto)
            )
            switchItem(
                title = "Vibrant palette",
                description = "Use higher saturation tones",
                icon = painterResource(R.drawable.ic_palette),
                checked = tintPalette,
                onCheckedChange = { checked ->
                    scope.launch { themeRepository.setTintPalette(checked) }
                },
                visible = !dynamicColor
            )
        }

        title("Design")
        section {
            switchItem(
                title = "Expressive design",
                description = "Use expressive, bold UI",
                icon = painterResource(R.drawable.ic_animation),
                iconShape = if (expressiveDesign) MaterialShapes.Cookie12Sided else MaterialShapes.Circle,
                iconMorphShape = if (expressiveDesign) MaterialShapes.Circle else MaterialShapes.Cookie12Sided,
                iconMorphOnSelection = false,
                checked = expressiveDesign,
                onCheckedChange = { checked ->
                    scope.launch { themeRepository.setExpressiveDesign(checked) }
                }
            )
        }

        title("Motion")
        section {
            val options = listOf(
                Triple(MotionLevel.NONE, "None", R.drawable.ic_stop_circle),
                Triple(MotionLevel.LOW, "Low", R.drawable.ic_trail_length_short),
                Triple(MotionLevel.MEDIUM, "Medium", R.drawable.ic_trail_length_medium),
                Triple(MotionLevel.HIGH, "High", R.drawable.ic_trail_length)
            )

            options.forEach { (level, label, icon) ->
                choiceItem(
                    key = level.id,
                    title = label,
                    selected = motionLevel == level,
                    icon = painterResource(icon),
                    containerColor = if (motionLevel == level) colorScheme.primary else colorScheme.surfaceVariant,
                    onClick = { scope.launch { themeRepository.setMotionLevel(level) } }
                )
            }
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
}
