package com.teamdogs.dogs.group

import com.teamdogs.dogs.prototype.CareState

/** Validates initial group input before starting any server writes. */
fun validateGroupSetup(state: CareState) {
    require(state.ready && state.nickname.isNotBlank() && state.nickname.length <= 20)
    require(state.groupName.isNotBlank() && state.groupName.length <= 30)
    require(state.pets.isNotEmpty() && state.pets.size <= 20 && state.tasks.size <= 100)
    require(state.pets.map { it.id }.distinct().size == state.pets.size)
    require(state.tasks.map { it.id }.distinct().size == state.tasks.size)
    require(state.pets.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 20 })
    require(state.tasks.all { task -> task.id.isNotBlank() && task.title.isNotBlank() && task.title.length <= 100 &&
        task.note.length <= 1000 && Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(task.time) &&
        state.pets.any { it.id == task.petId } })
}
