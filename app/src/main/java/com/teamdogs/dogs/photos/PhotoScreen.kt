package com.teamdogs.dogs.photos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.teamdogs.dogs.group.GroupSnapshot
import com.teamdogs.dogs.group.Photo
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable fun rememberPhotoModel(uid: String, groupId: String): PhotoViewModel {
    val context=LocalContext.current.applicationContext
    val store=remember(uid,groupId) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    return remember(store) { ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T =
            PhotoViewModel(FirebasePhotoRepository(context,uid,groupId)) as T
    })[PhotoViewModel::class.java] }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PhotoAlbum(model: PhotoViewModel, group: GroupSnapshot) {
    val ui by model.state.collectAsStateWithLifecycle()
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,model) {
        val observer=LifecycleEventObserver { _,e->if(e==Lifecycle.Event.ON_RESUME) model.refresh() }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model) { model.refresh() }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var tag by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Photo?>(null) }
    var deleting by remember { mutableStateOf<Photo?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri->selectedUri=uri;tag=null }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text("우리의 사진첩",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        TextButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!ui.busy && ui.draft==null,modifier=Modifier.testTag("photo-add")) { Text("+ 사진") }
    }
    Text("가족의 소중한 순간을 모아 보세요.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(ui.filter==null,{model.select(null)},label={Text("전체")},shape=RoundedCornerShape(50))
            FilterChip(ui.filter=="",{model.select("")},label={Text("공통")},modifier=Modifier.testTag("photo-filter-common"),shape=RoundedCornerShape(50))
            group.dogs.forEach { dog->FilterChip(ui.filter==dog.id,{model.select(dog.id)},label={Text(dog.name)},shape=RoundedCornerShape(50)) }
        }
        TextButton(onClick=model::refresh,enabled=!ui.loading,contentPadding=PaddingValues(horizontal=4.dp)) { Text("새로고침") }
    }
    if(ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    ui.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    ui.draft?.let { draft->
        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            AsyncImage(draft.file,"업로드할 사진",Modifier.fillMaxWidth().height(160.dp),contentScale=ContentScale.Fit)
            Text(group.dogs.find { it.id==draft.dogId }?.name ?: "공통")
            if(ui.busy) { LinearProgressIndicator(progress={ui.progress},modifier=Modifier.fillMaxWidth()); Text(if(ui.progress>=.9f) "사진을 정리하고 있어요…" else "업로드 중 ${(ui.progress*100).toInt()}%") }
            Row {
                Button(onClick=model::upload,enabled=!ui.busy,modifier=Modifier.testTag("photo-upload")) { Text("업로드 다시 시도") }
                TextButton(onClick=model::discard,enabled=!ui.busy) { Text("업로드 취소") }
            }
        } }
    }
    if(ui.busy && ui.draft==null) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(!ui.loading && ui.photos.isEmpty()) Text("아직 사진이 없어요. 첫 추억을 올려 보세요.")
    PhotoRows(ui.photos,model.repository) { detail=it }
    if(ui.hasMore) TextButton(onClick=model::more,enabled=!ui.loading) { Text("사진 더 보기") }
    selectedUri?.let { uri->AlertDialog(
        onDismissRequest={selectedUri=null},
        containerColor=MaterialTheme.colorScheme.surface,
        tonalElevation=0.dp,
        shape=RoundedCornerShape(28.dp),
        title={Text("사진 올리기",fontWeight=FontWeight.SemiBold)},
        text={
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                    AsyncImage(uri,"선택한 사진",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                }
                Spacer(Modifier.height(24.dp))
                Text("반려견 태그",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,
                    color=MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("선택하지 않으면 공통 사진으로 저장돼요.",style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected=tag==null,onClick={tag=null},label={Text("공통")},shape=RoundedCornerShape(50))
                    group.dogs.forEach { dog->
                        FilterChip(selected=tag==dog.id,onClick={tag=if(tag==dog.id) null else dog.id},
                            label={Text(dog.name)},shape=RoundedCornerShape(50))
                    }
                }
            }
        },
        confirmButton={Button(onClick={model.prepare(uri,tag);selectedUri=null},shape=RoundedCornerShape(50),
            contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp)) { Text("업로드") }},
        dismissButton={TextButton(onClick={selectedUri=null}){Text("취소")}}
    ) }
    detail?.let { photo->AlertDialog(onDismissRequest={detail=null},title={Text(group.dogs.find { it.id==photo.dog_id }?.name ?: "공통 사진")},text={
        CloudPhoto(photo,model.repository,false,Modifier.fillMaxWidth().height(330.dp))
    },confirmButton={TextButton(onClick={detail=null}){Text("닫기")}},dismissButton={
        if(photo.uploader_id==group.user.id || group.group.leader_id==group.user.id)
            TextButton(onClick={deleting=photo;detail=null},enabled=!ui.busy){Text("삭제")}
    }) }
    deleting?.let { photo->AlertDialog(onDismissRequest={deleting=null},title={Text("사진을 삭제할까요?")},text={Text("그룹 사진첩에서 삭제되며 가족 모두 더 이상 볼 수 없어요.")},confirmButton={TextButton(onClick={model.delete(photo.id);deleting=null}){Text("삭제")}},dismissButton={TextButton(onClick={deleting=null}){Text("취소")}}) }
}
@Composable fun RecentPhotos(model: PhotoViewModel, group: GroupSnapshot, dogId: String?, onOpen: ()->Unit) {
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(model,dogId,group) {
        photos=emptyList(); error=false
        try { photos=model.repository.list(dogId,limit=3).photos }
        catch(e: CancellationException){throw e} catch(_:Exception){error=true}
    }
    if(error) Text("최근 사진을 불러오지 못했어요. 사진첩에서 다시 확인해 주세요.")
    else if(photos.isEmpty()) Text("사진첩에 가족의 추억을 올려 보세요.")
    PhotoRows(photos,model.repository) {onOpen()}
}
@Composable private fun PhotoRows(photos: List<Photo>, repository: PhotoRepository, onClick: (Photo)->Unit) {
    photos.chunked(3).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        row.forEach { photo->Column(Modifier.weight(1f).testTag("photo-item-${photo.id}").clickable {onClick(photo)}) {
            CloudPhoto(photo,repository,true,Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
        } }
        repeat(3-row.size){Spacer(Modifier.weight(1f))}
    } }
}
@Composable private fun CloudPhoto(photo: Photo, repository: PhotoRepository, thumbnail: Boolean, modifier: Modifier) {
    var file by remember(photo.id,repository,thumbnail) { mutableStateOf<File?>(null) }
    var failed by remember(photo.id,repository,thumbnail) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(photo.id,repository,thumbnail,retry) {
        failed=false;file=null
        try { file=repository.image(photo,thumbnail) }
        catch(e:CancellationException){throw e} catch(_:Exception){failed=true}
    }
    if(file!=null) AsyncImage(file,"반려견 사진",modifier,contentScale=if(thumbnail) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if(failed) TextButton(onClick={retry++}) {Text("다시 불러오기")} else CircularProgressIndicator(Modifier.size(24.dp))
    }
}
