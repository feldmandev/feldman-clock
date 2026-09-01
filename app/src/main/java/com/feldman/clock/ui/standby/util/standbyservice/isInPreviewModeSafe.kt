package com.feldman.clock.ui.standby.util.standbyservice

import android.service.dreams.DreamService

fun DreamService.isInPreviewModeSafe(): Boolean {
    return try {
        val m = DreamService::class.java.getMethod("isPreviewMode")
        (m.invoke(this) as? Boolean) ?: false
    } catch (_: Throwable) {
        false
    }
}
