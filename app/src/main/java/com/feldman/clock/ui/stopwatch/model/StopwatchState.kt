package com.feldman.clock.ui.stopwatch.model

import com.feldman.clock.core.data.Stopwatch
import com.feldman.clock.core.data.stopwatch.Lap

data class StopwatchState(
    val totalTimeInMillis: Long = 0L,
    val status: Stopwatch.State = Stopwatch.State.RESET,
    val laps: List<Lap> = emptyList()
)
