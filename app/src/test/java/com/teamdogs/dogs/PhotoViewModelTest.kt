package com.teamdogs.dogs

import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.teamdogs.dogs.group.Photo
import com.teamdogs.dogs.photos.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    private class FakeRepo:PhotoRepository {
        var draft:PhotoDraft?=PhotoDraft("same-id",null,File("/tmp/photo-test.jpg"))
        var failUpload=true
        val ids=mutableListOf<String>()
        override suspend fun list(filter:String?,cursor:DocumentSnapshot?,limit:Long):PhotoPage {delay(if(filter=="slow") 100 else 1);return PhotoPage(listOf(Photo(id=filter ?: "all")),null,false)}
        override suspend fun prepare(uri:Uri,dogId:String?)=error("unused")
        override fun pending()=draft
        override suspend fun upload(draft:PhotoDraft,progress:(Float)->Unit){ids+=draft.id;progress(.5f);if(failUpload) error("network");this.draft=null}
        override suspend fun discard(draft:PhotoDraft){this.draft=null}
        override suspend fun delete(id:String){}
        override suspend fun image(photo:Photo,thumbnail:Boolean)=File("unused")
        override fun close(){}
    }
    @Test fun interruptedUploadRetainsSameDraftForRetry()=runTest(dispatcher){
        val repo=FakeRepo();val model=PhotoViewModel(repo);advanceUntilIdle()
        model.upload();advanceUntilIdle()
        assertEquals("same-id",model.state.value.draft?.id);assertNotNull(model.state.value.error);assertFalse(model.state.value.busy)
        repo.failUpload=false;model.upload();advanceUntilIdle()
        assertEquals(listOf("same-id","same-id"),repo.ids);assertNull(model.state.value.draft)
    }
    @Test fun rapidFilterSwitchDoesNotRestoreOldResults()=runTest(dispatcher){
        val model=PhotoViewModel(FakeRepo());advanceUntilIdle()
        model.select("slow");runCurrent();model.select("");advanceUntilIdle()
        assertEquals("",model.state.value.filter);assertEquals("",model.state.value.photos.single().id)
    }
    @Test fun doubleUploadTapStartsOneRequest()=runTest(dispatcher){
        val repo=FakeRepo();val model=PhotoViewModel(repo);advanceUntilIdle()
        model.upload();model.upload();advanceUntilIdle();assertEquals(listOf("same-id"),repo.ids)
    }
}
