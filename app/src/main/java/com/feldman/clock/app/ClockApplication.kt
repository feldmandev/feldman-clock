/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.app

import com.feldman.clock.R

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.feldman.clock.core.controller.Controller
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.analytics.LogEventTracker
import com.feldman.clock.core.util.NotificationUtils
import java.io.File
import androidx.core.net.toUri

class ClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Companion.applicationContext = this

        val prefs = getDefaultSharedPreferences(applicationContext)

        DataModel.dataModel.init(applicationContext, prefs)

        Controller.getController().apply {
            setContext(applicationContext)
            addEventTracker(LogEventTracker(applicationContext))
            updateShortcuts()
        }

        NotificationUtils.updateNotificationChannels(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivityCount = 0

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    DataModel.dataModel.isApplicationInForeground = true
                    DataModel.dataModel.updateAllNotifications()
                }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount == 0) {
                    DataModel.dataModel.isApplicationInForeground = false
                    DataModel.dataModel.updateAllNotifications()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var applicationContext: Context

        @JvmStatic
        fun getContext(): Context = applicationContext

        /**
         * Returns the default [SharedPreferences] instance from the underlying storage context.
         */
        @JvmStatic
        fun getDefaultSharedPreferences(context: Context): SharedPreferences {

            val storageContext: Context = context.createDeviceProtectedStorageContext()
            val name = context.packageName + "_preferences"
            val prefsFilename = storageContext.dataDir.toString() + "/shared_prefs/" + name + ".xml"
            val prefs = File(prefsFilename.toUri().path!!)

            if (!prefs.exists()) {
                if (!storageContext.moveSharedPreferencesFrom(context, name)) {
                    Log.wtf("ClockApplication" ,"Failed to migrate shared preferences")
                }
            }

            return PreferenceManager.getDefaultSharedPreferences(storageContext)
        }
    }
}
