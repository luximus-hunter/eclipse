package eu.tlipman.eclipse

import eu.tlipman.eclipse.system.ScheduleTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ScheduleTimeTest {
    @Test
    fun parsesAndFormats24HourTimes() {
        val time = ScheduleTimes.parse("19:30")
        assertEquals(LocalTime.of(19, 30), time)
        assertEquals("19:30", ScheduleTimes(time!!, LocalTime.of(6, 30)).darkStartText())
    }

    @Test
    fun rejectsInvalidFormatsAndEqualTimes() {
        assertNull(ScheduleTimes.parse("25:00"))
        assertNull(ScheduleTimes.parse("6:30"))
        assertEquals(
            "Start and end times must be different.",
            ScheduleTimes(LocalTime.of(7, 0), LocalTime.of(7, 0)).validate()
        )
    }

    @Test
    fun acceptsCrossMidnightSchedule() {
        val overnight = ScheduleTimes(LocalTime.of(19, 30), LocalTime.of(6, 30))
        assertTrue(overnight.validate() == null)
        assertEquals("19:30", overnight.darkStartText())
        assertEquals("06:30", overnight.lightStartText())
    }
}
