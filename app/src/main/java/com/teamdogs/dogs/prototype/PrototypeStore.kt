package com.teamdogs.dogs.prototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class Pet(val id: String, val name: String, val photo: String = "")
data class CareTask(val id: String, val title: String, val time: String, val petId: String,
    val note: String = "", val done: Boolean = false)
data class PetPhoto(val id: String, val path: String, val petId: String)
data class CareState(
    val ready: Boolean = false, val nickname: String = "", val groupName: String = "우리집",
    val pets: List<Pet> = emptyList(), val tasks: List<CareTask> = emptyList(),
    val photos: List<PetPhoto> = emptyList(), val requestAnswer: String = "",
    val day: String = LocalDate.now().toString(),
)

interface CareStore {
    fun load(): CareState?
    fun save(state: CareState)
}

// Prototype data stays on this device. Each Firebase UID and the demo have separate stores.
class LocalCareStore(context: Context, key: String) : CareStore {
    private val prefs = context.getSharedPreferences("dogs_prototype_$key", Context.MODE_PRIVATE)
    override fun load(): CareState? {
        val value = prefs.getString("state", null) ?: return null
        return runCatching {
            val j = JSONObject(value)
            CareState(j.optBoolean("ready"), j.optString("nickname"), j.optString("group", "우리집"),
                j.optJSONArray("pets").objects().map { Pet(it.getString("id"), it.getString("name"), it.optString("photo")) },
                j.optJSONArray("tasks").objects().map { CareTask(it.getString("id"), it.getString("title"),
                    it.getString("time"), it.getString("pet"), it.optString("note"), it.optBoolean("done")) },
                j.optJSONArray("photos").objects().map { PetPhoto(it.getString("id"), it.getString("path"), it.getString("pet")) },
                j.optString("answer"), j.optString("day", LocalDate.now().toString()))
        }.getOrNull()
    }
    override fun save(state: CareState) {
        val j = JSONObject().put("ready", state.ready).put("nickname", state.nickname)
            .put("group", state.groupName).put("answer", state.requestAnswer).put("day", state.day)
            .put("pets", JSONArray(state.pets.map { JSONObject().put("id", it.id).put("name", it.name).put("photo", it.photo) }))
            .put("tasks", JSONArray(state.tasks.map { JSONObject().put("id", it.id).put("title", it.title)
                .put("time", it.time).put("pet", it.petId).put("note", it.note).put("done", it.done) }))
            .put("photos", JSONArray(state.photos.map { JSONObject().put("id", it.id).put("path", it.path).put("pet", it.petId) }))
        prefs.edit().putString("state", j.toString()).apply()
    }
}
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

class CareController(private val store: CareStore, val demo: Boolean) {
    var state by mutableStateOf(store.load() ?: if (demo) sampleCareState() else CareState())
        private set
    init { refreshDay() }
    private fun update(value: CareState) { state = value; store.save(value) }
    fun refreshDay() {
        val today = LocalDate.now().toString()
        if (state.day != today) update(state.copy(day = today, tasks = state.tasks.map { it.copy(done = false) }))
    }
    fun toggle(id: String) = update(state.copy(tasks = state.tasks.map { if (it.id == id) it.copy(done = !it.done) else it }))
    fun answer(value: String) = update(state.copy(requestAnswer = value))
    fun addTask(title: String, time: String, petId: String): Boolean {
        if (title.isBlank() || !Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(time) || state.pets.none { it.id == petId }) return false
        update(state.copy(tasks = state.tasks + CareTask(UUID.randomUUID().toString(), title.trim(), time, petId)))
        return true
    }
    fun addPhoto(path: String, petId: String) {
        if (state.pets.any { it.id == petId }) update(state.copy(photos = listOf(PetPhoto(UUID.randomUUID().toString(), path, petId)) + state.photos))
    }
    fun startSetup() = update(state.copy(ready = false))
    fun restore(value: CareState) { update(value); refreshDay() }
    fun completeSetup(nickname: String, group: String, petName: String, photo: String, starters: Set<String>): Boolean {
        val value = prepareSetup(nickname, group, petName, photo, starters) ?: return false
        update(value)
        return true
    }
    fun prepareSetup(nickname: String, group: String, petName: String, photo: String, starters: Set<String>): CareState? {
        if (nickname.isBlank() || nickname.trim().length > 20 || group.trim().length > 30 || petName.isBlank() || petName.trim().length > 20) return null
        val pet = Pet(state.pets.firstOrNull()?.id ?: "pet-1", petName.trim(), photo)
        val starterTasks = listOf("아침 급여" to "07:30", "산책" to "19:30", "물 갈아주기" to "12:00")
            .filter { it.first in starters }.map { CareTask(UUID.nameUUIDFromBytes("${pet.id}:${it.first}".toByteArray()).toString(), it.first, it.second, pet.id) }
        return state.copy(ready = true, nickname = nickname.trim(), groupName = group.trim().ifBlank { "우리집" },
            pets = listOf(pet) + state.pets.drop(1), tasks = if (state.tasks.isEmpty()) starterTasks else state.tasks,
            day = LocalDate.now().toString())
    }
}

fun sampleCareState() = CareState(ready = true, nickname = "민지", pets = listOf(Pet("kong", "콩이"), Pet("bori", "보리")),
    tasks = listOf(
        CareTask("breakfast", "아침 급여", "07:30", "kong", "사료 80g", true),
        CareTask("morning-walk", "아침 산책", "08:10", "bori", "동네 한 바퀴", true),
        CareTask("brush", "빗질", "12:05", "kong", "털을 부드럽게 정리해요", true),
        CareTask("water", "물그릇 갈기", "15:20", "bori", "깨끗한 물로 교체", true),
        CareTask("dinner", "저녁 급여", "17:30", "kong", "사료 80g"),
        CareTask("evening-walk", "저녁 산책", "19:30", "bori", "40분"),
    ))

// The system photo picker grants access only to the selected image. No storage permission or cloud upload.
suspend fun importLocalPhoto(context: Context, uri: Uri, ownerKey: String): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "사진을 읽지 못했어요." }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("사진을 읽지 못했어요.")
    val exif = resolver.openInputStream(uri)?.use { stream -> runCatching { ExifInterface(stream) }.getOrNull() }
    val matrix = Matrix().apply {
        if (exif != null) {
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
    }
    val oriented = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    val directory = File(context.filesDir, "prototype_photos/$ownerKey").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    try { file.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.JPEG, 88, it)) } }
    finally { if (oriented !== bitmap) oriented.recycle(); bitmap.recycle() }
    file.absolutePath
}
