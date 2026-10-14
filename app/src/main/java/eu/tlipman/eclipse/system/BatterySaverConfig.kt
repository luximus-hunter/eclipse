package eu.tlipman.eclipse.system

sealed interface ConfigUpdate {
    data class Success(val value: String) : ConfigUpdate
    data class Failure(val reason: String) : ConfigUpdate
}

sealed interface ConfigRestore {
    data class Success(val value: String?) : ConfigRestore
    data class Failure(val reason: String) : ConfigRestore
}

object BatterySaverConfig {
    const val KEY = "enable_night_mode"

    fun disableNightMode(current: String?): ConfigUpdate {
        val entries = parse(current) ?: return ConfigUpdate.Failure(
            "Battery Saver configuration contains malformed entries and was left unchanged."
        )
        var found = false
        val updated = entries.map { entry ->
            if (entry.key == KEY) {
                found = true
                entry.withValue("false")
            } else {
                entry.raw
            }
        }
        return ConfigUpdate.Success(
            if (found) updated.joinToString(",") else (updated + "$KEY=false").joinToString(",")
        )
    }

    fun hasNightModeDisabled(value: String?): Boolean =
        parse(value)?.filter { it.key == KEY }?.let { matches ->
            matches.isNotEmpty() && matches.all { it.value.trim() == "false" }
        } ?: false

    fun safeRestore(
        current: String?,
        lastApplied: String,
        original: String?
    ): ConfigUpdate = when {
        current != lastApplied -> ConfigUpdate.Failure(
            "Battery Saver configuration changed outside Eclipse. It was left untouched."
        )
        original == null -> ConfigUpdate.Success("")
        else -> ConfigUpdate.Success(original)
    }

    fun restoreNightModeOnly(current: String?, original: String?): ConfigRestore {
        val currentEntries = parse(current) ?: return ConfigRestore.Failure(
            "Current Battery Saver configuration is malformed; it was left unchanged."
        )
        val originalEntries = parse(original) ?: return ConfigRestore.Failure(
            "Saved Battery Saver configuration is malformed; it was left unchanged."
        )
        val originalNightEntries = originalEntries.filter { it.key == KEY }.map { it.raw }
        val merged = mutableListOf<String>()
        var restoredTarget = false
        currentEntries.forEach { entry ->
            if (entry.key == KEY) {
                if (!restoredTarget) {
                    merged.addAll(originalNightEntries)
                    restoredTarget = true
                }
            } else {
                merged += entry.raw
            }
        }
        if (!restoredTarget) merged.addAll(originalNightEntries)
        if (merged.isEmpty() && original == null) {
            return ConfigRestore.Success(null)
        }
        return ConfigRestore.Success(merged.joinToString(","))
    }

    private data class Entry(
        val raw: String,
        val key: String,
        val value: String,
        val valueStart: Int
    ) {
        fun withValue(replacement: String): String {
            val rawValue = raw.substring(valueStart)
            val leading = rawValue.takeWhile(Char::isWhitespace)
            val trailing = rawValue.takeLastWhile(Char::isWhitespace)
            return raw.substring(0, valueStart) + leading + replacement + trailing
        }
    }

    private fun parse(value: String?): List<Entry>? {
        if (value.isNullOrEmpty()) return emptyList()
        val segments = value.split(',')
        if (segments.any { it.isBlank() }) return null
        return segments.map { raw ->
            val separator = raw.indexOf('=')
            if (separator <= 0 || raw.substring(0, separator).trim().isEmpty()) {
                return null
            }
            Entry(
                raw = raw,
                key = raw.substring(0, separator).trim(),
                value = raw.substring(separator + 1),
                valueStart = separator + 1
            )
        }
    }
}
