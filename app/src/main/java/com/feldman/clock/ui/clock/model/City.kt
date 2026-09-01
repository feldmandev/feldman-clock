package com.feldman.clock.ui.clock.model

import java.util.TimeZone

data class City(
    val id: String,
    val name: String,
    val timeZone: String,
    val country: String = "",
    val state: String = ""
) {
    // Helper to get helper text like "+5 HRS"
    fun getTimeDifferenceStr(): String {
        // Simplified logic for demo
        val tz = TimeZone.getTimeZone(timeZone)
        val offset = tz.rawOffset
        val localOffset = TimeZone.getDefault().rawOffset
        val diffHours = (offset - localOffset) / (1000 * 60 * 60)
        
        return if (diffHours > 0) "+${diffHours}h" else "${diffHours}h"
    }
}
