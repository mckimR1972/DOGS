package com.teamdogs.dogs.prototype

import com.teamdogs.dogs.group.GroupSnapshot
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PrototypeApp(
    accountKey: String, demo: Boolean, defaultNickname: String,
    notificationsEnabled: Boolean, onRequestNotifications: () -> Unit,
    onTestNotification: () -> Unit, onExit: () -> Unit,
    suppliedController: CareController? = null,
    onSaveSetup: ((String, String, String, String, Set<String>) -> Boolean)? = null,
    setupSaving: Boolean = false, setupError: String? = null, cloudSetup: Boolean = false,
    cloudGroup: GroupSnapshot? = null, onToggleTask: ((String) -> Unit)? = null,
    onCreateTask: ((String, String, String) -> Boolean)? = null, onRefresh: (() -> Unit)? = null,
    onEditDog: ((String) -> Unit)? = null, cloudBusy: Boolean = false,
) {
    val context = LocalContext.current
    val controller = remember(accountKey) { suppliedController ?: CareController(LocalCareStore(context, accountKey), demo) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && cloudGroup == null) controller.refreshDay() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val state = controller.state
    val toggleTask = onToggleTask ?: controller::toggle
    val isLeader = cloudGroup == null || cloudGroup.group.leader_id == cloudGroup.user.id
    if (!state.ready && cloudGroup == null) {
        SetupScreen(state, defaultNickname, accountKey, notificationsEnabled, onRequestNotifications,
            onExit = onExit, onFinish = onSaveSetup ?: controller::completeSetup,
            saving = setupSaving, saveError = setupError, cloudSetup = cloudSetup)
        return
    }
    var tab by rememberSaveable(accountKey) { mutableIntStateOf(0) }
    var selectedPet by rememberSaveable(accountKey) { mutableStateOf("") }
    var addTask by remember { mutableStateOf(false) }
    var detailPhoto by remember { mutableStateOf<PetPhoto?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val pet = selectedPet.ifBlank { state.pets.firstOrNull()?.id ?: error("반려견을 먼저 등록해 주세요.") }
                controller.addPhoto(importLocalPhoto(context, uri, accountKey), pet)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { info = "사진을 가져오지 못했어요. 다른 사진으로 다시 시도해 주세요." }
            finally { importing = false }
        }
    }
    BackHandler(tab != 0) { tab = 0 }
    val tasks = state.tasks.filter { selectedPet.isEmpty() || it.petId == selectedPet }.sortedBy { it.time }
    val photos = state.photos.filter { selectedPet.isEmpty() || it.petId == selectedPet }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("홈", "할 일", "사진첩", "그룹").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { TabIcon(index) }, label = { Text(label) }, modifier = Modifier.testTag("tab-$index"))
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.groupName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(if (demo) "샘플 체험 · 이 기기에만 저장" else if (cloudGroup != null) "그룹 저장 · 사진은 기기 보관" else if (cloudSetup) "초기 설정 저장됨 · 일상 기록은 기기 보관" else "임시 화면 · 이 기기에만 저장",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onRefresh?.let { TextButton(onClick = it, enabled = !cloudBusy) { Text("새로고침") } }
                TextButton(onClick = onTestNotification) { Text("알림") }
            }
            if (tab != 3) PetFilters(state.pets, selectedPet) { selectedPet = it }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                when (tab) {
                    0 -> {
                        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("오늘 돌봄 ${tasks.size}개 중\n${tasks.count { it.done }}개를 마쳤어요",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("care-progress"))
                        Timeline(tasks)
                        val next = tasks.firstOrNull { !it.done }
                        if (next != null) Panel {
                            Text("${next.time} 예정", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(next.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("${state.pets.firstOrNull { it.id == next.petId }?.name.orEmpty()} · ${next.note.ifBlank { "오늘의 돌봄" }}")
                                }
                                PetBadge(state.pets.firstOrNull { it.id == next.petId }?.name.orEmpty())
                            }
                            Button(onClick = { toggleTask(next.id) }, modifier = Modifier.fillMaxWidth().testTag("complete-next")) {
                                Text("✓ 완료했어요")
                            }
                            Text(if (cloudGroup != null) "완료 취소는 완료한 사람 또는 그룹장이 할 수 있어요." else "완료 표시는 할 일 탭에서 되돌릴 수 있어요.", style = MaterialTheme.typography.labelSmall)
                        } else Panel { Text(if (tasks.isEmpty()) "첫 돌봄을 추가해 볼까요?" else "오늘의 돌봄을 모두 마쳤어요!") }
                        SectionTitle("남은 일 ${tasks.count { !it.done }}", "전체 일정 ›") { tab = 1 }
                        tasks.filter { !it.done }.take(3).forEach { TaskRow(it, state.pets, toggleTask) }
                        if (demo && state.requestAnswer.isBlank()) {
                            Text("받은 요청 · 샘플", fontWeight = FontWeight.Bold)
                            Panel(tinted = true) {
                                Text("지훈", fontWeight = FontWeight.Bold)
                                Text("오늘 야근이라 늦어요. 저녁 산책 부탁해요!")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { controller.answer("샘플 요청을 수락했어요.") }) { Text("제가 할게요") }
                                    OutlinedButton(onClick = { controller.answer("샘플 요청을 거절했어요.") }) { Text("어려워요") }
                                }
                            }
                        }
                        if (state.requestAnswer.isNotBlank()) Text(state.requestAnswer, color = MaterialTheme.colorScheme.primary)
                        Text("완료 ${tasks.count { it.done }}", fontWeight = FontWeight.Bold)
                        tasks.filter { it.done }.forEach { task ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("✓ ${task.title}", style = MaterialTheme.typography.bodyMedium)
                                Text(task.time, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        SectionTitle("최근 사진", "사진첩 ›") { tab = 2 }
                        PhotoGrid(photos.take(3), state.pets, demo, onClick = { detailPhoto = it })
                        HorizontalDivider()
                        Text("오늘의 돌봄 팁", fontWeight = FontWeight.Bold)
                        Text("완료한 돌봄은 바로 체크해 주세요. 가족이 같은 일을 두 번 하지 않도록 도와줘요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    1 -> {
                        SectionTitle("오늘 할 일", if (isLeader) "+ 추가" else "그룹장 관리") {
                            if (isLeader && !cloudBusy) addTask = true
                            else if (!isLeader) info = "할 일 생성·수정은 그룹장이 담당해요. 변경 제안 화면은 준비 중이에요."
                        }
                        Text("${tasks.count { it.done }} / ${tasks.size} 완료", color = MaterialTheme.colorScheme.primary)
                        if (tasks.isEmpty()) Panel { Text("등록된 할 일이 없어요. + 추가로 첫 돌봄을 만들어 보세요.") }
                        tasks.forEach { task -> Panel { TaskRow(task, state.pets, toggleTask) } }
                        Text(if (cloudGroup != null) "완료 기록은 날짜별로 그룹에 저장돼요. 다른 사람의 변경은 새로고침으로 확인하세요." else "임시 일정은 매일 같은 목록으로 표시돼요. 날짜가 바뀌면 완료 표시가 초기화됩니다.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    2 -> {
                        SectionTitle("우리의 사진첩", if (importing) "가져오는 중" else "+ 사진") {
                            if (!importing) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Text("선택한 사진은 이 기기에만 저장돼요. 전체 필터에서는 첫 번째 강아지의 사진으로 추가돼요.",
                            style = MaterialTheme.typography.bodySmall)
                        PhotoGrid(photos, state.pets, demo, onClick = { detailPhoto = it })
                    }
                    3 -> {
                        Text("함께 돌봐요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Panel {
                            Text(state.groupName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (demo) "샘플 가족 4명" else if (cloudGroup != null) "구성원 ${cloudGroup.members.size}명 · ${if (isLeader) "그룹장" else "멤버"}" else "보호자 1명 · 내가 그룹장")
                            val members = if (demo) listOf(state.nickname, "아빠", "엄마", "지훈") else cloudGroup?.members?.map { if (it.user_id == cloudGroup.user.id) state.nickname else it.user_id.take(6) }.orEmpty().ifEmpty { listOf(state.nickname) }
                            members.forEachIndexed { index, name ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PetBadge(name); Text(name); if ((cloudGroup == null && index == 0) || cloudGroup?.members?.getOrNull(index)?.role == "leader") Text("그룹장", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            OutlinedButton(onClick = { info = if (cloudGroup != null) "그룹 생성·소속 확인은 연결됐어요. 초대 코드 가입과 그룹장 위임은 다음 단계에서 연결합니다." else "가족 초대는 서버 연결 후 제공할 예정이에요." },
                                modifier = Modifier.fillMaxWidth()) { Text("가족 초대 안내") }
                        }
                        Panel {
                            Text("우리 강아지", fontWeight = FontWeight.Bold)
                            state.pets.forEach { pet -> Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (pet.photo.isNotBlank()) LocalPhoto(pet.photo, pet.name, Modifier.size(48.dp).clip(CircleShape)) else PetBadge(pet.name)
                                Text(pet.name)
                                onEditDog?.let { edit -> TextButton(onClick = { edit(pet.id) }) { Text("정보 수정") } } } }
                            if (cloudGroup == null) TextButton(onClick = controller::startSetup) { Text("초기 설정 다시 보기") }
                        }
                        Panel {
                            Text("돌봄 알림", fontWeight = FontWeight.Bold)
                            Text(if (notificationsEnabled) "알림이 허용되어 있어요." else "알림을 허용하면 돌봄 소식을 받을 수 있어요.")
                            OutlinedButton(onClick = onRequestNotifications) { Text("알림 권한 설정") }
                            TextButton(onClick = onTestNotification) { Text("테스트 알림 보내기") }
                            Text("현재는 테스트 알림만 지원해요. 예약·가족 알림은 서버 연결 후 추가됩니다.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text(if (demo) "샘플 체험 종료" else "로그아웃") }
                    }
                }
            }
        }
    }
    if (addTask) AddTaskDialog(state.pets, onDismiss = { addTask = false }, onAdd = onCreateTask ?: controller::addTask)
    info?.let { message -> AlertDialog(onDismissRequest = { info = null }, title = { Text("안내") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { info = null }) { Text("확인") } }) }
    detailPhoto?.let { photo -> AlertDialog(onDismissRequest = { detailPhoto = null },
        title = { Text(state.pets.firstOrNull { it.id == photo.petId }?.name ?: "사진") },
        text = { LocalPhoto(photo.path, "선택한 사진", Modifier.fillMaxWidth().height(320.dp), ContentScale.Fit) },
        confirmButton = { TextButton(onClick = { detailPhoto = null }) { Text("닫기") } }) }
}

@Composable private fun PetFilters(pets: List<Pet>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected.isBlank(), { onSelect("") }, label = { Text("전체") })
        pets.forEach { FilterChip(selected == it.id, { onSelect(it.id) }, label = { Text(it.name) }) }
    }
}
@Composable private fun Panel(tinted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(
        containerColor = if (tinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAction) { Text(action) }
    }
}
@Composable private fun PetBadge(name: String) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun TaskRow(task: CareTask, pets: List<Pet>, toggle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(task.time, style = MaterialTheme.typography.labelMedium)
        Column(Modifier.weight(1f)) {
            Text(task.title, fontWeight = FontWeight.SemiBold)
            Text(pets.firstOrNull { it.id == task.petId }?.name.orEmpty(), style = MaterialTheme.typography.labelSmall)
        }
        Checkbox(task.done, { toggle(task.id) }, modifier = Modifier.testTag("task-${task.id}"))
    }
}
@Composable private fun Timeline(tasks: List<CareTask>) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawLine(outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            tasks.forEach { task ->
                val parts = task.time.split(":")
                val hour = parts[0].toFloat() + parts[1].toFloat() / 60
                val center = Offset(((hour - 6) / 18).coerceIn(.02f, .98f) * size.width, size.height / 2)
                drawCircle(if (task.done) primary else outline, 5.dp.toPx(), center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("6시", "12시", "18시", "24시").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
@Composable private fun PhotoGrid(photos: List<PetPhoto>, pets: List<Pet>, demo: Boolean, onClick: (PetPhoto) -> Unit) {
    if (photos.isEmpty()) {
        if (demo) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Color(0xFFBCAB8F), Color(0xFFA2B7A9), Color(0xFFB3BDC7)).forEachIndexed { i, color ->
                Box(Modifier.weight(1f).height(126.dp).clip(RoundedCornerShape(14.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text("${pets.getOrNull(i % pets.size)?.name.orEmpty()}\n샘플 사진", color = Color(0xFF25342D))
                }
            }
        } else Panel { Text("아직 사진이 없어요. 첫 추억을 추가해 보세요.") }
    } else photos.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { photo ->
                LocalPhoto(photo.path, "강아지 사진", Modifier.weight(1f).aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp)).clickable { onClick(photo) })
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
@Composable fun LocalPhoto(path: String, description: String, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    if (bitmap != null) Image(bitmap!!, description, modifier, contentScale = scale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("사진") }
}
@Composable private fun TabIcon(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val stroke = 1.7.dp.toPx()
        when (index) {
            0 -> { drawLine(color, Offset(0f, h * .45f), Offset(w / 2, 0f), stroke)
                drawLine(color, Offset(w / 2, 0f), Offset(w, h * .45f), stroke)
                drawRect(color, Offset(w * .16f, h * .42f), androidx.compose.ui.geometry.Size(w * .68f, h * .55f), style = Stroke(stroke)) }
            1 -> { drawRect(color, style = Stroke(stroke)); drawLine(color, Offset(w * .2f, h * .5f), Offset(w * .43f, h * .7f), stroke)
                drawLine(color, Offset(w * .43f, h * .7f), Offset(w * .8f, h * .25f), stroke) }
            2 -> { drawRect(color, style = Stroke(stroke)); drawCircle(color, w * .1f, Offset(w * .3f, h * .3f))
                drawLine(color, Offset(w * .12f, h * .85f), Offset(w * .55f, h * .4f), stroke)
                drawLine(color, Offset(w * .55f, h * .4f), Offset(w * .9f, h * .85f), stroke) }
            else -> { drawCircle(color, w * .17f, Offset(w * .5f, h * .22f), style = Stroke(stroke))
                drawArc(color, 180f, 180f, false, Offset(w * .13f, h * .52f), androidx.compose.ui.geometry.Size(w * .74f, h * .8f), style = Stroke(stroke)) }
        }
    }
}

@Composable private fun AddTaskDialog(pets: List<Pet>, onDismiss: () -> Unit, onAdd: (String, String, String) -> Boolean) {
    var title by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("19:00") }
    var petId by rememberSaveable { mutableStateOf(pets.firstOrNull()?.id.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("할 일 추가") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(40) }, label = { Text("할 일 이름") }, singleLine = true)
            OutlinedTextField(time, { time = it.take(5) }, label = { Text("시간 (예: 19:30)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(petId.isBlank(), { petId = "" }); Text("그룹 공통")
            }
            pets.forEach { pet -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(petId == pet.id, { petId = pet.id }); Text(pet.name)
            } }
            if (error) Text("이름과 올바른 시간(HH:mm)을 입력해 주세요.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { if (onAdd(title, time, petId)) onDismiss() else error = true }) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable fun SetupScreen(state: CareState, defaultNickname: String, ownerKey: String,
    notificationsEnabled: Boolean, onRequestNotifications: () -> Unit, onExit: () -> Unit,
    onFinish: (String, String, String, String, Set<String>) -> Boolean,
    saving: Boolean = false, saveError: String? = null, cloudSetup: Boolean = false, groupSetup: Boolean = false,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var nickname by rememberSaveable { mutableStateOf(state.nickname.ifBlank { defaultNickname }) }
    var group by rememberSaveable { mutableStateOf(state.groupName) }
    var pet by rememberSaveable { mutableStateOf(state.pets.firstOrNull()?.name.orEmpty()) }
    var photo by rememberSaveable { mutableStateOf(state.pets.firstOrNull()?.photo.orEmpty()) }
    var starters by rememberSaveable { mutableStateOf(listOf("아침 급여", "산책", "물 갈아주기")) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try { photo = importLocalPhoto(context, uri, ownerKey); error = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "사진을 읽지 못했어요. 다시 선택해 주세요." }
            finally { importing = false }
        }
    }
    BackHandler { if (!saving) { if (step > 0) step-- else onExit() } }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(enabled = !saving, onClick = { if (step > 0) step-- else onExit() }) { Text("‹ 이전") }
            Text("DOGS · 처음 시작하기 ${step + 1}/4", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
            Text(listOf("어떻게 불러드릴까요?", "우리 강아지를 소개해 주세요", "매일의 돌봄을 골라 주세요", "돌봄 소식을 알려드릴까요?")[step],
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            when (step) {
                0 -> {
                    Text("혼자 시작해도 괜찮아요. 나를 그룹장으로 우리집을 만들어요.")
                    OutlinedTextField(nickname, { nickname = it.take(20) }, Modifier.fillMaxWidth().testTag("setup-nickname"), label = { Text("보호자 이름") }, singleLine = true)
                    OutlinedTextField(group, { group = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("그룹 이름") }, singleLine = true)
                    Text(if (groupSetup) "나를 그룹장으로 그룹과 반려견 정보를 저장해요. 초대 가입은 준비 중이에요." else if (cloudSetup) "초기 설정은 내 계정에 저장돼요. 가족 초대는 추후 연결됩니다." else "현재는 기기 안에서만 저장되는 임시 설정입니다. 가족 초대는 추후 연결됩니다.", style = MaterialTheme.typography.bodySmall)
                }
                1 -> {
                    if (photo.isNotBlank()) LocalPhoto(photo, "강아지 프로필", Modifier.size(140.dp).clip(CircleShape))
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !importing) {
                        Text(if (importing) "사진 가져오는 중…" else "프로필 사진 선택 (선택)")
                    }
                    if (cloudSetup) Text("사진은 아직 이 기기에만 보관돼요.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(pet, { pet = it.take(20) }, Modifier.fillMaxWidth().testTag("setup-pet"), label = { Text("강아지 이름") }, singleLine = true)
                }
                2 -> {
                    Text("원하는 것만 선택하세요. 나중에 할 일 탭에서 추가할 수 있어요.")
                    listOf("아침 급여", "산책", "물 갈아주기").forEach { title ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(title in starters, { starters = if (title in starters) starters - title else starters + title })
                            Text(title)
                        }
                    }
                }
                3 -> {
                    Text("할 일을 잊지 않도록 알림을 받을 수 있어요. 허용하지 않아도 앱을 사용할 수 있습니다.")
                    Text(if (notificationsEnabled) "알림이 허용되어 있어요." else "알림 받기는 선택 사항이에요.")
                    OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) { Text("알림 켜기") }
                    Text("현재 버전에서는 그룹 탭에서 테스트 알림을 보낼 수 있어요.", style = MaterialTheme.typography.bodySmall)
                }
            }
            (saveError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                when {
                    step == 0 && nickname.isBlank() -> error = "보호자 이름을 입력해 주세요."
                    step == 1 && pet.isBlank() -> error = "강아지 이름을 입력해 주세요."
                    step < 3 -> { error = null; step++ }
                    else -> if (!onFinish(nickname, group, pet, photo, starters.toSet())) error = "입력한 정보를 확인해 주세요."
                }
            }, enabled = !importing && !saving, modifier = Modifier.fillMaxWidth().testTag("setup-next")) {
                Text(if (saving) "저장 중…" else if (step == 3) "홈으로 시작하기" else "다음")
            }
        }
    }
}
