package com.teamdogs.dogs.group

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.prototype.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface GroupRepository {
    suspend fun load(): GroupSnapshot?
    suspend fun saveSetup(state: CareState): GroupSnapshot
    suspend fun updateUser(user: User)
    suspend fun submitChange(groupId: String, request: ChangeRequest)
    suspend fun sendRequest(request: CareRequest)
    suspend fun respondRequest(groupId: String, requestId: String, accepted: Boolean)
    suspend fun notifications(): List<Notification>
    suspend fun markNotificationRead(id: String)
    suspend fun saveDog(dog: Dog)
    suspend fun saveTodo(todo: Todo)
    suspend fun deleteTodo(groupId: String, todoId: String)
    suspend fun setCompleted(groupId: String, todoId: String, day: String, completed: Boolean)
}

class FirestoreGroupRepository(private val account: SignedInUser,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : GroupRepository {
    private val userRef get() = db.document("users/${account.uid}")
    private fun groupRef(id: String) = db.document("groups/$id")

    override suspend fun load(): GroupSnapshot? {
        val userDoc = userRef.get(Source.SERVER).awaitGroup()
        if (userDoc.exists()) {
            val user = userDoc.toObject(User::class.java) ?: error("사용자 정보를 읽지 못했어요.")
            check(user.schema_version == 2L) { "지원하지 않는 사용자 데이터 버전이에요." }
            return user.active_group_id?.let { readGroup(user, it) }
        }
        return null
    }

    private suspend fun readGroup(user: User, gid: String): GroupSnapshot {
        val ref = groupRef(gid)
        val group = ref.get(Source.SERVER).awaitGroup().toObject(Group::class.java) ?: error("그룹을 찾을 수 없어요.")
        val members = ref.collection("members").get(Source.SERVER).awaitGroup().toObjects(GroupMember::class.java)
        check(members.any { it.user_id == account.uid }) { "그룹에 속해 있지 않아요." }
        val dogs = ref.collection("dogs").get(Source.SERVER).awaitGroup().toObjects(Dog::class.java)
        val todos = ref.collection("todos").get(Source.SERVER).awaitGroup().toObjects(Todo::class.java)
        val day = group.today()
        val logs = ref.collection("todoLogs").whereEqualTo("occurrence_day", day).get(Source.SERVER).awaitGroup().toObjects(TodoLog::class.java)
        return GroupSnapshot(user, group, members, dogs.sortedBy { it.id }, todos, logs, day)
    }

    override suspend fun saveSetup(state: CareState): GroupSnapshot {
        validateGroupSetup(state)
        val gid = stableId("first-group:${account.uid}")
        val ref = groupRef(gid)
        val dogIds = state.pets.associate { it.id to stableId("$gid:dog:${it.id}") }
        val resultGid = try { db.runTransaction { tx ->
            val old = tx.get(userRef)
            // Concurrent creation/retry must never recreate dogs/todos or overwrite newer group data.
            if (old.exists()) {
                val existing = old.toObject(User::class.java) ?: error("사용자 정보 오류")
                check(existing.schema_version == 2L)
                existing.active_group_id ?: error("그룹 가입 절차가 필요해요.")
            } else {
                tx.set(userRef, User(account.uid, account.email, state.nickname, "", gid))
                tx.set(ref, mapOf("id" to gid, "name" to state.groupName, "leader_id" to account.uid,
                    "invite_code" to null, "timezone" to "Asia/Seoul", "created_at" to FieldValue.serverTimestamp()))
                tx.set(ref.collection("members").document(account.uid), mapOf("group_id" to gid,
                    "user_id" to account.uid, "role" to "leader", "joined_at" to FieldValue.serverTimestamp()))
                state.pets.forEach { pet ->
                    val id = dogIds.getValue(pet.id)
                    tx.set(ref.collection("dogs").document(id), Dog(id, gid, pet.name))
                }
                state.tasks.forEach { task ->
                    val id = stableId("$gid:todo:${task.id}")
                    tx.set(ref.collection("todos").document(id), Todo(id, gid, dogIds.getValue(task.petId), task.title,
                        RepeatRule(), task.time, account.uid, task.note))
                }
                gid
            }
        }.awaitGroup() } catch (error: FirebaseFirestoreException) {
            // Rules can reject a stale create before the SDK retries a racing transaction.
            // Accept only a confirmed bootstrap by the same account, never weaken the rules.
            if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            val existing = userRef.get(Source.SERVER).awaitGroup().toObject(User::class.java)
            if (existing?.schema_version != 2L || existing.active_group_id != gid) throw error
            gid
        }
        val user = userRef.get(Source.SERVER).awaitGroup().toObject(User::class.java) ?: error("저장 확인 실패")
        return readGroup(user, resultGid)
    }

    override suspend fun updateUser(user: User) {
        require(user.id == account.uid && user.name.isNotBlank() && user.name.length <= 20)
        userRef.set(user).awaitGroup()
    }
    override suspend fun submitChange(groupId: String, request: ChangeRequest) {
        require(request.proposer_id == account.uid && request.status == "pending" && request.payload.isNotEmpty())
        groupRef(groupId).collection("changeRequests").document(request.id).set(request).awaitGroup()
    }
    override suspend fun sendRequest(request: CareRequest) {
        require(request.from_user_id == account.uid && request.status == "pending" && request.content.isNotBlank())
        groupRef(request.group_id).collection("requests").document(request.id).set(request).awaitGroup()
    }
    override suspend fun respondRequest(groupId: String, requestId: String, accepted: Boolean) {
        val ref = groupRef(groupId).collection("requests").document(requestId)
        val status = if (accepted) "accepted" else "rejected"
        db.runTransaction { tx ->
            val current = tx.get(ref)
            check(current.getString("to_user_id") == account.uid)
            if (current.getString("status") != status) {
                check(current.getString("status") == "pending")
                tx.update(ref, "status", status)
            }
        }.awaitGroup()
    }
    override suspend fun notifications(): List<Notification> = userRef.collection("notifications")
        .get(Source.SERVER).awaitGroup().toObjects(Notification::class.java)
    override suspend fun markNotificationRead(id: String) {
        userRef.collection("notifications").document(id).update("is_read", true).awaitGroup()
    }

    override suspend fun saveDog(dog: Dog) {
        require(dog.name.isNotBlank() && dog.name.length <= 20)
        require(dog.weight == null || (dog.weight > 0 && dog.weight <= 200))
        dog.birth?.let { java.time.LocalDate.parse(it) }
        groupRef(dog.group_id).collection("dogs").document(dog.id).set(dog).awaitGroup()
    }

    override suspend fun saveTodo(todo: Todo) {
        require(todo.title.isNotBlank() && todo.title.length <= 100)
        require(Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(todo.due_at))
        require(todo.repeat_rule.frequency in listOf("daily", "weekly"))
        require(todo.repeat_rule.weekdays.distinct().size == todo.repeat_rule.weekdays.size)
        require(todo.repeat_rule.weekdays.all { it in 1..7 })
        require(if (todo.repeat_rule.frequency == "daily") todo.repeat_rule.weekdays.isEmpty() else todo.repeat_rule.weekdays.isNotEmpty())
        groupRef(todo.group_id).collection("todos").document(todo.id).set(todo).awaitGroup()
    }

    override suspend fun deleteTodo(groupId: String, todoId: String) {
        groupRef(groupId).collection("todos").document(todoId).delete().awaitGroup()
    }

    override suspend fun setCompleted(groupId: String, todoId: String, day: String, completed: Boolean) {
        java.time.LocalDate.parse(day)
        val ref = groupRef(groupId).collection("todoLogs").document(logId(todoId, day))
        try { db.runTransaction { tx ->
            val current = tx.get(ref)
            if (completed && !current.exists()) tx.set(ref, mapOf("id" to ref.id, "todo_id" to todoId,
                "done_by" to account.uid, "done_at" to FieldValue.serverTimestamp(), "occurrence_day" to day))
            else if (!completed && current.exists()) tx.delete(ref)
        }.awaitGroup() } catch (error: FirebaseFirestoreException) {
            if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
            // Recheck under ordinary read rules: same occurrence already completed/undone is success.
            val exists = ref.get(Source.SERVER).awaitGroup().exists()
            if (exists != completed) throw error
        }
    }
}

internal suspend fun <T> Task<T>.awaitGroup(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { if (continuation.isActive) {
        if (it.isSuccessful) continuation.resume(it.result)
        else continuation.resumeWithException(it.exception ?: IllegalStateException("요청이 취소됐어요."))
    } }
}
