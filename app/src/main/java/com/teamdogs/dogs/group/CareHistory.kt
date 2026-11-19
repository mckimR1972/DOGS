package com.teamdogs.dogs.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.ui.components.*
import com.teamdogs.dogs.ui.theme.DogsTheme
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

data class CareHistoryState(val day:String, val items:List<CareHistoryItem> = emptyList(), val loading:Boolean=false,
    val saving:Boolean=false, val error:String?=null)
class CareHistoryViewModel(private val repository:GroupRepository, private val group:Group) : ViewModel() {
    private val mutable=MutableStateFlow(CareHistoryState(group.today()))
    val state=mutable.asStateFlow()
    private var readJob:Job?=null
    init { refresh() }
    fun selectDay(day:String) { LocalDate.parse(day); mutable.value=mutable.value.copy(day=day,items=emptyList());refresh() }
    fun refresh() {
        readJob?.cancel();val day=mutable.value.day
        mutable.value=mutable.value.copy(loading=true,error=null)
        readJob=viewModelScope.launch {
            try { val items=withTimeout(25_000){repository.careHistory(group.id,day,group.timezone)}
                mutable.value=mutable.value.copy(items=items,loading=false)
            } catch(e:CancellationException) { if(e is TimeoutCancellationException) readError() else throw e }
            catch(_:Exception){readError()}
        }
    }
    private fun readError(){mutable.value=mutable.value.copy(items=emptyList(),loading=false,error="돌봄 기록을 불러오지 못했어요. 새로고침해 주세요.")}
    fun save(record:CareRecord,onSuccess:()->Unit)=write {
        repository.saveCareRecord(record)
        val day=record.done_at!!.toDate().toInstant().atZone(ZoneId.of(group.timezone)).toLocalDate().toString()
        selectDay(day);onSuccess()
    }
    fun remove(item:CareHistoryItem,onSuccess:()->Unit)=write {
        if(item.todoId==null) repository.deleteCareRecord(group.id,item.id)
        else repository.setCompleted(group.id,item.todoId,item.day,false)
        refresh();onSuccess()
    }
    private fun write(action:suspend()->Unit) {
        if(mutable.value.saving)return
        mutable.value=mutable.value.copy(saving=true,error=null)
        viewModelScope.launch {
            try {withTimeout(25_000){action()};mutable.value=mutable.value.copy(saving=false)}
            catch(e:CancellationException){if(e is TimeoutCancellationException) writeError() else throw e}
            catch(_:Exception){writeError()}
        }
    }
    private fun writeError(){mutable.value=mutable.value.copy(saving=false,error="저장을 확인하지 못했어요. 권한과 연결을 확인한 뒤 다시 시도해 주세요.")}
}
@Composable fun CareHistorySection(account:SignedInUser,snapshot:GroupSnapshot,petId:String,compact:Boolean,onChanged:()->Unit) {
    val store=remember(account.uid,snapshot.group.id,compact){ViewModelStore()}
    DisposableEffect(store){onDispose{store.clear()}}
    val model=remember(store){ViewModelProvider(store,object:ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T =
            CareHistoryViewModel(FirestoreGroupRepository(account),snapshot.group) as T
    })[CareHistoryViewModel::class.java]}
    val ui by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(snapshot){if(compact)model.selectDay(snapshot.group.today()) else model.refresh()}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,model){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)model.refresh()}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    var adding by remember{mutableStateOf(false)}
    var remove by remember{mutableStateOf<CareHistoryItem?>(null)}
    var chooseDay by remember{mutableStateOf(false)}
    val items=ui.items.filter{petId.isBlank() || it.dogId==petId}
    val zone=ZoneId.of(snapshot.group.timezone)
    SectionHeader(if(compact) "오늘 돌봄 기록" else "돌봄 기록", count=items.size.takeIf{!ui.loading},
        action=if(compact) null else "이미 한 일 기록", onAction=if(compact) null else ({adding=true}), actionEnabled=!ui.saving)
    if(!compact) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        IconButton(onClick={model.selectDay(LocalDate.parse(ui.day).minusDays(1).toString())}){Icon(DogsIcons.ChevronLeft,"이전 날짜")}
        TextButton(onClick={chooseDay=true},modifier=Modifier.weight(1f)){
            val d=LocalDate.parse(ui.day)
            Text((if(ui.day==snapshot.group.today()) "오늘, " else "")+d.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",java.util.Locale.KOREAN)),
                style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick={model.selectDay(LocalDate.parse(ui.day).plusDays(1).toString())},enabled=ui.day<snapshot.group.today()){Icon(DogsIcons.ChevronRight,"다음 날짜")}
    }
    if(ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary,trackColor=DogsTheme.colors.divider)
    ui.error?.let{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Icon(DogsIcons.Info,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.error)
        Text(it,Modifier.weight(1f),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        TextLink("다시 불러오기",model::refresh)}}
    if(!ui.loading && items.isEmpty())Text(if(compact)"아직 완료한 돌봄이 없어요." else "이 날짜에 남긴 돌봄 기록이 없어요.",
        style=MaterialTheme.typography.bodyMedium,color=DogsTheme.colors.textTertiary)
    Column{(if(compact)items.take(3) else items).forEachIndexed{i,item->
        if(i>0) ListDivider()
        val actor=if(item.actor==account.uid) snapshot.user.name else "구성원 ${item.actor.take(6)}"
        val dog=snapshot.dogs.find{it.id==item.dogId}?.name ?: if(item.dogId==null)"공통" else "반려견"
        Row(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)){
            PersonAvatar(actor,32.dp,highlighted=true)
            Column(Modifier.weight(1f)){
                Text(item.title,style=MaterialTheme.typography.bodyLarge,maxLines=2)
                Text("$actor, $dog",style=MaterialTheme.typography.labelMedium,color=DogsTheme.colors.textTertiary,maxLines=1)
            }
            Column(horizontalAlignment=Alignment.End){
                Text(item.performedAt.toDate().toInstant().atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
                    style=MaterialTheme.typography.labelMedium,color=DogsTheme.colors.textTertiary)
                if(!compact && (item.actor==account.uid || snapshot.group.leader_id==account.uid))
                    TextLink(if(item.todoId==null)"기록 삭제" else "완료 취소",{remove=item},enabled=!ui.saving,
                        color=MaterialTheme.colorScheme.error)
            }
        }
    }}
    if(adding) CareRecordDialog(snapshot,account.uid,petId,ui.saving,ui.error,onDismiss={adding=false},onSave={record->
        model.save(record){adding=false;onChanged()}
    })
    remove?.let{item->AlertDialog(onDismissRequest={if(!ui.saving)remove=null},title={Text(if(item.todoId==null)"기록을 삭제할까요?" else "완료를 취소할까요?")},
        text={Column{Text("${item.title} 기록이 돌봄 기록에서 사라져요.");ui.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},
        confirmButton={TextButton(onClick={model.remove(item){remove=null;onChanged()}},enabled=!ui.saving,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("확인")}},
        dismissButton={TextButton(onClick={remove=null},enabled=!ui.saving){Text("취소")}})}
    if(chooseDay){var day by remember{mutableStateOf(ui.day)};var invalid by remember{mutableStateOf(false)}
        AlertDialog(onDismissRequest={chooseDay=false},title={Text("돌봄 기록 날짜")},text={Column{
            OutlinedTextField(day,{day=it.take(10)},label={Text("날짜 YYYY-MM-DD")},singleLine=true,shape=MaterialTheme.shapes.medium)
            if(invalid)Text("오늘 또는 이전 날짜를 입력해 주세요.")
        }},confirmButton={TextButton(onClick={val parsed=runCatching{LocalDate.parse(day)}.getOrNull()
            if(parsed==null || parsed.year<1 || parsed>LocalDate.parse(snapshot.group.today()))invalid=true else{model.selectDay(day);chooseDay=false}}){Text("보기")}},
            dismissButton={TextButton(onClick={chooseDay=false}){Text("취소")}})
    }
}
@Composable internal fun CareRecordDialog(group:GroupSnapshot,uid:String,selectedDog:String,saving:Boolean,serverError:String?,onDismiss:()->Unit,onSave:(CareRecord)->Unit){
    val now=remember{LocalDateTime.now(ZoneId.of(group.group.timezone))}
    val id=rememberSaveable{UUID.randomUUID().toString()}
    var title by rememberSaveable{mutableStateOf("")}
    var dog by rememberSaveable{mutableStateOf(selectedDog)}
    var day by rememberSaveable{mutableStateOf(now.toLocalDate().toString())}
    var time by rememberSaveable{mutableStateOf(now.format(DateTimeFormatter.ofPattern("HH:mm")))}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},containerColor=MaterialTheme.colorScheme.background,title={Text("이미 한 일 기록")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("실제로 돌본 시각을 남겨 주세요. 기본값은 지금이에요.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(title,{title=it.take(100)},label={Text("어떤 돌봄을 했나요?")},singleLine=true,shape=MaterialTheme.shapes.medium,enabled=!saving)
            OutlinedTextField(day,{day=it.take(10)},label={Text("한 날짜 YYYY-MM-DD")},singleLine=true,shape=MaterialTheme.shapes.medium,enabled=!saving)
            OutlinedTextField(time,{time=it.take(5)},label={Text("한 시각 HH:mm")},singleLine=true,shape=MaterialTheme.shapes.medium,enabled=!saving)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                DogChip("공통",dog.isBlank(),{if(!saving)dog=""})
                group.dogs.forEach{d->DogChip(d.name,dog==d.id,{if(!saving)dog=d.id},dogId=d.id)}
            }
            (error ?: serverError)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={Button(enabled=!saving,shape=androidx.compose.foundation.shape.CircleShape,onClick={
        val instant=runCatching{LocalDate.parse(day).also { require(it.year in 1..9999) }.atTime(LocalTime.parse(time)).atZone(ZoneId.of(group.group.timezone)).toInstant()}.getOrNull()
        if(title.isBlank() || instant==null || instant>Instant.now())error="내용과 실제로 한 날짜·시각을 확인해 주세요. 미래 시각은 기록할 수 없어요."
        else{error=null;onSave(CareRecord(id,group.group.id,title.trim(),dog.ifBlank{null},uid,Timestamp(Date.from(instant))))}
    }){Text(if(saving)"저장 중…" else "기록하기")}},dismissButton={TextButton(onClick=onDismiss,enabled=!saving){Text("취소")}})
}
