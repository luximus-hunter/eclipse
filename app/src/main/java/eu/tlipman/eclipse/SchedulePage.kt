package eu.tlipman.eclipse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tlipman.eclipse.system.ScheduleMode
import java.time.LocalTime

@Composable
internal fun SchedulePage(
    state: ScreenState,
    scheduleMode: ScheduleMode,
    darkStart: LocalTime,
    lightStart: LocalTime,
    onScheduleChange: (Boolean) -> Unit,
    onScheduleModeChange: (ScheduleMode) -> Unit,
    onDarkTime: (LocalTime) -> Unit,
    onLightTime: (LocalTime) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Android dark-mode schedule") {
            ScheduleToggleRow(
                title = "Use Android's schedule",
                subtitle = "Use the selected schedule; changes apply immediately while enabled.",
                checked = state.scheduleEnabled,
                enabled = state.authorised && !state.busy,
                onCheckedChange = onScheduleChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(
                icon = Icons.Outlined.Schedule,
                title = "System mode",
                value = compactScheduleStatus(state.scheduleStatus),
                positive = if (
                    state.scheduleStatus.startsWith("Custom schedule") ||
                    state.scheduleStatus == "Sunset to sunrise"
                ) true else null
            )
        }

        SectionCard(title = "Schedule type") {
            ScheduleModeOption(
                title = "Sunset to sunrise",
                subtitle = "Let Android switch to dark mode at sunset and back to light mode at sunrise.",
                selected = scheduleMode == ScheduleMode.SUNSET_TO_SUNRISE,
                enabled = !state.busy,
                onSelect = { onScheduleModeChange(ScheduleMode.SUNSET_TO_SUNRISE) }
            )
            ScheduleModeOption(
                title = "Custom times",
                subtitle = "Choose when dark mode starts and light mode returns.",
                selected = scheduleMode == ScheduleMode.CUSTOM_TIMES,
                enabled = !state.busy,
                onSelect = { onScheduleModeChange(ScheduleMode.CUSTOM_TIMES) }
            )
            Text(
                "Changes are saved while the schedule is off and applied when you enable it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Android may defer a scheduled appearance change while the screen is in use, applying it after the screen turns off or a screensaver starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (scheduleMode == ScheduleMode.CUSTOM_TIMES) {
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
            }
        }
    }
}

@Composable
private fun ScheduleModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
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
