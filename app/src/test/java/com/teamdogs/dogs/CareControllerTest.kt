package com.teamdogs.dogs

import com.teamdogs.dogs.prototype.*
import org.junit.Assert.*
import org.junit.Test

class MemoryCareStore(var value: CareState? = null) : CareStore {
    override fun load() = value
    override fun save(state: CareState) { value = state }
}

class CareControllerTest {
    @Test fun completionCanBeUndoneAndRestored() {
        val store = MemoryCareStore()
        val controller = CareController(store, true)
        controller.toggle("dinner")
        assertEquals(5, controller.state.tasks.count { it.done })
        val restored = CareController(store, true)
        assertTrue(restored.state.tasks.first { it.id == "dinner" }.done)
        restored.toggle("dinner")
        assertEquals(4, restored.state.tasks.count { it.done })
    }
    @Test fun invalidTaskDoesNotMutateStore() {
        val controller = CareController(MemoryCareStore(), true)
        val before = controller.state
        assertFalse(controller.addTask("", "19:00", "kong"))
        assertFalse(controller.addTask("산책", "25:00", "kong"))
        assertFalse(controller.addTask("산책", "10:99", "kong"))
        assertFalse(controller.addTask("산책", "10:30", "missing"))
        assertEquals(before, controller.state)
        assertTrue(controller.addTask("간식", "14:30", "kong"))
        assertEquals(7, controller.state.tasks.size)
    }
    @Test fun newUserDoesNotInheritDemoAndCanSkipStarterTasks() {
        val real = CareController(MemoryCareStore(), false)
        assertFalse(real.state.ready)
        assertTrue(real.state.tasks.isEmpty())
        assertFalse(real.completeSetup("", "우리집", "초코", "", emptySet()))
        assertTrue(real.completeSetup("민지", "우리집", "초코", "", emptySet()))
        assertEquals("초코", real.state.pets.single().name)
        assertTrue(real.state.tasks.isEmpty())
    }
    @Test fun onlySelectedStarterTasksAreCreated() {
        val controller = CareController(MemoryCareStore(), false)
        controller.completeSetup("민지", "우리집", "초코", "", setOf("산책"))
        assertEquals("산책", controller.state.tasks.single().title)
        assertEquals(controller.state.pets.single().id, controller.state.tasks.single().petId)
    }
    @Test fun newDayClearsCompletionButKeepsTasks() {
        val store = MemoryCareStore(sampleCareState().copy(day = "2020-01-01"))
        val controller = CareController(store, true)
        assertEquals(6, controller.state.tasks.size)
        assertEquals(0, controller.state.tasks.count { it.done })
    }
    @Test fun reopeningSetupPreservesExistingPetsAndTheirTasks() {
        val controller = CareController(MemoryCareStore(), true)
        controller.startSetup()
        controller.completeSetup("민지", "우리집", "콩이", "", emptySet())
        assertTrue(controller.state.tasks.all { task -> controller.state.pets.any { it.id == task.petId } })
    }
}
