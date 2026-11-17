package com.teamdogs.dogs.photos

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.*
import com.teamdogs.dogs.group.Photo
import com.teamdogs.dogs.group.awaitGroup
import com.teamdogs.dogs.prototype.importLocalPhoto
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

// null = all, empty string = common, otherwise one dog ID.
data class PhotoPage(val photos: List<Photo>, val cursor: DocumentSnapshot?, val hasMore: Boolean)
data class PhotoDraft(val id: String, val dogId: String?, val file: File)
interface PhotoRepository {
    suspend fun list(filter: String?, cursor: DocumentSnapshot? = null, limit: Long = 30): PhotoPage
    suspend fun prepare(uri: Uri, dogId: String?): PhotoDraft
    fun pending(): PhotoDraft?
    suspend fun upload(draft: PhotoDraft, progress: (Float) -> Unit)
    suspend fun discard(draft: PhotoDraft)
    suspend fun delete(id: String)
    suspend fun image(photo: Photo, thumbnail: Boolean): File
    fun close()
}
class FirebasePhotoRepository(
    private val context: Context, private val uid: String, private val groupId: String,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance("gs://dogs-dev-f9a4e.firebasestorage.app"),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3"),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : PhotoRepository {
    private val collection = db.collection("groups").document(groupId).collection("photos")
    private val draftDir = File(context.noBackupFilesDir,"photo_uploads/$uid/$groupId").apply { mkdirs() }
    private val cache = File(context.cacheDir,"group_photos/$uid/$groupId/${UUID.randomUUID()}").apply { mkdirs() }
    @Volatile private var closed = false
    private fun active() { check(!closed && auth.currentUser?.uid == uid) { "로그인 상태가 변경됐어요." } }
    override suspend fun list(filter: String?, cursor: DocumentSnapshot?, limit: Long): PhotoPage {
        active()
        var query: Query = collection.whereEqualTo("status","ready")
        if(filter != null) query = query.whereEqualTo("dog_id",filter.ifEmpty { null })
        query = query.orderBy("created_at",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(limit)
        if(cursor != null) query = query.startAfter(cursor)
        val result = query.get(Source.SERVER).awaitGroup()
        active()
        return PhotoPage(result.documents.mapNotNull { it.toObject(Photo::class.java) },result.documents.lastOrNull(),result.size().toLong()==limit)
    }
    override suspend fun prepare(uri: Uri, dogId: String?): PhotoDraft = withContext(Dispatchers.IO) {
        active(); check(pending()==null) { "먼저 이전 업로드를 완료하거나 취소해 주세요." }
        val id=UUID.randomUUID().toString()
        val imported=File(importLocalPhoto(context,uri,"upload_$uid"))
        try {
            require(imported.length() in 1..5L*1024*1024) { "사진 용량이 너무 커요. 다른 사진을 선택해 주세요." }
            val image=File(draftDir,"$id.jpg"); imported.copyTo(image,overwrite=true)
            val json=JSONObject().put("id",id).put("dogId",dogId ?: JSONObject.NULL)
            File(draftDir,"pending.tmp").writeText(json.toString())
            check(File(draftDir,"pending.tmp").renameTo(File(draftDir,"pending.json")))
            PhotoDraft(id,dogId,image)
        } finally { imported.delete() }
    }
    override fun pending(): PhotoDraft? = runCatching {
        val j=JSONObject(File(draftDir,"pending.json").readText()); val id=j.getString("id")
        require(Regex("[a-f0-9-]{36}").matches(id))
        PhotoDraft(id,if(j.isNull("dogId")) null else j.getString("dogId"),File(draftDir,"$id.jpg"))
    }.getOrNull()
    private suspend fun call(name: String, id: String, dogId: String? = null): Map<*,*> {
        active()
        val result=functions.getHttpsCallable(name).apply { setTimeout(120, java.util.concurrent.TimeUnit.SECONDS) }.call(mapOf("groupId" to groupId,"photoId" to id,"dogId" to dogId)).awaitGroup()
        active(); return result.data as Map<*,*>
    }
    override suspend fun upload(draft: PhotoDraft, progress: (Float) -> Unit) {
        val result=call("preparePhotoUpload",draft.id,draft.dogId)
        if(result["status"]!="ready") {
            val path="groups/$groupId/photos/${draft.id}/upload.jpg"
            check(result["upload_path"]==path)
            val ref=storage.reference.child(path)
            // The server checks staging existence; missing-object metadata reads can return 403 in production.
            val exists=result["upload_exists"] == true
            if(!exists) {
                require(draft.file.isFile) { "선택한 사진 파일이 없어요. 취소 후 다시 선택해 주세요." }
                val task=ref.putFile(Uri.fromFile(draft.file),StorageMetadata.Builder().setContentType("image/jpeg").build())
                task.addOnProgressListener { progress((it.bytesTransferred.toFloat()/it.totalByteCount.coerceAtLeast(1))*0.9f) }
                try { task.awaitGroup() } catch(e: CancellationException) { task.cancel(); throw e }
            }
            progress(.95f); call("completePhotoUpload",draft.id)
        }
        progress(1f); clearDraft(draft)
    }
    private fun clearDraft(draft: PhotoDraft) { draft.file.delete(); File(draftDir,"pending.json").delete() }
    override suspend fun discard(draft: PhotoDraft) { delete(draft.id); clearDraft(draft) }
    override suspend fun delete(id: String) { call("deletePhoto",id); cache.listFiles()?.filter { it.name.startsWith(id) }?.forEach { it.delete() } }
    override suspend fun image(photo: Photo, thumbnail: Boolean): File = withContext(Dispatchers.IO) {
        active()
        val name=if(thumbnail) "thumb" else "image"
        val expected="groups/$groupId/photos/${photo.id}/$name.jpg"
        require(photo.group_id==groupId && (if(thumbnail) photo.thumbnail_path else photo.storage_path)==expected)
        val ref=storage.reference.child(expected)
        // Recheck access even when an image is cached. Never use public download tokens.
        ref.metadata.awaitGroup(); active()
        val target=File(cache,"${photo.id}_$name.jpg")
        if(!target.exists()) {
            cache.mkdirs()
            val temporary=File(cache,"${photo.id}_${UUID.randomUUID()}.part")
            val task=ref.getFile(temporary)
            try { task.awaitGroup(); active(); check(temporary.renameTo(target)) }
            catch(e: CancellationException) { task.cancel(); throw e }
            finally { temporary.delete() }
        }
        val files=cache.listFiles().orEmpty().filter { it.extension=="jpg" }.sortedBy { it.lastModified() }
        var bytes=files.sumOf { it.length() }
        for(file in files) if(bytes>40L*1024*1024 && file!=target) { bytes-=file.length(); file.delete() }
        target.setLastModified(System.currentTimeMillis()); target
    }
    override fun close() { closed=true; cache.deleteRecursively() }
}
