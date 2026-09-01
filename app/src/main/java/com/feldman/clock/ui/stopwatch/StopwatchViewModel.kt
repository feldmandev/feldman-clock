package com.feldman.clock.ui.stopwatch

import com.feldman.clock.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.stopwatch.Lap
import com.feldman.clock.core.data.Stopwatch
import com.feldman.clock.core.data.StopwatchListener
import com.feldman.clock.ui.stopwatch.model.StopwatchState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StopwatchViewModel : ViewModel() {
    private val dataModel = DataModel.dataModel
    
    // We keep track of the latest stopwatch snapshot to calculate time
    private var currentStopwatch: Stopwatch = dataModel.stopwatch
        set(value) {
            field = value
            updateState()
            manageTicker()
        }

    private val _uiState = MutableStateFlow(StopwatchState(
        totalTimeInMillis = currentStopwatch.totalTime,
        status = currentStopwatch.state,
        laps = dataModel.laps
    ))
    val uiState: StateFlow<StopwatchState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    
    private val listener = object : StopwatchListener {
        override fun stopwatchUpdated(after: Stopwatch) {
            currentStopwatch = after
            // Laps are cleared on reset
            if (after.isReset) {
                 _uiState.value = _uiState.value.copy(laps = emptyList())
            }
        }

        override fun lapAdded(lap: Lap) {
            val currentLaps = dataModel.laps // Get fresh list from DataModel
            _uiState.value = _uiState.value.copy(laps = currentLaps)
        }
    }

    init {
        dataModel.addStopwatchListener(listener)
        // sync initial state
        currentStopwatch = dataModel.stopwatch
        _uiState.value = _uiState.value.copy(laps = dataModel.laps)
        manageTicker()
    }
    
    private fun updateState() {
        _uiState.value = _uiState.value.copy(
            totalTimeInMillis = currentStopwatch.totalTime,
            status = currentStopwatch.state
        )
    }

    private fun manageTicker() {
        if (currentStopwatch.isRunning) {
            if (tickerJob?.isActive != true) {
                tickerJob = viewModelScope.launch {
                    while (isActive) {
                        _uiState.value = _uiState.value.copy(
                            totalTimeInMillis = currentStopwatch.totalTime
                        )
                        delay(30) // ~30 fps
                    }
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
            // Final update to ensure exact value
             _uiState.value = _uiState.value.copy(
                totalTimeInMillis = currentStopwatch.totalTime
            )
        }
    }

    fun toggleStartPause() {
        if (currentStopwatch.isRunning) {
            dataModel.pauseStopwatch()
        } else {
            val wasIdle = currentStopwatch.totalTime == 0L
            dataModel.startStopwatch()

            if (wasIdle && dataModel.laps.isEmpty()) {
                dataModel.addLap()
            }
        }
    }

    fun reset() {
        dataModel.resetStopwatch()
    }

    fun lap() {
        if (currentStopwatch.isRunning) {
            dataModel.addLap()
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataModel.removeStopwatchListener(listener)
        tickerJob?.cancel()
    }
}
