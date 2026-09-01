package com.feldman.clock.alarm

object AlarmConstants {
    // This extra is used when receiving an intent to create an alarm, but no alarm details
    // have been passed in, so the alarm page should start the process of creating a new alarm.
    const val ALARM_CREATE_NEW_INTENT_EXTRA: String = "deskclock.create.new"

    // This extra is used when receiving an intent to scroll to specific alarm. If alarm
    // can not be found, and toast message will pop up that the alarm has be deleted.
    const val SCROLL_TO_ALARM_INTENT_EXTRA: String = "deskclock.scroll.to.alarm"
}
