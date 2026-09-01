package com.feldman.clock.ui.timer.model

import com.feldman.clock.R

data class Timer(
    val id: Long,
    val totalLengthInMillis: Long,
    val remainingTimeInMillis: Long,
    val label: String = "",
    val state: TimerState = TimerState.RESET
)

enum class TimerState {
    RESET, RUNNING, PAUSED, EXPIRED
}
