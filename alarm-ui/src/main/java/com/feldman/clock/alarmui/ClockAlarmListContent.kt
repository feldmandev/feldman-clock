package com.feldman.clock.alarmui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feldman.motion.ItemPosition
import com.feldman.motion.MotionButton
import com.feldman.motion.MotionCard
import com.feldman.motion.MotionSwitch
import com.feldman.motion.rememberSymbolPainter
import kotlinx.coroutines.launch
import java.util.Calendar

object ClockAlarmBridgeContract {
    const val METHOD_LIST = "list"
    const val METHOD_CREATE = "create"
    const val METHOD_UPDATE_TIME = "update_time"
    const val METHOD_SET_ENABLED = "set_enabled"
    const val METHOD_DELETE = "delete"
    const val KEY_ALARMS = "alarms"
    const val KEY_ID = "id"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"
    const val KEY_ENABLED = "enabled"
    const val KEY_LABEL = "label"
    const val KEY_REPEAT_DAYS = "repeat_days"
}

data class ClockAlarmItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val label: String,
    val repeatDays: Int
)

interface ClockAlarmController {
    suspend fun load(): List<ClockAlarmItem>
    suspend fun create(hour: Int, minute: Int)
    suspend fun updateTime(id: Long, hour: Int, minute: Int)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
}

@Composable
fun ClockAlarmListContent(
    controller: ClockAlarmController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    editNextOnOpen: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alarms by remember { mutableStateOf<List<ClockAlarmItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var editorAlarm by remember { mutableStateOf<ClockAlarmItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var initialEditHandled by remember { mutableStateOf(false) }

    LaunchedEffect(controller, refresh) {
        loading = true
        runCatching { controller.load() }
            .onSuccess {
                alarms = it
                error = null
                if (editNextOnOpen && !initialEditHandled) {
                    editorAlarm = it.firstOrNull { alarm -> alarm.enabled }
                    creating = editorAlarm == null
                    initialEditHandled = true
                }
            }
            .onFailure { error = "Feldman Clock is unavailable" }
        loading = false
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            runCatching { action() }
                .onSuccess { refresh++ }
                .onFailure { error = "Could not update the alarm" }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Alarms", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Managed by Feldman Clock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MotionButton(
                icon = "close",
                onClick = onDismiss,
                width = 48.dp,
                height = 48.dp,
                iconSize = 22.dp
            )
        }
        Spacer(Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && alarms.isEmpty() -> Text(
                    error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                alarms.isEmpty() -> Text(
                    "No alarms yet",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                        val position = when {
                            alarms.size == 1 -> ItemPosition.Alone
                            index == 0 -> ItemPosition.Start
                            index == alarms.lastIndex -> ItemPosition.End
                            else -> ItemPosition.Middle
                        }
                        MotionCard(
                            position = position,
                            modifier = Modifier.clickable { editorAlarm = alarm },
                            containerColor = if (alarm.enabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = rememberSymbolPainter("alarm"),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        formatAlarmTime(context, alarm.hour, alarm.minute),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        alarm.label.ifBlank { repeatLabel(alarm.repeatDays) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                MotionSwitch(
                                    checked = alarm.enabled,
                                    onCheckedChange = { enabled ->
                                        alarms = alarms.map {
                                            if (it.id == alarm.id) it.copy(enabled = enabled) else it
                                        }
                                        runAction { controller.setEnabled(alarm.id, enabled) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        MotionButton(
            text = "New alarm",
            icon = "add_alarm",
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth(),
            height = 56.dp
        )
    }

    val editing = editorAlarm
    if (creating || editing != null) {
        val now = remember { Calendar.getInstance() }
        val pickerState = rememberTimePickerState(
            initialHour = editing?.hour ?: now.get(Calendar.HOUR_OF_DAY),
            initialMinute = editing?.minute ?: now.get(Calendar.MINUTE),
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { creating = false; editorAlarm = null },
            title = { Text(if (editing == null) "New alarm" else "Edit alarm time") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    if (editing == null) {
                        runAction { controller.create(pickerState.hour, pickerState.minute) }
                    } else {
                        runAction { controller.updateTime(editing.id, pickerState.hour, pickerState.minute) }
                    }
                    creating = false
                    editorAlarm = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (editing != null) {
                        TextButton(onClick = {
                            runAction { controller.delete(editing.id) }
                            editorAlarm = null
                        }) { Text("Delete") }
                    }
                    TextButton(onClick = { creating = false; editorAlarm = null }) { Text("Cancel") }
                }
            }
        )
    }
}

private fun formatAlarmTime(context: android.content.Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

private fun repeatLabel(bits: Int): String = when (bits) {
    0 -> "Once"
    0x7F -> "Every day"
    0x1F -> "Weekdays"
    0x60 -> "Weekends"
    else -> "Repeating alarm"
}
