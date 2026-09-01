/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.controller

import androidx.annotation.StringRes
import com.feldman.clock.core.analytics.EventTracker

internal class EventController {

    private val eventTrackers = mutableListOf<EventTracker>()

    fun addEventTracker(eventTracker: EventTracker) {
        eventTrackers.add(eventTracker)
    }

    fun sendEvent(@StringRes category: Int, @StringRes action: Int, @StringRes label: Int) {
        eventTrackers.forEach { it.sendEvent(category, action, label) }
    }
}
