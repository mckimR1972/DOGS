package com.teamdogs.dogs

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.firebase.firestore.DocumentSnapshot
import com.teamdogs.dogs.group.*
import com.teamdogs.dogs.photos.*
import com.teamdogs.dogs.ui.theme.DOGSTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

private class FakePhotos : PhotoRepository {
    var filter: String? = null
    override suspend fun list(filter: String?, cursor: DocumentSnapshot?, limit: Long): PhotoPage {
        this.filter=filter
        val all=listOf(Photo(id="common",group_id="g",uploader_id="other",status="ready"),Photo(id="dog",group_id="g",dog_id="dog1",uploader_id="other",status="ready"))
        return PhotoPage(all.filter { filter==null || it.dog_id==filter.ifEmpty {null} },null,false)
    }
    override suspend fun prepare(uri: Uri,dogId: String?)=error("unused")
    override fun pending(): PhotoDraft?=null
    override suspend fun upload(draft: PhotoDraft,progress:(Float)->Unit) {}
    override suspend fun discard(draft: PhotoDraft) {}
    override suspend fun delete(id:String) {}
    override suspend fun image(photo:Photo,thumbnail:Boolean):File=error("no fixture image")
    override fun close() {}
}
class PhotoScreenTest {
    @get:Rule val compose=createComposeRule()
    private fun group(leader:Boolean)=GroupSnapshot(User(id="me"),Group(id="g",leader_id=if(leader) "me" else "other"),emptyList(),listOf(Dog(id="dog1",name="콩이")),emptyList(),emptyList(),"2026-11-16")
    @Test fun commonFilterIsIndependentOfDogAndAllFilters() {
        val repo=FakePhotos();val model=PhotoViewModel(repo)
        compose.setContent { DOGSTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PhotoAlbum(model,group(false)) } } }
        compose.onNodeWithTag("photo-filter-common").performClick()
        compose.runOnIdle { assertEquals("",repo.filter);assertEquals(listOf("common"),model.state.value.photos.map{it.id}) }
        compose.onNodeWithText("콩이").performClick()
        compose.runOnIdle { assertEquals("dog1",repo.filter);assertEquals(listOf("dog"),model.state.value.photos.map{it.id}) }
        compose.onNodeWithText("전체").performClick()
        compose.runOnIdle { assertNull(repo.filter);assertEquals(2,model.state.value.photos.size) }
    }
    @Test fun memberCannotSeeDeleteForSomeoneElsesPhoto() {
        val model=PhotoViewModel(FakePhotos())
        compose.setContent { DOGSTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PhotoAlbum(model,group(false)) } } }
        compose.onNodeWithTag("photo-item-common").performScrollTo().performClick()
        compose.onNodeWithText("공통 사진").assertIsDisplayed()
        compose.onNodeWithText("삭제").assertDoesNotExist()
    }
    @Test fun leaderGetsDeleteConfirmation() {
        val model=PhotoViewModel(FakePhotos())
        compose.setContent { DOGSTheme { Column(Modifier.verticalScroll(rememberScrollState())) { PhotoAlbum(model,group(true)) } } }
        compose.onNodeWithTag("photo-item-common").performScrollTo().performClick()
        compose.onNodeWithText("삭제").performClick()
        compose.onNodeWithText("사진을 삭제할까요?").assertIsDisplayed()
    }
}
