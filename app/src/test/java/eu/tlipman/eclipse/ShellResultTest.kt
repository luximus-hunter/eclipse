package eu.tlipman.eclipse

import eu.tlipman.eclipse.shizuku.ShellResult
import eu.tlipman.eclipse.shizuku.ShizukuShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellResultTest {
    @Test
    fun shellArgumentsAreSingleQuotedSafely() {
        assertEquals("'simple'", ShizukuShell.quote("simple"))
        assertEquals("'a'\\''b'", ShizukuShell.quote("a'b"))
    }

    @Test
    fun commandResultDoesNotTreatFailureOrTimeoutAsSuccess() {
        assertTrue(ShellResult(0, "done", "").successful)
        assertFalse(ShellResult(1, "", "denied").successful)
        assertFalse(ShellResult(0, "", "", timedOut = true).successful)
        assertEquals("denied", ShellResult(1, "", "denied").summary)
    }
}
