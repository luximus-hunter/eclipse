package eu.tlipman.eclipse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsPage(
    state: ScreenState,
    onPermission: () -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onMergeRestore: () -> Unit,
    onDiagnostics: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.busy) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        state.lastResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        SectionCard(title = "Shizuku access") {
            InfoRow(
                icon = Icons.Outlined.Security,
                title = "Connection",
                value = when {
                    !state.shizukuInstalled -> "Not installed"
                    !state.binderConnected -> "Not running"
                    state.authorised -> "Connected"
                    else -> "Required"
                },
                positive = state.authorised
            )
            Text(
                "Required to change protected Android settings. Keep Shizuku running and grant Eclipse access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onPermission,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Text(if (state.authorised) "  Check access" else "  Grant access")
            }
        }

        SectionCard(title = "Apply / verify") {
            Text(
                "Check and reapply enabled protection or schedule settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onApply,
                enabled = state.authorised && !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text("  Apply / verify now")
            }
            if (!state.busy) {
                Text(
                    state.lastResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = "Diagnostics") {
            Text(
                "View Shizuku and Android setting details for troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onDiagnostics,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text("  View diagnostics")
            }
        }

        SectionCard(title = "Restore previous settings") {
            Text(
                "Restores saved values only if they still match Eclipse's changes. Other changes are left untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onRestore,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null)
                Text("  Restore safely")
            }
            if (state.configConflictDetected && !state.overrideEnabled) {
                OutlinedButton(
                    onClick = onMergeRestore,
                    enabled = state.authorised && !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore only Eclipse's key")
                }
            }
        }
    }
}
