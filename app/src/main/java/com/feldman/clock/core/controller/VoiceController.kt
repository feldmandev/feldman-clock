/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.controller

import com.feldman.clock.R

import android.app.Activity
import android.app.VoiceInteractor

internal class VoiceController {
    /**
     * If the [activity] is currently hosting a voice interaction session, indicate the voice
     * command was processed successfully.
     *
     * @param activity an Activity that may be hosting a voice interaction session
     * @param message to be spoken to the user to indicate success
     */
    fun notifyVoiceSuccess(activity: Activity, message: String) {
        activity.voiceInteractor?.let { voiceInteractor ->
            val prompt = VoiceInteractor.Prompt(message)
            voiceInteractor.submitRequest(VoiceInteractor.CompleteVoiceRequest(prompt, null))
        }
    }

    /**
     * If the [activity] is currently hosting a voice interaction session, indicate the voice
     * command failed and must be aborted.
     *
     * @param activity an Activity that may be hosting a voice interaction session
     * @param message to be spoken to the user to indicate failure
     */
    fun notifyVoiceFailure(activity: Activity, message: String) {
        activity.voiceInteractor?.let { voiceInteractor ->
            val prompt = VoiceInteractor.Prompt(message)
            voiceInteractor.submitRequest(VoiceInteractor.AbortVoiceRequest(prompt, null))
        }
    }
}
