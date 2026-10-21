package eu.tlipman.eclipse

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import eu.tlipman.eclipse.shizuku.ShizukuShell
import eu.tlipman.eclipse.system.BatterySaverConfig
import eu.tlipman.eclipse.system.ManagerResult
import eu.tlipman.eclipse.system.EclipseManager
import eu.tlipman.eclipse.system.EclipseStore
import eu.tlipman.eclipse.system.ScheduleTimes
import eu.tlipman.eclipse.ui.theme.EclipseTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.time.LocalTime

private const val SHIZUKU_PERMISSION_REQUEST = 1024

class MainActivity : ComponentActivity() {
    private lateinit var store: EclipseStore
    private lateinit var manager: EclipseManager
    private var screen by mutableStateOf(ScreenState())
    private var darkStart by mutableStateOf(LocalTime.of(19, 30))
    private var lightStart by mutableStateOf(LocalTime.of(6, 30))
    private var dialog by mutableStateOf<String?>(null)
    private var receiverRegistered = false

    private val binderListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
        refresh()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        screen = screen.copy(
            binderConnected = false,
            authorised = false,
            uid = null,
            overrideStatus = "Shizuku disconnected"
        )
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
            screen = screen.copy(lastResult = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                "Shizuku permission granted."
            } else {
                "Shizuku permission was denied."
            })
            refresh()
        }
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = EclipseStore(this)
        manager = EclipseManager(this, ShizukuShell(), store)
        darkStart = store.darkStart
        lightStart = store.lightStart
        manager.customScheduleTimes()?.let { times ->
            darkStart = times.darkStart
            lightStart = times.lightStart
            store.darkStart = times.darkStart
            store.lightStart = times.lightStart
        }
        screen = screen.copy(
            overrideEnabled = store.overrideEnabled,
            scheduleEnabled = store.scheduleEnabled
        )
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
        setContent {
            EclipseTheme {
                EclipseScreen(
                    state = screen,
                    darkStart = darkStart,
                    lightStart = lightStart,
                    onOverrideChange = ::setOverrideEnabled,
                    onScheduleChange = ::setScheduleEnabled,
                    onDarkTime = ::changeDarkStart,
                    onLightTime = ::changeLightStart,
                    onApply = ::applyNow,
                    onRestore = { dialog = "restore" },
                    onMergeRestore = { dialog = "merge_restore" },
                    onPermission = ::requestShizukuPermission,
                    onDiagnostics = { showDiagnostics() }
                )
                when (dialog) {
                    "restore" -> AlertDialog(
                        onDismissRequest = { dialog = null },
                        title = { Text("Restore previous settings?") },
                        text = {
                            Text("Eclipse will restore only settings that still match the values it applied. If another app or Android changed them, those settings will be left untouched.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                dialog = null
                                restorePrevious()
                            }) { Text("Restore safely") }
                        },
                        dismissButton = {
                            TextButton(onClick = { dialog = null }) { Text("Cancel") }
                        }
                    )
                    "diagnostics" -> AlertDialog(
                        onDismissRequest = { dialog = null },
                        title = { Text("Diagnostics") },
                        text = { Text(screen.diagnostics) },
                        confirmButton = {
                            TextButton(onClick = { dialog = null }) { Text("Done") }
                        }
                    )
                    "merge_restore" -> AlertDialog(
                        onDismissRequest = { dialog = null },
                        title = { Text("Restore Eclipse's key only?") },
                        text = {
                            Text("This restores only enable_night_mode to its saved value and preserves every other current Battery Saver entry. Malformed settings will be left unchanged.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                dialog = null
                                restoreOverrideKeyOnly()
                            }) { Text("Restore key only") }
                        },
                        dismissButton = {
                            TextButton(onClick = { dialog = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }
        receiverRegistered = true
        refresh()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDarkTheme = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (screen.darkTheme != isDarkTheme) {
            refresh()
        }
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(powerReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun refreshShizukuState() {
        val installed = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    "moe.shizuku.privileged.api",
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val connected = Shizuku.pingBinder()
        val authorised = if (connected) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: IllegalStateException) {
                false
            }
        } else false
        val uid = if (connected) {
            try {
                Shizuku.getUid()
            } catch (_: IllegalStateException) {
                null
            }
        } else null
        screen = screen.copy(
            shizukuInstalled = installed,
            binderConnected = connected,
            authorised = authorised,
            uid = uid
        )
    }

    private fun refresh() {
        refreshShizukuState()
        val powerManager = getSystemService(PowerManager::class.java)
        screen = screen.copy(
            batterySaver = powerManager?.isPowerSaveMode == true,
            darkTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES,
            overrideEnabled = store.overrideEnabled,
            scheduleEnabled = store.scheduleEnabled,
            configConflictDetected = store.configConflictDetected,
            scheduleStatus = manager.scheduleStatus()
        )
        if (!screen.binderConnected || !screen.authorised) {
            screen = screen.copy(
                overrideStatus = if (!screen.shizukuInstalled) "Shizuku is not installed"
                else if (!screen.binderConnected) "Shizuku is not running"
                else "Permission required",
                diagnostics = "System settings are unavailable without authorised Shizuku access."
            )
            return
        }
        lifecycleScope.launch {
            try {
                val config = manager.diagnosticConfig()
                val installed = BatterySaverConfig.hasNightModeDisabled(config)
                screen = screen.copy(
                    overrideStatus = if (installed) "Installed and verified" else "Not installed",
                    diagnostics = "battery_saver_constants: ${config ?: "(absent)"}"
                )
                if (store.overrideEnabled) {
                    val result = manager.maintainOverride(config)
                    if (result.message != "Battery Saver override is present.") {
                        setResult(result)
                    }
                }
            } catch (e: Exception) {
                screen = screen.copy(
                    overrideStatus = "Could not read setting",
                    diagnostics = "Read failed: ${e.message}"
                )
            }
        }
    }

    private fun setOverrideEnabled(enabled: Boolean) {
        if (enabled && !canUseShizuku()) return
        screen = screen.copy(
            busy = true,
            lastResult = if (enabled) "Installing and verifying Battery Saver protection…"
            else "Checking and restoring the saved Battery Saver setting…"
        )
        lifecycleScope.launch {
            val result = if (enabled) manager.enableOverride() else manager.restoreOverride()
            setResult(result)
            refresh()
        }
    }

    private fun setScheduleEnabled(enabled: Boolean) {
        if (enabled) {
            if (!canUseShizuku()) return
            store.scheduleEnabled = true
            screen = screen.copy(scheduleEnabled = true)
            applySchedule(rollbackEnableOnFailure = true)
        } else {
            val wasEnabled = store.scheduleEnabled
            store.scheduleEnabled = false
            screen = screen.copy(
                busy = true,
                scheduleEnabled = false,
                lastResult = "Turning off Android's native dark-mode schedule…"
            )
            lifecycleScope.launch {
                val result = manager.disableSchedule()
                if (!result.success) store.scheduleEnabled = wasEnabled
                setResult(result)
                refresh()
            }
        }
    }

    private fun changeDarkStart(time: LocalTime) {
        darkStart = time
        store.darkStart = time
        if (store.scheduleEnabled) applySchedule()
    }

    private fun changeLightStart(time: LocalTime) {
        lightStart = time
        store.lightStart = time
        if (store.scheduleEnabled) applySchedule()
    }

    private fun applySchedule(rollbackEnableOnFailure: Boolean = false) {
        if (!canUseShizuku()) {
            if (rollbackEnableOnFailure) {
                store.scheduleEnabled = false
                screen = screen.copy(scheduleEnabled = false)
            }
            return
        }
        screen = screen.copy(busy = true, lastResult = "Applying and verifying Android's native schedule…")
        lifecycleScope.launch {
            val result = manager.applySchedule(ScheduleTimes(darkStart, lightStart))
            if (rollbackEnableOnFailure && !result.success) store.scheduleEnabled = false
            setResult(result)
            refresh()
        }
    }

    private fun applyNow() {
        if (!canUseShizuku()) return
        if (!store.overrideEnabled && !store.scheduleEnabled) {
            screen = screen.copy(lastResult = "Enable Battery Saver protection or the native schedule first.")
            return
        }
        screen = screen.copy(busy = true, lastResult = "Checking Eclipse settings…")
        lifecycleScope.launch {
            val results = mutableListOf<ManagerResult>()
            if (store.overrideEnabled) results += manager.maintainOverride()
            if (store.scheduleEnabled) results += manager.applySchedule(ScheduleTimes(darkStart, lightStart))
            val failed = results.firstOrNull { !it.success }
            val result = failed ?: ManagerResult(
                message = results.joinToString(" ").ifBlank {
                    "Enable Battery Saver protection or the native schedule first."
                },
                success = results.isNotEmpty()
            )
            setResult(result)
            refresh()
        }
    }

    private fun restorePrevious() {
        if (!canUseShizuku()) return
        if (!store.hasConfigBackup && !store.hasScheduleBackup) {
            screen = screen.copy(lastResult = "No saved settings are available to restore.")
            return
        }
        screen = screen.copy(busy = true, lastResult = "Checking saved settings before restoration…")
        lifecycleScope.launch {
            val results = mutableListOf<ManagerResult>()
            if (store.hasConfigBackup) results += manager.restoreOverride()
            if (store.hasScheduleBackup) results += manager.restoreSchedule()
            screen = screen.copy(
                lastResult = if (results.isEmpty()) "No saved settings are available to restore."
                else results.joinToString(" ") { it.message }
            )
            refresh()
        }
    }

    private fun restoreOverrideKeyOnly() {
        if (!canUseShizuku()) return
        screen = screen.copy(
            busy = true,
            lastResult = "Restoring only Eclipse's key while preserving other entries…"
        )
        lifecycleScope.launch {
            setResult(manager.restoreOverrideKeyOnly())
            refresh()
        }
    }

    private fun showDiagnostics() {
        refreshShizukuState()
        lifecycleScope.launch {
            val config = if (screen.authorised) {
                runCatching { manager.diagnosticConfig() }.getOrElse { "Read failed: ${it.message}" }
            } else "Unavailable without authorised Shizuku."
            screen = screen.copy(
                diagnostics = buildString {
                    appendLine("Shizuku installed: ${screen.shizukuInstalled}")
                    appendLine("Binder connected: ${screen.binderConnected}")
                    appendLine("Permission: ${screen.authorised}")
                    appendLine("Shizuku UID: ${screen.uid ?: "unavailable"}")
                    appendLine("Battery Saver: ${screen.batterySaver}")
                    appendLine("System theme: ${if (screen.darkTheme) "Dark" else "Light"}")
                    appendLine("Night mode: ${manager.currentModeName()}")
                    appendLine("battery_saver_constants: ${config ?: "(absent)"}")
                    append("Last result: ${screen.lastResult}")
                }
            )
            dialog = "diagnostics"
        }
    }

    private fun requestShizukuPermission() {
        refreshShizukuState()
        if (!screen.binderConnected) {
            screen = screen.copy(lastResult = "Start Shizuku, then return to Eclipse.")
            return
        }
        if (screen.authorised) {
            screen = screen.copy(lastResult = "Shizuku is already authorised.")
            return
        }
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
        } catch (e: IllegalStateException) {
            screen = screen.copy(lastResult = "Could not request Shizuku permission: ${e.message}")
        }
    }

    private fun canUseShizuku(): Boolean {
        refreshShizukuState()
        if (screen.binderConnected && screen.authorised) return true
        screen = screen.copy(
            lastResult = if (!screen.binderConnected) "Connect to Shizuku first."
            else "Grant Eclipse Shizuku permission first."
        )
        return false
    }

    private fun setResult(result: ManagerResult) {
        screen = screen.copy(
            lastResult = result.message,
            busy = false,
            overrideEnabled = store.overrideEnabled,
            scheduleEnabled = store.scheduleEnabled,
            configConflictDetected = store.configConflictDetected
        )
    }
}

@Composable
private fun LegacyEclipseScreen(
    state: ScreenState,
    darkStart: LocalTime,
    lightStart: LocalTime,
    onOverrideChange: (Boolean) -> Unit,
    onScheduleChange: (Boolean) -> Unit,
    onDarkTime: (LocalTime) -> Unit,
    onLightTime: (LocalTime) -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onMergeRestore: () -> Unit,
    onPermission: () -> Unit,
    onDiagnostics: () -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text("Eclipse", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Keep Battery Saver from overriding your Light/Dark schedule.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "System status",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Device and schedule overview",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val badgeColor = if (state.authorised) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeColor
                        ) {
                            Text(
                                if (state.authorised) "CONNECTED" else "SETUP",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.authorised) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatusLine(
                        "Shizuku",
                        when {
                            !state.shizukuInstalled -> "Not installed"
                            !state.binderConnected -> "Not running"
                            state.authorised -> "Connected · UID ${state.uid ?: "?"}"
                            else -> "Permission required"
                        },
                        positive = state.authorised
                    )
                    StatusLine("Battery Saver", if (state.batterySaver) "On" else "Off")
                    StatusLine("Current theme", if (state.darkTheme) "Dark" else "Light")
                    StatusLine(
                        "Override",
                        state.overrideStatus,
                        positive = when (state.overrideStatus) {
                            "Installed and verified" -> true
                            "Could not read setting" -> false
                            else -> null
                        }
                    )
                    StatusLine(
                        "Native schedule",
                        state.scheduleStatus,
                        positive = state.scheduleStatus.startsWith("Custom schedule")
                    )
                    if (state.configConflictDetected && !state.overrideEnabled) {
                        OutlinedButton(
                            onClick = onMergeRestore,
                            enabled = state.authorised && !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restore only Eclipse's key") }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleRow(
                        title = "Battery Saver protection",
                        subtitle = "Keep enable_night_mode=false while enabled. Disabling restores the saved value only if it has not changed.",
                        checked = state.overrideEnabled,
                        enabled = state.authorised && !state.busy,
                        onCheckedChange = onOverrideChange
                    )
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow(
                        title = "Android native schedule",
                        subtitle = "Uses Android's built-in Light/Dark schedule.",
                        checked = state.scheduleEnabled,
                        enabled = state.authorised && !state.busy,
                        onCheckedChange = onScheduleChange
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeButton("Dark starts", darkStart, context, onDarkTime, Modifier.weight(1f))
                        TimeButton("Light starts", lightStart, context, onLightTime, Modifier.weight(1f))
                    }
                    Text(
                        "Cross-midnight schedules are supported. Android may defer a theme change while the screen is in use, applying it after the screen turns off or a screensaver starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(state.lastResult, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onPermission, modifier = Modifier.weight(1f)) {
                    Text(if (state.authorised) "Shizuku ready" else "Connect Shizuku")
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Text("Restore")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDiagnostics, modifier = Modifier.weight(1f)) { Text("Diagnostics") }
                TextButton(onClick = onAbout, modifier = Modifier.weight(1f)) { Text("About") }
            }
            Text(
                "Recovery runs while Eclipse is active. Android may stop background work; no always-on service is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onApply,
                enabled = state.authorised && !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply now") }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun StatusLine(label: String, value: String, positive: Boolean? = null) {
    val indicatorColor = when (positive) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .padding(end = 10.dp)
                .size(8.dp)
                .background(indicatorColor, CircleShape)
        )
        Text(label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            fontWeight = if (positive == true) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeButton(
    label: String,
    time: LocalTime,
    context: Context,
    onTime: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTime(LocalTime.of(hour, minute)) },
                    time.hour,
                    time.minute,
                    true
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(time.toString().substring(0, 5))
        }
    }
}
