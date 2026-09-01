package com.feldman.clock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.feldman.motion.rememberExpressiveDesign

@Composable
fun expressiveChromeColor(fallback: Color = MaterialTheme.colorScheme.background): Color {
    return if (rememberExpressiveDesign()) {
        MaterialTheme.colorScheme.primaryContainer
            .copy(alpha = 0.4f)
            .compositeOver(MaterialTheme.colorScheme.background)
    } else {
        fallback
    }
}
