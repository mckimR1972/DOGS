package com.teamdogs.dogs

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.teamdogs.dogs.group.awaitGroup
import com.teamdogs.dogs.photos.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit opt-in only: uses the already signed-in test device, adds a synthetic image, then deletes it. */
class PhotoRepositoryTest {
    @Test fun uploadedPhotoSurvivesRepositoryRecreationAndCanBeDeleted() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("productionPhotoSmoke")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val uid=FirebaseAuth.getInstance().currentUser?.uid
        assumeTrue("기기에 로그인된 계정이 필요합니다",uid!=null)
        val gid=FirebaseFirestore.getInstance().document("users/$uid").get(Source.SERVER).awaitGroup().getString("active_group_id")
        assumeTrue("초기 설정이 완료된 그룹이 필요합니다",gid!=null)
        val repo=FirebasePhotoRepository(context,uid!!,gid!!)
        assumeTrue("사용자의 진행 중 업로드를 유지합니다",repo.pending()==null)
        val source=File.createTempFile("photo-smoke-",".jpg",context.cacheDir)
        val bitmap=Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(36,102,79))
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,80,it) };bitmap.recycle()
        var draft:PhotoDraft?=null
        var fresh:FirebasePhotoRepository?=null
        try {
            draft=repo.prepare(Uri.fromFile(source),null)
            android.util.Log.i("PhotoSmoke", "Uploading synthetic photo")
            repo.upload(draft) {}
            android.util.Log.i("PhotoSmoke", "Upload complete; reopening repository")
            assertNull(repo.pending());repo.close()
            fresh=FirebasePhotoRepository(context,uid,gid)
            val photo=fresh.list("").photos.first {it.id==draft.id}
            assertNull(photo.dog_id);assertEquals(uid,photo.uploader_id);assertEquals("ready",photo.status)
            android.util.Log.i("PhotoSmoke", "List loaded; downloading thumbnail and image")
            assertTrue(fresh.image(photo,true).length()>0)
            assertTrue(fresh.image(photo,false).length()>0)
            android.util.Log.i("PhotoSmoke", "Downloads complete; deleting photo")
            fresh.delete(photo.id)
            assertTrue(fresh.list("").photos.none {it.id==photo.id})
        } finally {
            try { if(draft!=null) (fresh ?: repo).discard(draft) } finally { fresh?.close();repo.close();source.delete() }
        }
    }
}
