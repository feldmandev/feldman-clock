package com.feldman.clock.ui.components

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feldman.motion.feldmanFont
import com.feldman.motion.rememberExpressiveDesign

@Composable
fun ClockPageTitle(text: String) {
    if (rememberExpressiveDesign()) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = feldmanFont(width = 120f, weight = 900),
            fontSize = 26.sp
        )
    } else {
        Text(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ClockAppBarIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // Primary in both modes. Without expressive design the app bar is already surface-coloured,
    // so a surface-coloured button sat on it with almost no contrast and read as disabled.
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        content = content
    )
}
@Composable
fun ExpressiveContentSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val expressive = rememberExpressiveDesign()
    Surface(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (expressive) {
                    Modifier
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                } else {
                    Modifier
                }
            ),
        color = if (expressive) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        content = content
    )
}
