package eu.tlipman.eclipse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime

@Composable
internal fun SchedulePage(
    state: ScreenState,
    darkStart: LocalTime,
    lightStart: LocalTime,
    onScheduleChange: (Boolean) -> Unit,
    onDarkTime: (LocalTime) -> Unit,
    onLightTime: (LocalTime) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Android dark-mode schedule") {
            ScheduleToggleRow(
                title = "Use Android's schedule",
                subtitle = "Use the selected times; changes apply immediately while enabled.",
                checked = state.scheduleEnabled,
                enabled = state.authorised && !state.busy,
                onCheckedChange = onScheduleChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(
                icon = Icons.Outlined.Schedule,
                title = "System mode",
                value = compactScheduleStatus(state.scheduleStatus),
                positive = if (state.scheduleStatus.startsWith("Custom schedule")) true else null
            )
        }

        SectionCard(title = "Schedule times") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton(
                    label = "Dark mode starts",
                    time = darkStart,
                    enabled = !state.busy,
                    onTime = onDarkTime,
                    modifier = Modifier.weight(1f)
                )
                TimeButton(
                    label = "Light mode starts",
                    time = lightStart,
                    enabled = !state.busy,
                    onTime = onLightTime,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "When off, Android's mode is None and time edits are saved for next time. Turning it on applies the selected times.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Android may defer a scheduled appearance change while the screen is in use, applying it after the screen turns off or a screensaver starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(
    label: String,
    time: LocalTime,
    enabled: Boolean,
    onTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.AccessTime, contentDescription = null)
            Text("  ${time.toString().take(5)}")
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
//            title = { Text(label) },
            text = {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TimePicker(
                            state = pickerState,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTime(LocalTime.of(pickerState.hour, pickerState.minute))
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
