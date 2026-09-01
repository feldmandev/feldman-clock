package com.feldman.clock.core.alarm

import android.content.Context
import androidx.core.content.edit

/**
 * A task the user must complete before an alarm will dismiss, so it cannot be swatted away
 * half-asleep. Snoozing is deliberately left ungated.
 */
enum class AlarmChallenge(val id: String, val label: String) {
    NONE("none", "None"),
    MATH("math", "Solve a sum"),
    SHAKE("shake", "Shake the phone"),
    TYPE("type", "Type a phrase");

    companion object {
        fun fromId(id: String?): AlarmChallenge =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * When the challenge also applies to snoozing.
 *
 * Snoozing is unlimited in this app, so leaving it ungated lets a half-asleep user tap Snooze
 * forever and never face the challenge — which defeats the point of setting one. Rather than
 * pick a policy for everyone, the alarm decides.
 *
 * @param freeSnoozes how many snoozes are allowed before the challenge is required
 */
enum class AlarmSnoozePolicy(val id: String, val label: String, val freeSnoozes: Int) {
    NEVER("never", "Never", Int.MAX_VALUE),
    AFTER_THREE("after3", "After 3 snoozes", 3),
    AFTER_ONE("after1", "After 1 snooze", 1),
    ALWAYS("always", "Always", 0);

    companion object {
        val DEFAULT = AFTER_THREE

        fun fromId(id: String?): AlarmSnoozePolicy =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Per-alarm challenge storage.
 *
 * Kept in preferences rather than as an alarms-table column on purpose. Alarm and AlarmInstance
 * read their columns through hand-maintained positional cursor indices shared across two
 * tables, so inserting a column shifts every index after it — a lot of blast radius for a
 * setting nothing in the scheduling path needs. Preferences are also already covered by both
 * the Android backup agent and ClockBackup, so the value still survives a transfer.
 */
object AlarmChallengePrefs {

    private const val FILE = "alarm_challenge_prefs"
    private const val KEY_PREFIX = "challenge_"
    private const val KEY_SNOOZE_POLICY_PREFIX = "snooze_policy_"
    private const val KEY_SNOOZE_COUNT_PREFIX = "snooze_count_"

    fun get(context: Context, alarmId: Long): AlarmChallenge =
        AlarmChallenge.fromId(prefs(context).getString(KEY_PREFIX + alarmId, null))

    fun set(context: Context, alarmId: Long, challenge: AlarmChallenge) {
        prefs(context).edit {
            if (challenge == AlarmChallenge.NONE) {
                remove(KEY_PREFIX + alarmId)
            } else {
                putString(KEY_PREFIX + alarmId, challenge.id)
            }
        }
    }

    fun getSnoozePolicy(context: Context, alarmId: Long): AlarmSnoozePolicy =
        AlarmSnoozePolicy.fromId(prefs(context).getString(KEY_SNOOZE_POLICY_PREFIX + alarmId, null))

    fun setSnoozePolicy(context: Context, alarmId: Long, policy: AlarmSnoozePolicy) {
        prefs(context).edit { putString(KEY_SNOOZE_POLICY_PREFIX + alarmId, policy.id) }
    }

    /**
     * Snoozes so far for one firing, counted per *instance* rather than per alarm so the tally
     * naturally covers a single occurrence and resets when the next one is created.
     */
    fun snoozeCount(context: Context, instanceId: Long): Int =
        prefs(context).getInt(KEY_SNOOZE_COUNT_PREFIX + instanceId, 0)

    fun recordSnooze(context: Context, instanceId: Long) {
        val next = snoozeCount(context, instanceId) + 1
        prefs(context).edit { putInt(KEY_SNOOZE_COUNT_PREFIX + instanceId, next) }
    }

    fun clearSnoozeCount(context: Context, instanceId: Long) {
        prefs(context).edit { remove(KEY_SNOOZE_COUNT_PREFIX + instanceId) }
    }

    /** Called when an alarm is deleted so its entries do not outlive it. */
    fun clear(context: Context, alarmId: Long) {
        prefs(context).edit {
            remove(KEY_PREFIX + alarmId)
            remove(KEY_SNOOZE_POLICY_PREFIX + alarmId)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
