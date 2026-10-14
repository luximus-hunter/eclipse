package eu.tlipman.eclipse.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalTime

@SuppressLint("ApplySharedPref")
class EclipseStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("eclipse", Context.MODE_PRIVATE)

    var overrideEnabled: Boolean
        get() = prefs.getBoolean("override_enabled", false)
        set(value) {
            commit { putBoolean("override_enabled", value) }
        }
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean("schedule_enabled", false)
        set(value) {
            commit { putBoolean("schedule_enabled", value) }
        }
    var lastAppliedConfig: String?
        get() = prefs.getString("last_applied_config", null)
        set(value) {
            commit {
                if (value == null) remove("last_applied_config")
                else putString("last_applied_config", value)
            }
        }
    var configConflictDetected: Boolean
        get() = prefs.getBoolean("config_conflict_detected", false)
        set(value) {
            commit { putBoolean("config_conflict_detected", value) }
        }
    var originalConfig: String?
        get() = if (prefs.getBoolean("original_config_present", false)) {
            prefs.getString("original_config", "")
        } else null
        set(value) {
            commit {
                putBoolean("original_config_present", value != null)
                if (value != null) putString("original_config", value) else remove("original_config")
            }
        }
    val hasConfigBackup: Boolean
        get() = prefs.getBoolean("config_backup_saved", false)
    fun markConfigBackupSaved() {
        commit { putBoolean("config_backup_saved", true) }
    }
    fun clearConfigBackup() {
        commit {
            remove("original_config")
            remove("original_config_present")
            remove("last_applied_config")
            remove("config_conflict_detected")
            remove("config_backup_saved")
        }
    }
    val hasScheduleBackup: Boolean
        get() = prefs.getBoolean("schedule_backup_saved", false)
    fun markScheduleBackupSaved() {
        commit { putBoolean("schedule_backup_saved", true) }
    }
    fun clearScheduleBackup() {
        commit {
            remove("schedule_backup_saved")
            remove("original_mode")
            remove("original_dark_hour")
            remove("original_dark_minute")
            remove("original_light_hour")
            remove("original_light_minute")
            remove("last_dark_hour")
            remove("last_dark_minute")
            remove("last_light_hour")
            remove("last_light_minute")
        }
    }

    var darkStart: LocalTime
        get() = LocalTime.of(prefs.getInt("dark_hour", 19), prefs.getInt("dark_minute", 30))
        set(value) {
            commit { putInt("dark_hour", value.hour).putInt("dark_minute", value.minute) }
        }
    var lightStart: LocalTime
        get() = LocalTime.of(prefs.getInt("light_hour", 6), prefs.getInt("light_minute", 30))
        set(value) {
            commit { putInt("light_hour", value.hour).putInt("light_minute", value.minute) }
        }

    var originalMode: Int?
        get() = if (prefs.contains("original_mode")) prefs.getInt("original_mode", 0) else null
        set(value) {
            commit {
                if (value == null) remove("original_mode") else putInt("original_mode", value)
            }
        }
    var originalDarkStart: LocalTime?
        get() = readTime("original_dark_hour", "original_dark_minute")
        set(value) = saveTime("original_dark_hour", "original_dark_minute", value)
    var originalLightStart: LocalTime?
        get() = readTime("original_light_hour", "original_light_minute")
        set(value) = saveTime("original_light_hour", "original_light_minute", value)
    var lastScheduleDarkStart: LocalTime?
        get() = readTime("last_dark_hour", "last_dark_minute")
        set(value) = saveTime("last_dark_hour", "last_dark_minute", value)
    var lastScheduleLightStart: LocalTime?
        get() = readTime("last_light_hour", "last_light_minute")
        set(value) = saveTime("last_light_hour", "last_light_minute", value)

    private fun readTime(hour: String, minute: String): LocalTime? =
        if (prefs.contains(hour) && prefs.contains(minute)) {
            LocalTime.of(prefs.getInt(hour, 0), prefs.getInt(minute, 0))
        } else null

    private fun saveTime(hour: String, minute: String, value: LocalTime?) {
        commit {
            if (value == null) {
                remove(hour)
                remove(minute)
            } else {
                putInt(hour, value.hour)
                putInt(minute, value.minute)
            }
        }
    }

    private inline fun commit(crossinline edit: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true) { edit() }
    }
}
