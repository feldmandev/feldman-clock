/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.concurrent

import com.feldman.clock.R

import android.os.Handler
import android.os.HandlerThread

/**
 * Helper class for managing the background thread used to perform io operations
 * and handle async broadcasts.
 */
object AsyncHandler {
    private val handlerThread = HandlerThread("AsyncHandler").apply { start() }
    private val handler = Handler(handlerThread.looper)

    @JvmStatic
    fun post(r: Runnable) {
        handler.post(r)
    }
}
