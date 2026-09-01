package com.feldman.clock.ui.timer

import com.feldman.clock.R

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.TimerListener
import com.feldman.clock.ui.timer.model.Timer
import com.feldman.clock.ui.timer.model.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val _timers = MutableStateFlow<List<Timer>>(emptyList())
    val timers: StateFlow<List<Timer>> = _timers.asStateFlow()

    private val timerListener = object : TimerListener {
        override fun timerAdded(timer: com.feldman.clock.core.data.Timer) {
            updateTimers()
        }

        override fun timerUpdated(before: com.feldman.clock.core.data.Timer, after: com.feldman.clock.core.data.Timer) {
            updateTimers()
        }

        override fun timerRemoved(timer: com.feldman.clock.core.data.Timer) {
            updateTimers()
        }
    }

    init {
        DataModel.dataModel.addTimerListener(timerListener)
        updateTimers()
        startTicker()
    }

    private fun updateTimers() {
        val legacyTimers = DataModel.dataModel.timers
        _timers.value = legacyTimers.map { it.toUiModel() }.sortedForDisplay()
    }

    private fun com.feldman.clock.core.data.Timer.toUiModel(): Timer {
        val uiState = when (this.state) {
            com.feldman.clock.core.data.Timer.State.RUNNING -> TimerState.RUNNING
            com.feldman.clock.core.data.Timer.State.PAUSED -> TimerState.PAUSED
            com.feldman.clock.core.data.Timer.State.EXPIRED -> TimerState.EXPIRED
            com.feldman.clock.core.data.Timer.State.RESET -> TimerState.RESET
            com.feldman.clock.core.data.Timer.State.MISSED -> TimerState.EXPIRED
            else -> TimerState.RESET
        }

        return Timer(
            id = this.id.toLong(),
            totalLengthInMillis = this.length,
            remainingTimeInMillis = this.remainingTime, // This gets calculated dynamically in getRemainingTime() for running timers
            label = this.label ?: "",
            state = uiState
        )
    }
    
    // Ticker is only for updating the UI display of remaining time for running timers
    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                // We need to re-map regularly because 'remainingTime' calculation depends on wall clock for running timers
                updateTimers()
                delay(20) // 50fps update rate for smooth countdowns
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        DataModel.dataModel.removeTimerListener(timerListener)
    }

    fun toggleTimer(id: Long) {
        val legacyTimer = DataModel.dataModel.getTimer(id.toInt()) ?: return
        
        when (legacyTimer.state) {
            com.feldman.clock.core.data.Timer.State.RUNNING -> DataModel.dataModel.pauseTimer(legacyTimer)
            com.feldman.clock.core.data.Timer.State.PAUSED -> DataModel.dataModel.startTimer(legacyTimer)
            com.feldman.clock.core.data.Timer.State.RESET -> DataModel.dataModel.startTimer(legacyTimer)
            com.feldman.clock.core.data.Timer.State.EXPIRED, com.feldman.clock.core.data.Timer.State.MISSED -> {
                // If expired, we usually reset it.
                DataModel.dataModel.resetOrDeleteTimer(legacyTimer, R.string.app_label)
            }
        }
    }

    fun deleteTimer(id: Long) {
        val legacyTimer = DataModel.dataModel.getTimer(id.toInt()) ?: return
        DataModel.dataModel.removeTimer(legacyTimer)
    }

    fun setTimerLabel(id: Long, label: String) {
        val legacyTimer = DataModel.dataModel.getTimer(id.toInt()) ?: return
        DataModel.dataModel.setTimerLabel(legacyTimer, label)
    }

    fun resetTimer(id: Long) {
        val legacyTimer = DataModel.dataModel.getTimer(id.toInt()) ?: return
        DataModel.dataModel.resetOrDeleteTimer(legacyTimer, 0)
    }

    fun addTimer(millis: Long, label: String = "") {
        DataModel.dataModel.addTimer(millis, label, "60", false)
    }


    fun addOneMinute(id: Long) {
        val legacyTimer = DataModel.dataModel.getTimer(id.toInt()) ?: return
        
        // Ensure button time is set to 60 seconds (1 minute) before adding custom time
        // This ensures consistent "+1 minute" behavior regardless of previous settings
        DataModel.dataModel.setTimerButtonTime(legacyTimer, "60")
        
        // Fetch it again to be safe ensuring we have the updated model state if immutable/copied
        val refreshedTimer = DataModel.dataModel.getTimer(id.toInt())
        if (refreshedTimer != null) {
            DataModel.dataModel.addCustomTimeToTimer(refreshedTimer)
        }
    }
    
    private fun Timer.sortPriority(): Int =
        when (state) {
            TimerState.RUNNING -> 0
            TimerState.PAUSED -> 1
            TimerState.RESET -> 2
            TimerState.EXPIRED -> 3
        }

    private fun List<Timer>.sortedForDisplay(): List<Timer> =
        this.sortedWith(
            compareBy<Timer> { it.sortPriority() }
                .thenBy {
                    when (it.state) {
                        TimerState.RESET -> it.totalLengthInMillis
                        else -> it.remainingTimeInMillis
                    }
                }
                .thenBy { it.id }
        )
}
