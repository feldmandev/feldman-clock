/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.controller

import com.feldman.clock.R

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import com.feldman.clock.core.analytics.EventTracker
import com.feldman.clock.core.util.Utils.enforceMainLooper

/**
 * Interactions with Android framework components responsible for part of the user experience are
 * handled via this singleton.
 */
class Controller private constructor() {

    private lateinit var context: Context

    /**
     * The controller that dispatches app events to event trackers.
     */
    private lateinit var eventController: EventController

    /**
     * The controller that interacts with voice interaction sessions on M+.
     */
    private lateinit var voiceController: VoiceController

    /**
     * The controller that creates and updates launcher shortcuts on N MR1+
     */
    private var shortcutController: ShortcutController? = null

    fun setContext(context: Context) {
        if (!::context.isInitialized || this.context != context) {
            this.context = context.applicationContext
            eventController = EventController()
            voiceController = VoiceController()
            shortcutController = ShortcutController(this.context)
        }
    }

    //
    // Event Tracking
    //

    /**
     * @param eventTracker to be registered for tracking application events
     */
    fun addEventTracker(eventTracker: EventTracker) {
        enforceMainLooper()
        eventController.addEventTracker(eventTracker)
    }

    /**
     * Tracks an event. Events have a category, action and label. This method can be used to track
     * events such as button presses or other user interactions with your application.
     *
     * @param category resource id of event category
     * @param action   resource id of event action
     * @param label    resource id of event label
     */
    fun sendEvent(@StringRes category: Int, @StringRes action: Int, @StringRes label: Int) {
        eventController.sendEvent(category, action, label)
    }

    //
    // Voice Interaction
    //

    fun notifyVoiceSuccess(activity: Activity, message: String) {
        voiceController.notifyVoiceSuccess(activity, message)
    }

    fun notifyVoiceFailure(activity: Activity, message: String) {
        voiceController.notifyVoiceFailure(activity, message)
    }

    //
    // Shortcuts
    //

    fun updateShortcuts() {
        enforceMainLooper()
        shortcutController?.updateShortcuts()
    }

    companion object {
        private val sController = Controller()

        @JvmStatic
        fun getController(): Controller = sController
    }
}
