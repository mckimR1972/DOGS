package com.teamdogs.dogs

import com.teamdogs.dogs.group.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GroupScheduleTest {
    @Test fun weeklyRuleUsesIsoWeekdayAndDailyRuleIncludesEveryDay() {
        val monday = LocalDate.of(2026,11,9)
        val weekly = Todo(repeat_rule=RepeatRule("weekly",listOf(1,3,5)))
        assertTrue(weekly.isDue(monday)); assertFalse(weekly.isDue(monday.plusDays(1)))
        assertTrue(Todo().isDue(monday.plusDays(6)))
    }
    @Test fun occurrenceAndMigrationIdsSeparateDaysGroupsAndLegacyIds() {
        assertNotEquals(logId("todo","2026-11-06"),logId("todo","2026-11-07"))
        assertEquals(stableId("g1:dog:pet-1"),stableId("g1:dog:pet-1"))
        assertNotEquals(stableId("g1:dog:pet-1"),stableId("g2:dog:pet-1"))
    }
}
