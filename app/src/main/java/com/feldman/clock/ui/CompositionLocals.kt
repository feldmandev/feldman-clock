package com.feldman.clock.ui

import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSetFabClick = staticCompositionLocalOf<((() -> Unit)?) -> Unit> { error("LocalSetFabClick not provided") }
val LocalActivityIntent = staticCompositionLocalOf<Intent?> { null }

/**
 * Shows an app-level snackbar.
 *
 * Wrapped in a class rather than exposed as a bare lambda so the action parameters can be
 * optional — `showSnackbar("Saved")` and
 * `showSnackbar("Alarm deleted", "Undo") { restore() }` both work.
 */
class ShowSnackbar(
    private val show: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit
) {
    operator fun invoke(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) = show(message, actionLabel, onAction)
}

val LocalShowSnackbar = staticCompositionLocalOf<ShowSnackbar> { error("LocalShowSnackbar not provided") }
