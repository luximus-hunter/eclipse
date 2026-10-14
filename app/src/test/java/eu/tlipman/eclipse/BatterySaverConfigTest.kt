package eu.tlipman.eclipse

import eu.tlipman.eclipse.system.BatterySaverConfig
import eu.tlipman.eclipse.system.ConfigUpdate
import eu.tlipman.eclipse.system.ConfigRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaverConfigTest {
    @Test
    fun changesOnlyNightModeAndPreservesUnknownEntries() {
        val original =
            "adjust_brightness_factor=0.8,enable_night_mode=true,future_key=kept"
        val result = BatterySaverConfig.disableNightMode(original)

        assertEquals(
            ConfigUpdate.Success(
                "adjust_brightness_factor=0.8,enable_night_mode=false,future_key=kept"
            ),
            result
        )
    }

    @Test
    fun absentAndEmptySettingsCreateMinimalOverride() {
        assertEquals(
            ConfigUpdate.Success("enable_night_mode=false"),
            BatterySaverConfig.disableNightMode(null)
        )
        assertEquals(
            ConfigUpdate.Success("enable_night_mode=false"),
            BatterySaverConfig.disableNightMode("")
        )
    }

    @Test
    fun updatesAllDuplicateTargetKeysAndIsIdempotent() {
        val once = BatterySaverConfig.disableNightMode(
            " enable_night_mode = true ,enable_night_mode=yes,unknown=1"
        )
        assertEquals(
            ConfigUpdate.Success(
                " enable_night_mode = false ,enable_night_mode=false,unknown=1"
            ),
            once
        )
        assertTrue(
            BatterySaverConfig.hasNightModeDisabled(
                (once as ConfigUpdate.Success).value
            )
        )
        assertEquals(once, BatterySaverConfig.disableNightMode(once.value))
    }

    @Test
    fun malformedEntriesFailInsteadOfDiscardingData() {
        assertTrue(BatterySaverConfig.disableNightMode("unknown=1,bad-entry") is ConfigUpdate.Failure)
        assertTrue(BatterySaverConfig.disableNightMode("unknown=1,,other=2") is ConfigUpdate.Failure)
    }

    @Test
    fun restoreRequiresTheCurrentValueToMatchEclipsesLastWrite() {
        assertEquals(
            ConfigUpdate.Success("original=value"),
            BatterySaverConfig.safeRestore("applied=false", "applied=false", "original=value")
        )
        assertEquals(
            ConfigUpdate.Success(""),
            BatterySaverConfig.safeRestore("applied=false", "applied=false", null)
        )
        assertTrue(
            BatterySaverConfig.safeRestore("external=change", "applied=false", "original=value")
                is ConfigUpdate.Failure
        )
    }

    @Test
    fun nightModeVerifierRejectsMixedDuplicates() {
        assertFalse(BatterySaverConfig.hasNightModeDisabled("enable_night_mode=false,enable_night_mode=true"))
    }

    @Test
    fun conflictRestoreChangesOnlyEclipseEntry() {
        assertEquals(
            ConfigRestore.Success("foreign=new,enable_night_mode=true,other=kept"),
            BatterySaverConfig.restoreNightModeOnly(
                "foreign=new,enable_night_mode=false,other=kept",
                "enable_night_mode=true,foreign=old"
            )
        )
    }

    @Test
    fun conflictRestoreRemovesOnlyManagedEntryForOriginallyAbsentSetting() {
        assertEquals(
            ConfigRestore.Success("foreign=kept"),
            BatterySaverConfig.restoreNightModeOnly(
                "foreign=kept,enable_night_mode=false",
                null
            )
        )
        assertEquals(
            ConfigRestore.Success(null),
            BatterySaverConfig.restoreNightModeOnly("enable_night_mode=false", null)
        )
    }
}
