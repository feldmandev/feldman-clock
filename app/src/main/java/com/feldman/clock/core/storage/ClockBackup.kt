package com.feldman.clock.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.core.data.Weekdays
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.AlarmInstance
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Exports and restores everything the user configured, as a single JSON file they own.
 *
 * Android's own backup agent already covers device-to-device transfer, but only through a
 * Google account. This is the manual path: it works offline, survives a switch away from
 * Google entirely, and can be inspected before it is restored.
 *
 * Three stores hold user data, and all three are covered:
 *  - alarms live in the [ClockProvider] database,
 *  - timers and every setting live in the default (device-protected) preferences,
 *  - world-clock cities and widget configuration live in their own preference files.
 */
object ClockBackup {

    private const val TAG = "ClockBackup"

    /** Bumped only when the shape changes in a way older builds cannot read. */
    private const val FORMAT_VERSION = 1

    private const val KEY_FORMAT = "format"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_PREFERENCES = "preferences"

    private const val PREFS_DEFAULT = "default"
    private const val PREFS_WORLD_CLOCK = "world_clock_prefs"
    private const val PREFS_WIDGETS = "clock_widget_prefs"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Suggested file name, e.g. `clock-backup-2026-08-07.json`. */
    fun suggestedFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
        return "clock-backup-$date.json"
    }

    // ---------------------------------------------------------------- export

    fun export(context: Context, out: OutputStream) {
        val root = JsonObject().apply {
            addProperty(KEY_FORMAT, FORMAT_VERSION)
            addProperty(KEY_CREATED_AT, System.currentTimeMillis())
            add(KEY_ALARMS, gson.toJsonTree(exportAlarms(context.contentResolver)))
            add(KEY_PREFERENCES, JsonObject().apply {
                add(PREFS_DEFAULT, exportPrefs(defaultPrefs(context)))
                add(PREFS_WORLD_CLOCK, exportPrefs(namedPrefs(context, PREFS_WORLD_CLOCK)))
                add(PREFS_WIDGETS, exportPrefs(namedPrefs(context, PREFS_WIDGETS)))
            })
        }
        out.bufferedWriter().use { it.write(gson.toJson(root)) }
    }

    private fun exportAlarms(resolver: ContentResolver): List<BackedUpAlarm> =
        Alarm.getAlarms(resolver, null).map { alarm ->
            BackedUpAlarm(
                enabled = alarm.enabled,
                year = alarm.year,
                month = alarm.month,
                day = alarm.day,
                hour = alarm.hour,
                minutes = alarm.minutes,
                daysOfWeekBits = alarm.daysOfWeek.mBits,
                vibrate = alarm.vibrate,
                flash = alarm.flash,
                label = alarm.label,
                alert = alarm.alert?.toString(),
                deleteAfterUse = alarm.deleteAfterUse,
                autoSilenceDuration = alarm.autoSilenceDuration,
                snoozeDuration = alarm.snoozeDuration,
                missedAlarmRepeatLimit = alarm.missedAlarmRepeatLimit,
                crescendoDuration = alarm.crescendoDuration,
                alarmVolume = alarm.alarmVolume
            )
        }

    /**
     * Preferences are written with their runtime type recorded, because JSON collapses Long and
     * Int into one number type and restoring a Long as an Int throws on read.
     */
    private fun exportPrefs(prefs: SharedPreferences): JsonObject {
        val out = JsonObject()
        prefs.all.forEach { (key, value) ->
            val entry = JsonObject()
            when (value) {
                is Boolean -> { entry.addProperty("t", "b"); entry.addProperty("v", value) }
                is Int -> { entry.addProperty("t", "i"); entry.addProperty("v", value) }
                is Long -> { entry.addProperty("t", "l"); entry.addProperty("v", value) }
                is Float -> { entry.addProperty("t", "f"); entry.addProperty("v", value) }
                is String -> { entry.addProperty("t", "s"); entry.addProperty("v", value) }
                is Set<*> -> {
                    entry.addProperty("t", "ss")
                    entry.add("v", gson.toJsonTree(value.filterIsInstance<String>()))
                }
                else -> return@forEach
            }
            out.add(key, entry)
        }
        return out
    }

    // --------------------------------------------------------------- restore

    /**
     * Replaces all current data with the contents of [input].
     *
     * Destructive by design: a restore that merged would silently duplicate every alarm each
     * time it ran. Callers are expected to have confirmed with the user first.
     */
    fun import(context: Context, input: InputStream): Result {
        val text = input.bufferedReader().use { it.readText() }
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: return Result.Failure("Not a valid backup file")

        val format = root.get(KEY_FORMAT)?.asInt
            ?: return Result.Failure("Not a Clock backup file")
        if (format > FORMAT_VERSION) {
            return Result.Failure("This backup was made by a newer version of Clock")
        }

        root.getAsJsonObject(KEY_PREFERENCES)?.let { prefs ->
            prefs.getAsJsonObject(PREFS_DEFAULT)?.let { importPrefs(defaultPrefs(context), it) }
            prefs.getAsJsonObject(PREFS_WORLD_CLOCK)
                ?.let { importPrefs(namedPrefs(context, PREFS_WORLD_CLOCK), it) }
            prefs.getAsJsonObject(PREFS_WIDGETS)
                ?.let { importPrefs(namedPrefs(context, PREFS_WIDGETS), it) }
        }

        val alarms = root.getAsJsonArray(KEY_ALARMS)?.map {
            gson.fromJson(it, BackedUpAlarm::class.java)
        }.orEmpty()
        val restored = importAlarms(context, alarms)

        return Result.Success(alarmCount = restored)
    }

    private fun importAlarms(context: Context, alarms: List<BackedUpAlarm>): Int {
        val resolver = context.contentResolver

        // Clear existing alarms and every scheduled instance, so a restore is a replacement
        // rather than a merge.
        Alarm.getAlarms(resolver, null).forEach { existing ->
            AlarmStateManager.deleteAllInstances(context, existing.id)
            Alarm.deleteAlarm(resolver, existing.id)
        }
        AlarmInstance.getInstances(resolver, null).forEach { instance ->
            AlarmInstance.deleteInstance(resolver, instance.mId)
        }

        var count = 0
        alarms.forEach { backed ->
            val alarm = Alarm().apply {
                enabled = backed.enabled
                year = backed.year
                month = backed.month
                day = backed.day
                hour = backed.hour
                minutes = backed.minutes
                daysOfWeek = Weekdays(backed.daysOfWeekBits)
                vibrate = backed.vibrate
                flash = backed.flash
                label = backed.label
                alert = backed.alert?.let(Uri::parse)
                deleteAfterUse = backed.deleteAfterUse
                autoSilenceDuration = backed.autoSilenceDuration
                snoozeDuration = backed.snoozeDuration
                missedAlarmRepeatLimit = backed.missedAlarmRepeatLimit
                crescendoDuration = backed.crescendoDuration
                alarmVolume = backed.alarmVolume
            }
            val inserted = Alarm.addAlarm(resolver, alarm)
            if (inserted.enabled) {
                // Re-arm so a restored alarm actually rings without the user opening it.
                runCatching {
                    val instance = inserted.createInstanceAfter(Calendar.getInstance())
                    AlarmInstance.addInstance(resolver, instance)
                    AlarmStateManager.registerInstance(context, instance, true)
                }.onFailure { Log.w(TAG, "Could not schedule restored alarm ${inserted.id}", it) }
            }
            count++
        }
        return count
    }

    private fun importPrefs(prefs: SharedPreferences, json: JsonObject) {
        prefs.edit().apply {
            clear()
            json.entrySet().forEach { (key, element) ->
                val entry = element as? JsonObject ?: return@forEach
                val value = entry.get("v") ?: return@forEach
                when (entry.get("t")?.asString) {
                    "b" -> putBoolean(key, value.asBoolean)
                    "i" -> putInt(key, value.asInt)
                    "l" -> putLong(key, value.asLong)
                    "f" -> putFloat(key, value.asFloat)
                    "s" -> putString(key, (value as? JsonPrimitive)?.asString)
                    "ss" -> putStringSet(
                        key,
                        value.asJsonArray.map { it.asString }.toSet()
                    )
                }
            }
        }.commit()
    }

    // ----------------------------------------------------------------- infra

    private fun defaultPrefs(context: Context): SharedPreferences =
        ClockApplication.getDefaultSharedPreferences(context)

    private fun namedPrefs(context: Context, name: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** Everything about an alarm that is worth carrying between installs; ids are not. */
    private data class BackedUpAlarm(
        val enabled: Boolean,
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minutes: Int,
        val daysOfWeekBits: Int,
        val vibrate: Boolean,
        val flash: Boolean,
        val label: String?,
        val alert: String?,
        val deleteAfterUse: Boolean,
        val autoSilenceDuration: Int,
        val snoozeDuration: Int,
        val missedAlarmRepeatLimit: Int,
        val crescendoDuration: Int,
        val alarmVolume: Int
    )

    sealed interface Result {
        data class Success(val alarmCount: Int) : Result
        data class Failure(val message: String) : Result
    }
}
