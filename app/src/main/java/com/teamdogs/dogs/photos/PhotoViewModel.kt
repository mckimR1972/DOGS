package com.teamdogs.dogs.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import com.teamdogs.dogs.group.Photo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhotoUiState(val photos: List<Photo> = emptyList(), val filter: String? = null,
    val loading: Boolean = false, val busy: Boolean = false, val progress: Float = 0f,
    val hasMore: Boolean = false, val draft: PhotoDraft? = null, val error: String? = null)
class PhotoViewModel(val repository: PhotoRepository) : ViewModel() {
    private val mutable=MutableStateFlow(PhotoUiState(draft=repository.pending()))
    val state=mutable.asStateFlow()
    private var cursor: DocumentSnapshot?=null
    private var queryJob: Job?=null
    init { refresh() }
    fun select(filter: String?) { mutable.value=mutable.value.copy(filter=filter,photos=emptyList(),hasMore=false); refresh() }
    fun refresh() = load(false)
    fun more() { if(mutable.value.hasMore && !mutable.value.loading) load(true) }
    private fun load(append: Boolean) {
        queryJob?.cancel()
        val filter=mutable.value.filter
        mutable.value=mutable.value.copy(loading=true,error=null)
        queryJob=viewModelScope.launch {
            try {
                val page=withTimeout(30_000) { repository.list(filter,if(append) cursor else null) }
                cursor=page.cursor
                mutable.value=mutable.value.copy(loading=false,photos=if(append) (mutable.value.photos+page.photos).distinctBy { it.id } else page.photos,hasMore=page.hasMore)
            } catch(e: CancellationException) { if(e is TimeoutCancellationException) fail(e, false) else throw e }
            catch(e: Exception) { fail(e, false) }
        }
    }
    fun prepare(uri: Uri, dogId: String?)=operate {
        val draft=repository.prepare(uri,dogId)
        mutable.value=mutable.value.copy(draft=draft)
        uploadDraft(draft)
    }
    fun upload() { val draft=mutable.value.draft ?: return; operate { uploadDraft(draft) } }
    private suspend fun uploadDraft(draft: PhotoDraft) {
        repository.upload(draft) { mutable.value=mutable.value.copy(progress=it) }
        mutable.value=mutable.value.copy(draft=null); refresh()
    }
    fun discard() { val draft=mutable.value.draft ?: return; operate { repository.discard(draft); mutable.value=mutable.value.copy(draft=null); refresh() } }
    fun delete(id: String)=operate { repository.delete(id); mutable.value=mutable.value.copy(photos=mutable.value.photos.filterNot { it.id==id }); refresh() }
    private fun operate(action: suspend ()->Unit) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,error=null,progress=0f)
        viewModelScope.launch {
            try { withTimeout(150_000) { action() }; mutable.value=mutable.value.copy(busy=false) }
            catch(e: CancellationException) { if(e is TimeoutCancellationException) fail(e) else throw e }
            catch(e: Exception) { fail(e) }
        }
    }
    private fun fail(e: Exception, clearBusy: Boolean = true) {
        val denied=when(e) {
            is FirebaseFirestoreException -> e.code==FirebaseFirestoreException.Code.PERMISSION_DENIED
            is FirebaseFunctionsException -> e.code==FirebaseFunctionsException.Code.PERMISSION_DENIED || e.code==FirebaseFunctionsException.Code.UNAUTHENTICATED
            is StorageException -> e.errorCode==StorageException.ERROR_NOT_AUTHORIZED || e.errorCode==StorageException.ERROR_NOT_AUTHENTICATED
            else -> false
        }
        val message=if(denied) "그룹 접근 권한이 없어요. 로그인과 그룹 소속을 확인해 주세요." else when(e) {
            is IllegalArgumentException -> e.message ?: "사진을 확인해 주세요."
            else -> "사진 작업을 완료하지 못했어요. 연결을 확인하고 다시 시도해 주세요."
        }
        mutable.value=mutable.value.copy(loading=false,busy=if(clearBusy) false else mutable.value.busy,error=message,photos=if(denied) emptyList() else mutable.value.photos)
    }
    override fun onCleared() { repository.close() }
}
