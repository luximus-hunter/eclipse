package eu.tlipman.eclipse.system

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import eu.tlipman.eclipse.shizuku.ShellResult
import eu.tlipman.eclipse.shizuku.ShizukuShell
import kotlinx.coroutines.CancellationException
import java.time.LocalTime

data class ManagerResult(val message: String, val success: Boolean)

class EclipseManager(
    private val context: Context,
    private val shell: ShizukuShell,
    private val store: EclipseStore
) {
    suspend fun enableOverride(): ManagerResult {
        return try {
            val original = readBatteryConstants()
            val update = BatterySaverConfig.disableNightMode(original)
            if (update !is ConfigUpdate.Success) {
                return ManagerResult((update as ConfigUpdate.Failure).reason, false)
            }
            if (store.hasConfigBackup && store.lastAppliedConfig != null &&
                original != store.lastAppliedConfig
            ) {
                store.configConflictDetected = true
            }
            if (!store.hasConfigBackup) {
                store.originalConfig = original
                store.markConfigBackupSaved()
            }
            writeBatteryConstants(update.value)
            val verified = readBatteryConstants()
            if (!BatterySaverConfig.hasNightModeDisabled(verified)) {
                error("Battery Saver override did not verify after writing.")
            }
            store.lastAppliedConfig = verified
            store.overrideEnabled = true
            ManagerResult("Battery Saver override is installed and verified.", true)
        } catch (e: Exception) {
            ManagerResult("Could not enable the Battery Saver override: ${e.message}", false)
        }
    }

    suspend fun maintainOverride(): ManagerResult = try {
        maintainOverride(readBatteryConstants())
    } catch (e: Exception) {
        ManagerResult("Could not check the Battery Saver override: ${e.message}", false)
    }

    suspend fun maintainOverride(current: String?): ManagerResult {
        return try {
            val last = store.lastAppliedConfig
            if (last != null && current != last) {
                store.configConflictDetected = true
            }
            if (BatterySaverConfig.hasNightModeDisabled(current)) {
                ManagerResult(
                    if (store.configConflictDetected) {
                        "Override is present; another system actor changed the setting. Restoration will preserve its unrelated entries."
                    } else {
                        "Battery Saver override is present."
                    },
                    true
                )
            } else {
                val update = BatterySaverConfig.disableNightMode(current)
                if (update !is ConfigUpdate.Success) {
                    return ManagerResult((update as ConfigUpdate.Failure).reason, false)
                }
                writeBatteryConstants(update.value)
                val verified = readBatteryConstants()
                if (!BatterySaverConfig.hasNightModeDisabled(verified)) {
                    error("Battery Saver override did not verify after writing.")
                }
                store.lastAppliedConfig = verified
                ManagerResult("Battery Saver override was reapplied and verified.", true)
            }
        } catch (e: Exception) {
            ManagerResult("Could not check the Battery Saver override: ${e.message}", false)
        }
    }

    suspend fun restoreOverride(): ManagerResult {
        if (!store.hasConfigBackup) {
            store.overrideEnabled = false
            return ManagerResult("No Battery Saver backup is available to restore.", false)
        }
        val last = store.lastAppliedConfig
            ?: return ManagerResult("The last Eclipse value is missing; configuration was left unchanged.", false)
        if (store.configConflictDetected) {
            store.overrideEnabled = false
            return ManagerResult(
                "Another actor changed Battery Saver configuration while Eclipse was active. It was left untouched; use “Restore only Eclipse's key” to preserve the other current entries.",
                false
            )
        }
        return try {
            val current = readBatteryConstants()
            val restore = BatterySaverConfig.safeRestore(current, last, store.originalConfig)
            if (restore !is ConfigUpdate.Success) {
                store.configConflictDetected = true
                store.overrideEnabled = false
                return ManagerResult((restore as ConfigUpdate.Failure).reason, false)
            }
            if (store.originalConfig == null) {
                requireSuccess(shell.run("settings delete global battery_saver_constants"))
            } else {
                writeBatteryConstants(restore.value)
            }
            val verified = readBatteryConstants()
            if (verified != store.originalConfig) error("Restored Battery Saver value did not verify.")
            store.overrideEnabled = false
            store.clearConfigBackup()
            ManagerResult("The original Battery Saver configuration was safely restored.", true)
        } catch (e: Exception) {
            ManagerResult("Could not restore Battery Saver settings: ${e.message}", false)
        }
    }

    suspend fun restoreOverrideKeyOnly(): ManagerResult {
        if (!store.hasConfigBackup) {
            return ManagerResult("No Battery Saver backup is available.", false)
        }
        return try {
            val current = readBatteryConstants()
            val restore = BatterySaverConfig.restoreNightModeOnly(current, store.originalConfig)
            if (restore !is ConfigRestore.Success) {
                return ManagerResult((restore as ConfigRestore.Failure).reason, false)
            }
            if (restore.value == null) {
                requireSuccess(shell.run("settings delete global battery_saver_constants"))
            } else {
                writeBatteryConstants(restore.value)
            }
            if (readBatteryConstants() != restore.value) {
                error("Restored Eclipse entry did not verify.")
            }
            store.overrideEnabled = false
            store.clearConfigBackup()
            ManagerResult(
                "Restored Eclipse's Battery Saver entry while preserving all other current entries.",
                true
            )
        } catch (e: Exception) {
            ManagerResult("Could not safely restore Eclipse's entry: ${e.message}", false)
        }
    }

    suspend fun applySchedule(times: ScheduleTimes): ManagerResult {
        times.validate()?.let { return ManagerResult(it, false) }
        return try {
            val manager = uiModeManager()
            if (!store.hasScheduleBackup) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    store.originalMode = manager.nightMode
                    store.originalDarkStart = manager.customNightModeStart
                    store.originalLightStart = manager.customNightModeEnd
                }
                store.markScheduleBackupSaved()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                manager.nightMode == UiModeManager.MODE_NIGHT_CUSTOM &&
                manager.customNightModeStart == times.darkStart &&
                manager.customNightModeEnd == times.lightStart
            ) {
                store.darkStart = times.darkStart
                store.lightStart = times.lightStart
                store.lastScheduleDarkStart = times.darkStart
                store.lastScheduleLightStart = times.lightStart
                store.scheduleEnabled = true
                return ManagerResult("Native Android schedule is already configured and verified.", true)
            }
            run(
                "cmd uimode time start ${times.darkStartText()} " +
                    "&& cmd uimode time end ${times.lightStartText()} " +
                    "&& cmd uimode night custom_schedule"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (manager.nightMode != UiModeManager.MODE_NIGHT_CUSTOM ||
                    manager.customNightModeStart != times.darkStart ||
                    manager.customNightModeEnd != times.lightStart)
            ) {
                error("Android did not report the requested native schedule after applying it.")
            }
            store.darkStart = times.darkStart
            store.lightStart = times.lightStart
            store.lastScheduleDarkStart = times.darkStart
            store.lastScheduleLightStart = times.lightStart
            store.scheduleEnabled = true
            ManagerResult(
                "Native Android schedule is configured and verified. Android may defer the visible change until the screen turns off.",
                true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ManagerResult("Could not configure the native schedule: ${e.message}", false)
        }
    }

    suspend fun disableSchedule(): ManagerResult {
        return try {
            run("cmd uimode night no")
            if (uiModeManager().nightMode != UiModeManager.MODE_NIGHT_NO) {
                error("Android did not confirm that automatic dark mode is set to none.")
            }
            store.scheduleEnabled = false
            ManagerResult("Android's native dark-mode schedule is off (mode: none).", true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ManagerResult("Could not turn off Android's native schedule: ${e.message}", false)
        }
    }

    suspend fun restoreSchedule(): ManagerResult {
        if (!store.hasScheduleBackup) {
            store.scheduleEnabled = false
            return ManagerResult("No previous schedule snapshot is available. Current Android settings were left unchanged.", false)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            store.scheduleEnabled = false
            return ManagerResult(
                "This Android version cannot read the saved native schedule times, so current settings were left unchanged.",
                false
            )
        }
        val oldMode = store.originalMode
        val oldStart = store.originalDarkStart
        val oldEnd = store.originalLightStart
        val lastStart = store.lastScheduleDarkStart
        val lastEnd = store.lastScheduleLightStart
        if (oldMode == null || oldStart == null || oldEnd == null ||
            lastStart == null || lastEnd == null
        ) {
            store.scheduleEnabled = false
            return ManagerResult(
                "Android did not expose enough original schedule state to restore it safely. The current native schedule was left unchanged.",
                false
            )
        }
        return try {
            val manager = uiModeManager()
            val modeIsUnchanged = manager.nightMode == UiModeManager.MODE_NIGHT_CUSTOM ||
                manager.nightMode == UiModeManager.MODE_NIGHT_NO
            if (!modeIsUnchanged ||
                manager.customNightModeStart != lastStart ||
                manager.customNightModeEnd != lastEnd
            ) {
                store.scheduleEnabled = false
                return ManagerResult("The native schedule changed outside Eclipse; it was left untouched.", false)
            }
            run("cmd uimode time start ${oldStart.format(TIME_FORMAT)}")
            run("cmd uimode time end ${oldEnd.format(TIME_FORMAT)}")
            run("cmd uimode night ${modeArgument(oldMode)}")
            if (manager.nightMode != oldMode ||
                (oldMode == UiModeManager.MODE_NIGHT_CUSTOM &&
                    (manager.customNightModeStart != oldStart || manager.customNightModeEnd != oldEnd))
            ) {
                error("Previous native schedule did not verify after restoration.")
            }
            store.scheduleEnabled = false
            store.clearScheduleBackup()
            ManagerResult("The previous native schedule was safely restored.", true)
        } catch (e: Exception) {
            ManagerResult("Could not restore the native schedule: ${e.message}", false)
        }
    }

    suspend fun readBatteryConstants(): String? {
        val result = shell.run("settings get global battery_saver_constants")
        requireSuccess(result)
        return result.stdout.trimEnd('\r', '\n').let { if (it == "null") null else it }
    }

    fun scheduleStatus(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = uiModeManager()
            when (manager.nightMode) {
                UiModeManager.MODE_NIGHT_CUSTOM ->
                    "Custom schedule · dark ${manager.customNightModeStart.format(TIME_FORMAT)} · light ${manager.customNightModeEnd.format(TIME_FORMAT)}"
                UiModeManager.MODE_NIGHT_NO -> "None"
                else -> "Android mode: ${modeName(manager.nightMode)}"
            }
        } else {
            "Exact schedule details require Android 11 or later."
        }
    }

    fun customScheduleTimes(): ScheduleTimes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = uiModeManager()
            if (manager.nightMode == UiModeManager.MODE_NIGHT_CUSTOM) {
                ScheduleTimes(manager.customNightModeStart, manager.customNightModeEnd)
            } else null
        } else null

    suspend fun diagnosticConfig(): String? = readBatteryConstants()

    fun currentModeName(): String = modeName(uiModeManager().nightMode)

    private suspend fun writeBatteryConstants(value: String) {
        requireSuccess(
            shell.run("settings put global battery_saver_constants ${ShizukuShell.quote(value)}")
        )
    }

    private suspend fun run(command: String): ShellResult =
        shell.run(command).also(::requireSuccess)

    private fun requireSuccess(result: ShellResult) {
        check(result.successful) { result.summary }
    }

    private fun uiModeManager(): UiModeManager =
        context.getSystemService(UiModeManager::class.java)
            ?: error("Android UI mode service is unavailable.")

    private fun modeName(mode: Int): String = when (mode) {
        UiModeManager.MODE_NIGHT_CUSTOM -> "Custom schedule"
        UiModeManager.MODE_NIGHT_NO -> "Light"
        UiModeManager.MODE_NIGHT_YES -> "Dark"
        UiModeManager.MODE_NIGHT_AUTO -> "Automatic"
        else -> "Unknown ($mode)"
    }

    private fun modeArgument(mode: Int): String = when (mode) {
        UiModeManager.MODE_NIGHT_CUSTOM -> "custom_schedule"
        UiModeManager.MODE_NIGHT_NO -> "no"
        UiModeManager.MODE_NIGHT_YES -> "yes"
        UiModeManager.MODE_NIGHT_AUTO -> "auto"
        else -> error("Android reported an unsupported previous night mode.")
    }

    companion object {
        private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
