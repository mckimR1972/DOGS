package com.teamdogs.dogs.group

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// Firestore field names deliberately match the team's logical model.
data class User(val id: String = "", val email: String = "", val name: String = "", val profile_img: String = "",
    val active_group_id: String? = null, val schema_version: Long = 2)
data class Group(val id: String = "", val name: String = "", val leader_id: String = "", val invite_code: String? = null,
    val timezone: String = "Asia/Seoul", val created_at: Timestamp? = null)
data class GroupMember(val group_id: String = "", val user_id: String = "", val role: String = "member", val joined_at: Timestamp? = null)
data class Dog(val id: String = "", val group_id: String = "", val name: String = "", val breed: String = "",
    val birth: String? = null, val weight: Double? = null, val photo: String = "")
data class RepeatRule(val frequency: String = "daily", val weekdays: List<Int> = emptyList())
data class Todo(val id: String = "", val group_id: String = "", val dog_id: String? = null, val title: String = "",
    val repeat_rule: RepeatRule = RepeatRule(), val due_at: String = "19:00", val created_by: String = "", val note: String = "")
data class TodoLog(val id: String = "", val todo_id: String = "", val done_by: String = "", val done_at: Timestamp? = null,
    val occurrence_day: String = "")
data class ChangeRequest(val id: String = "", val todo_id: String = "", val proposer_id: String = "",
    val payload: Map<String, Any?> = emptyMap(), val status: String = "pending")
data class CareRequest(val id: String = "", val group_id: String = "", val from_user_id: String = "", val to_user_id: String = "",
    val content: String = "", val status: String = "pending")
data class Photo(val id: String = "", val group_id: String = "", val dog_id: String? = null, val uploader_id: String = "",
    val url: String = "", val created_at: Timestamp? = null, val storage_path: String = "", val status: String = "uploading")
data class Notification(val id: String = "", val user_id: String = "", val type: String = "", val ref_id: String = "",
    @get:com.google.firebase.firestore.PropertyName("is_read") val is_read: Boolean = false, val created_at: Timestamp? = null)
data class GroupSnapshot(val user: User, val group: Group, val members: List<GroupMember>, val dogs: List<Dog>,
    val todos: List<Todo>, val logs: List<TodoLog>, val day: String)

fun stableId(value: String): String = UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()
fun Todo.isDue(day: LocalDate): Boolean = repeat_rule.frequency == "daily" ||
    (repeat_rule.frequency == "weekly" && day.dayOfWeek.value in repeat_rule.weekdays)
fun Group.today(): String = LocalDate.now(ZoneId.of(timezone)).toString()
fun logId(todoId: String, day: String) = "${todoId}_$day"
