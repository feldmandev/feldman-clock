package com.feldman.clock.ui.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.feldman.clock.app.MainActivity

/** Tapping any clock widget opens the Clock page. */
internal fun openAppPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_SHOW_CLOCK
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
