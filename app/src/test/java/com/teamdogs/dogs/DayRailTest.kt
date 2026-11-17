package com.teamdogs.dogs

import com.teamdogs.dogs.ui.components.RailState.DONE
import com.teamdogs.dogs.ui.components.RailState.LATE
import com.teamdogs.dogs.ui.components.RailState.UPCOMING
import com.teamdogs.dogs.ui.components.mergedRailState
import com.teamdogs.dogs.ui.components.parseMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayRailTest {
    @Test fun mergedCapsuleIsDoneOnlyWhenEveryTaskIsDone() {
        assertEquals(DONE, mergedRailState(listOf(DONE, DONE)))
        assertEquals(UPCOMING, mergedRailState(listOf(DONE, UPCOMING)))
        assertEquals(LATE, mergedRailState(listOf(DONE, LATE)))
        assertEquals(LATE, mergedRailState(listOf(UPCOMING, LATE)))
        assertEquals(UPCOMING, mergedRailState(emptyList()))
    }

    @Test fun parsesOnlyValidClockTimes() {
        assertEquals(7 * 60 + 30, parseMinutes("07:30"))
        assertEquals(23 * 60 + 59, parseMinutes(" 23:59 "))
        assertNull(parseMinutes(""))
        assertNull(parseMinutes("24:00"))
        assertNull(parseMinutes("7시"))
        assertNull(parseMinutes("12:60"))
    }
}
