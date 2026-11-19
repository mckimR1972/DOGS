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
import com.teamdogs.dogs.prototype.PhotoViewer
import com.teamdogs.dogs.ui.components.*
import com.teamdogs.dogs.ui.theme.DogsTheme
import androidx.compose.foundation.shape.CircleShape
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
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("우리의 사진첩",style=MaterialTheme.typography.titleMedium)
            Text("가족의 소중한 순간을 모아 보세요.",style=MaterialTheme.typography.bodySmall,color=DogsTheme.colors.textTertiary)
        }
        IconButton(onClick=model::refresh,enabled=!ui.loading) { Icon(DogsIcons.Refresh,"새로고침") }
        Button(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
            enabled=!ui.busy && ui.draft==null,modifier=Modifier.heightIn(min=44.dp).testTag("photo-add"),shape=CircleShape,
            contentPadding=PaddingValues(horizontal=16.dp)) {
            Icon(DogsIcons.Plus,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("사진 올리기",style=MaterialTheme.typography.labelLarge)
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DogChip("전체",ui.filter==null,{model.select(null)})
        DogChip("공통",ui.filter=="",{model.select("")},Modifier.testTag("photo-filter-common"))
        group.dogs.forEach { dog->DogChip(dog.name,ui.filter==dog.id,{model.select(dog.id)},dogId=dog.id) }
    }
    if(ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary,trackColor=DogsTheme.colors.divider)
    ui.error?.let { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(DogsIcons.Info,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.error)
        Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) } }
    ui.draft?.let { draft->
        FeatureCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                AsyncImage(draft.file,"업로드할 사진",Modifier.size(72.dp).clip(MaterialTheme.shapes.small),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(group.dogs.find { it.id==draft.dogId }?.name ?: "공통",style=MaterialTheme.typography.titleSmall)
                    if(ui.busy) {
                        LinearProgressIndicator(progress={ui.progress},modifier=Modifier.fillMaxWidth(),
                            color=MaterialTheme.colorScheme.primary,trackColor=DogsTheme.colors.divider,drawStopIndicator={})
                        Text(if(ui.progress>=.9f) "사진을 정리하고 있어요…" else "올리는 중 ${(ui.progress*100).toInt()}%",
                            style=MaterialTheme.typography.labelMedium,color=DogsTheme.colors.textTertiary)
                    } else Text("올리지 못했어요. 다시 시도하거나 취소해 주세요.",style=MaterialTheme.typography.labelMedium,color=DogsTheme.colors.attention)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                InverseButton("업로드 다시 시도",model::upload,Modifier.testTag("photo-upload"),enabled=!ui.busy)
                SecondaryButton("업로드 취소",model::discard,enabled=!ui.busy,height=40.dp)
            }
        }
    }
    if(ui.busy && ui.draft==null) LinearProgressIndicator(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary,trackColor=DogsTheme.colors.divider)
    if(!ui.loading && ui.photos.isEmpty()) Text("아직 사진이 없어요. 첫 추억을 올려 보세요.",style=MaterialTheme.typography.bodyMedium,color=DogsTheme.colors.textTertiary)
    PhotoRows(ui.photos,model.repository) { detail=it }
    if(ui.hasMore) SecondaryButton("사진 더 보기",model::more,Modifier.fillMaxWidth(),enabled=!ui.loading,height=44.dp)
    selectedUri?.let { uri->AlertDialog(
        onDismissRequest={selectedUri=null},
        containerColor=MaterialTheme.colorScheme.background,
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
                    DogChip("공통",tag==null,{tag=null})
                    group.dogs.forEach { dog->DogChip(dog.name,tag==dog.id,{tag=if(tag==dog.id) null else dog.id},dogId=dog.id) }
                }
            }
        },
        confirmButton={PrimaryButton("업로드",{model.prepare(uri,tag);selectedUri=null})},
        dismissButton={TextButton(onClick={selectedUri=null}){Text("취소")}}
    ) }
    detail?.let { photo->
        val canDelete=photo.uploader_id==group.user.id || group.group.leader_id==group.user.id
        PhotoViewer(group.dogs.find { it.id==photo.dog_id }?.name ?: "공통 사진",onClose={detail=null},
            subtitle=photo.created_at?.toDate()?.toInstant()?.atZone(java.time.ZoneId.of(group.group.timezone))
                ?.format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 a h:mm",java.util.Locale.KOREAN)),
            actions={ if(canDelete) TextButton(onClick={deleting=photo;detail=null},enabled=!ui.busy,
                colors=ButtonDefaults.textButtonColors(contentColor=DogsTheme.colors.onViewer)) {
                Icon(DogsIcons.Trash,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("삭제") } }) {
            CloudPhoto(photo,model.repository,false,Modifier.fillMaxWidth().aspectRatio(1f))
        }
    }
    deleting?.let { photo->AlertDialog(onDismissRequest={deleting=null},title={Text("사진을 삭제할까요?")},text={Text("그룹 사진첩에서 삭제되며 가족 모두 더 이상 볼 수 없어요.")},confirmButton={TextButton(onClick={model.delete(photo.id);deleting=null},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("삭제")}},dismissButton={TextButton(onClick={deleting=null}){Text("취소")}}) }
}
@Composable fun RecentPhotos(model: PhotoViewModel, group: GroupSnapshot, dogId: String?, onOpen: ()->Unit) {
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(model,dogId,group) {
        photos=emptyList(); error=false
        try { photos=model.repository.list(dogId,limit=3).photos }
        catch(e: CancellationException){throw e} catch(_:Exception){error=true}
    }
    if(error) Text("최근 사진을 불러오지 못했어요. 사진첩에서 다시 확인해 주세요.",style=MaterialTheme.typography.bodyMedium,color=DogsTheme.colors.textTertiary)
    else if(photos.isEmpty()) Text("사진첩에 가족의 추억을 올려 보세요.",style=MaterialTheme.typography.bodyMedium,color=DogsTheme.colors.textTertiary)
    PhotoRows(photos,model.repository) {onOpen()}
}
@Composable private fun PhotoRows(photos: List<Photo>, repository: PhotoRepository, onClick: (Photo)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { photos.chunked(3).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        row.forEach { photo->Column(Modifier.weight(1f).testTag("photo-item-${photo.id}").clickable(onClickLabel="사진 크게 보기") {onClick(photo)}) {
            CloudPhoto(photo,repository,true,Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small))
        } }
        repeat(3-row.size){Spacer(Modifier.weight(1f))}
    } } }
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
    else Box(modifier.background(DogsTheme.colors.photoPlaceholder),contentAlignment=Alignment.Center) {
        // In the grid the whole tile opens the viewer, so a failed thumbnail shows a passive icon; retry lives in the viewer.
        if(failed && thumbnail) Icon(DogsIcons.Paw,"사진을 불러오지 못했어요",Modifier.size(24.dp),tint=DogsTheme.colors.textTertiary)
        else if(failed) TextButton(onClick={retry++}) {Text("다시 불러오기")}
        else CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp,color=DogsTheme.colors.textTertiary)
    }
}
