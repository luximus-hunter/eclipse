package eu.tlipman.eclipse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun StatusPage(
    state: ScreenState,
    onOverrideChange: (Boolean) -> Unit,
    onMergeRestore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.ic_lucide_eclipse),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "Battery Saver protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Let Android's Light/Dark schedule stay in control when Battery Saver is on.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Switch(
                        checked = state.overrideEnabled,
                        onCheckedChange = onOverrideChange,
                        enabled = state.authorised && !state.busy
                    )
                }
            }
        }

        SectionCard(title = "System status") {
            InfoRow(
                icon = Icons.Outlined.Security,
                title = "Shizuku",
                value = when {
                    !state.shizukuInstalled -> "Not installed"
                    !state.binderConnected -> "Not running"
                    state.authorised -> "Connected"
                    else -> "Required"
                },
                positive = state.authorised
            )
            RowDivider()
            InfoRow(
                icon = Icons.Outlined.BatterySaver,
                title = "Battery Saver",
                value = if (state.batterySaver) "On" else "Off"
            )
            RowDivider()
            InfoRow(
                icon = Icons.Outlined.BrightnessMedium,
                title = "Appearance",
                value = if (state.darkTheme) "Dark" else "Light"
            )
            RowDivider()
            InfoRow(
                icon = Icons.Outlined.CheckCircle,
                title = "Protection",
                value = when (state.overrideStatus) {
                    "Installed and verified" -> "Verified"
                    "Could not read setting" -> "Read failed"
                    else -> state.overrideStatus
                },
                positive = when (state.overrideStatus) {
                    "Installed and verified" -> true
                    "Could not read setting" -> false
                    else -> null
                }
            )
            RowDivider()
            InfoRow(
                icon = Icons.Outlined.Schedule,
                title = "Android schedule",
                value = compactScheduleStatus(state.scheduleStatus),
                positive = if (
                    state.scheduleStatus.startsWith("Custom schedule") ||
                    state.scheduleStatus == "Sunset to sunrise"
                ) true else null
            )
            if (state.configConflictDetected && !state.overrideEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onMergeRestore,
                        enabled = state.authorised && !state.busy
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null)
                        Text("  Restore Eclipse's key")
                    }
                }
            }
        }
    }
}
