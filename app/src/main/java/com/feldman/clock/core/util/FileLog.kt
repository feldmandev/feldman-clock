package com.feldman.clock.core.util

import com.feldman.clock.R

import com.feldman.clock.app.ClockApplication

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private const val TAG = "FileLog"
    private const val LOG_FILE_NAME = "alarm_debug_log.txt"
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e(tag, message)
        write("E", tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String, tr: Throwable?) {
        Log.e(tag, message, tr)
        write("E", tag, "$message\n${Log.getStackTraceString(tr)}")
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        write("W", tag, message)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message)
    }

    @JvmStatic
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D", tag, message)
    }

    @JvmStatic
    fun v(tag: String, message: String) {
        Log.v(tag, message)
        write("V", tag, message)
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String) {
        // We need a context to get filesDir. 
        // Ideally this should be initialized, but for this quick debug utility 
        // let's try to grab it from ClockApplication if possible,
        // or require passing it. Since we want static calls, 
        // let's assume ClockApplication.getContext() is available based on previous checks.
        
        try {
            // Use Device Protected Storage to ensure logging works during Direct Boot / Locked state
            val context = com.feldman.clock.app.ClockApplication.getContext().createDeviceProtectedStorageContext()
            val file = File(context.filesDir, LOG_FILE_NAME)
            
            // Limit file size (e.g., 5MB) - primitive rotation
            if (file.exists() && file.length() > 5 * 1024 * 1024) {
                 val backup = File(context.filesDir, "$LOG_FILE_NAME.bak")
                 file.renameTo(backup)
            }

            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println("${DATE_FORMAT.format(Date())} $level/$tag: $message")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
}
