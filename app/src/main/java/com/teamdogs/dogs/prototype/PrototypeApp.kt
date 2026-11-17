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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.teamdogs.dogs.ui.components.*
import com.teamdogs.dogs.ui.theme.DogsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TwoColumnWidth = 600.dp

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
    albumContent: (@Composable () -> Unit)? = null,
    recentPhotosContent: (@Composable (String, () -> Unit) -> Unit)? = null,
    careHistoryContent: (@Composable (String, Boolean) -> Unit)? = null,
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
    // Minute ticker so late labels and the "now" marker stay current while the app is open.
    // Cloud groups judge "late" in the group's timezone so every member sees the same state; local mode uses the device.
    val zone = remember(cloudGroup?.group?.timezone) {
        cloudGroup?.group?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }
    fun minutesNow() = LocalTime.now(zone).let { it.hour * 60 + it.minute }
    var nowMinutes by remember(zone) { mutableIntStateOf(minutesNow()) }
    LaunchedEffect(zone) { while (true) { nowMinutes = minutesNow(); delay(60_000) } }

    BackHandler(tab != 0) { tab = 0 }
    val tasks = state.tasks.filter { selectedPet.isEmpty() || it.petId == selectedPet }
        .sortedWith(compareBy<CareTask> { it.time.isBlank() }.thenBy { it.time })
    val photos = state.photos.filter { selectedPet.isEmpty() || it.petId == selectedPet }
    val petName = { id: String -> state.pets.firstOrNull { it.id == id }?.name }
    val isLate = { task: CareTask -> !task.done && (parseMinutes(task.time)?.let { it < nowMinutes } ?: false) }
    val subtitle = when {
        demo -> "샘플 체험"
        cloudGroup != null -> "${cloudGroup.members.size}명"
        else -> "이 기기에만 저장"
    }
    val showPetFilters = tab != 3 && !(tab == 2 && albumContent != null)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                listOf("홈" to DogsIcons.Home, "할 일" to DogsIcons.Todo, "사진첩" to DogsIcons.Photo, "그룹" to DogsIcons.Group)
                    .forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icon, null) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = DogsTheme.colors.doneContainer,
                                selectedIconColor = DogsTheme.colors.onDoneContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = DogsTheme.colors.textTertiary,
                                unselectedTextColor = DogsTheme.colors.textTertiary),
                            modifier = Modifier.testTag("tab-$index"))
                    }
            }
        },
        floatingActionButton = {
            when {
                tab == 1 && isLeader -> ExtendedFloatingActionButton(onClick = { if (!cloudBusy) addTask = true },
                    icon = { Icon(DogsIcons.Plus, null) }, text = { Text("할 일 추가", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                tab == 2 && albumContent == null -> ExtendedFloatingActionButton(onClick = {
                    if (!importing) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, icon = { Icon(DogsIcons.Plus, null) },
                    text = { Text(if (importing) "가져오는 중" else "사진 올리기", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            TabTopBar(if (tab == 0) state.groupName else listOf("홈", "할 일", "사진첩", "그룹")[tab],
                subtitle = if (tab == 0) subtitle else null) {
                onRefresh?.let { BadgeIconButton(DogsIcons.Refresh, "새로고침", it, enabled = !cloudBusy) }
                BadgeIconButton(DogsIcons.Bell, "테스트 알림 보내기", onTestNotification)
            }
            if (showPetFilters) PetFilters(state.pets, selectedPet) { selectedPet = it }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= TwoColumnWidth
                when (tab) {
                    0 -> HomeTab(wide, state, tasks, photos, demo, nowMinutes, zone, isLate, petName, toggleTask, cloudGroup != null,
                        selectedPet, careHistoryContent, recentPhotosContent, controller,
                        onOpenTodo = { tab = 1 }, onOpenPhotos = { tab = 2 }, onPhoto = { detailPhoto = it })
                    1 -> TodoTab(wide, tasks, isLeader, cloudGroup != null, isLate, petName, toggleTask, selectedPet, careHistoryContent)
                    2 -> ScrollColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter)) {
                        if (albumContent != null) albumContent() else {
                            Text("우리의 사진첩", style = MaterialTheme.typography.titleMedium)
                            Text("선택한 사진은 이 기기에만 저장돼요. 전체 필터에서는 첫 번째 반려견의 사진으로 추가돼요.",
                                style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
                            PhotoGrid(photos, state.pets, demo, onClick = { detailPhoto = it })
                        }
                        Spacer(Modifier.height(72.dp))
                    }
                    3 -> ScrollColumn(Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter)) {
                        GroupTab(state, demo, cloudGroup, isLeader, notificationsEnabled, onRequestNotifications,
                            onTestNotification, onEditDog, controller, onExit,
                            onInvite = { info = if (cloudGroup != null) "초대 코드 가입과 그룹장 위임은 다음 단계에서 연결돼요." else "가족 초대는 서버 연결 후 제공할 예정이에요." })
                    }
                }
            }
        }
    }
    if (addTask) AddTaskDialog(state.pets, onDismiss = { addTask = false }, onAdd = onCreateTask ?: controller::addTask)
    info?.let { message -> AlertDialog(onDismissRequest = { info = null }, title = { Text("안내") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { info = null }) { Text("확인") } }) }
    detailPhoto?.let { photo -> PhotoViewer(petName(photo.petId) ?: "사진", onClose = { detailPhoto = null }) {
        LocalPhoto(photo.path, "선택한 사진", Modifier.fillMaxWidth().aspectRatio(1f), ContentScale.Fit)
    } }
}

@Composable
private fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

// ---------------- Home ----------------

@Composable
private fun HomeTab(
    wide: Boolean, state: CareState, tasks: List<CareTask>, photos: List<PetPhoto>, demo: Boolean, nowMinutes: Int, zone: ZoneId,
    isLate: (CareTask) -> Boolean, petName: (String) -> String?, toggleTask: (String) -> Unit, cloud: Boolean,
    selectedPet: String, careHistoryContent: (@Composable (String, Boolean) -> Unit)?,
    recentPhotosContent: (@Composable (String, () -> Unit) -> Unit)?, controller: CareController,
    onOpenTodo: () -> Unit, onOpenPhotos: () -> Unit, onPhoto: (PetPhoto) -> Unit,
) {
    val summary: @Composable ColumnScope.() -> Unit = {
        Text(LocalDate.now(zone).format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
            style = MaterialTheme.typography.labelMedium, color = DogsTheme.colors.textTertiary)
        Text("오늘 돌봄 ${tasks.size}개 중\n${tasks.count { it.done }}개를 마쳤어요",
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("care-progress"))
        DayRail(tasks.mapNotNull { t -> parseMinutes(t.time)?.let { RailPoint(it, when {
            t.done -> RailState.DONE; isLate(t) -> RailState.LATE; else -> RailState.UPCOMING }) } }, nowMinutes)
        val next = tasks.firstOrNull { !it.done }
        if (next != null) NextTaskCard(next, petName(next.petId), isLate(next), nowMinutes, cloud) { toggleTask(next.id) }
        else FeatureCard {
            Text(if (tasks.isEmpty()) "첫 돌봄을 추가해 볼까요?" else "오늘의 돌봄을 모두 마쳤어요",
                style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(if (tasks.isEmpty()) "할 일 탭에서 매일 하는 돌봄을 만들 수 있어요." else "가족 모두 수고했어요. 내일 일정은 아침에 다시 채워져요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (demo && state.requestAnswer.isBlank()) {
            SectionHeader("받은 요청")
            Banner(icon = DogsIcons.Hand) {
                Text("지훈 · 샘플", style = MaterialTheme.typography.labelLarge)
                Text("오늘 야근이라 늦어요. 저녁 산책 부탁해요!", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InverseButton("제가 할게요", { controller.answer("샘플 요청을 수락했어요.") })
                    SecondaryButton("어려워요", { controller.answer("샘플 요청을 거절했어요.") }, height = 40.dp)
                }
            }
        }
        if (state.requestAnswer.isNotBlank()) Banner(attention = false, icon = DogsIcons.Check) {
            Text(state.requestAnswer, color = DogsTheme.colors.onDoneContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
    val details: @Composable ColumnScope.() -> Unit = {
        val remaining = tasks.filter { !it.done }
        SectionHeader("남은 일", count = remaining.size, action = "전체 일정", onAction = onOpenTodo)
        if (remaining.isEmpty()) Text("남은 일이 없어요.", style = MaterialTheme.typography.bodyMedium, color = DogsTheme.colors.textTertiary)
        remaining.take(3).forEachIndexed { i, task ->
            if (i > 0) ListDivider()
            TaskRowItem(task.time, task.title, petName(task.petId) ?: task.note, false, isLate(task), { toggleTask(task.id) })
        }
        if (careHistoryContent != null) careHistoryContent(selectedPet, true) else {
            val done = tasks.filter { it.done }
            SectionHeader("완료", count = done.size)
            done.forEachIndexed { i, task ->
                if (i > 0) ListDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(DogsTheme.colors.doneContainer), contentAlignment = Alignment.Center) {
                        Icon(DogsIcons.Check, null, Modifier.size(14.dp), tint = DogsTheme.colors.onDoneContainer)
                    }
                    Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp))
                    Text(task.time.ifBlank { "시간 미정" }, style = MaterialTheme.typography.labelMedium, color = DogsTheme.colors.textTertiary)
                }
            }
        }
        SectionHeader("최근 사진", action = "사진첩", onAction = onOpenPhotos)
        if (recentPhotosContent != null) recentPhotosContent(selectedPet, onOpenPhotos)
        else PhotoGrid(photos.take(3), state.pets, demo, onClick = onPhoto)
        ListDivider(Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(DogsIcons.Bulb, null, tint = DogsTheme.colors.done)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("오늘의 돌봄 팁", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
                Text("완료한 돌봄은 바로 체크해 주세요. 가족이 같은 일을 두 번 하지 않도록 도와줘요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScrollColumn(Modifier.weight(1f), summary)
        ScrollColumn(Modifier.weight(1f), details)
    } else ScrollColumn { summary(); Spacer(Modifier.height(4.dp)); details() }
}

@Composable
private fun NextTaskCard(task: CareTask, pet: String?, late: Boolean, nowMinutes: Int, cloud: Boolean, onDone: () -> Unit) {
    val c = DogsTheme.colors
    FeatureCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val minutes = parseMinutes(task.time)
                val label = when {
                    minutes == null -> "시간 미정"
                    late -> "${task.time} 예정, ${nowMinutes - minutes}분 지났어요"
                    else -> "${task.time} 예정"
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(DogsIcons.Clock, null, Modifier.size(16.dp), tint = if (late) c.attention else MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = if (late) c.attention else MaterialTheme.colorScheme.primary)
                }
                Text(task.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(pet, task.note.ifBlank { null }).joinToString(", ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pet != null) DogAvatar(pet, task.petId, 44.dp)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("완료했어요", onDone, Modifier.fillMaxWidth().testTag("complete-next"), icon = DogsIcons.Check)
        Spacer(Modifier.height(12.dp))
        Text(if (cloud) "완료 취소는 완료한 사람 또는 그룹장이 할 수 있어요." else "완료 표시는 할 일 탭에서 되돌릴 수 있어요.",
            style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------- Todo ----------------

@Composable
private fun TodoTab(
    wide: Boolean, tasks: List<CareTask>, isLeader: Boolean, cloud: Boolean, isLate: (CareTask) -> Boolean,
    petName: (String) -> String?, toggleTask: (String) -> Unit, selectedPet: String,
    careHistoryContent: (@Composable (String, Boolean) -> Unit)?,
) {
    val list: @Composable ColumnScope.() -> Unit = {
        Text("${tasks.count { it.done }} / ${tasks.size} 완료", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        if (!isLeader) Banner(attention = false, icon = DogsIcons.Info) {
            Text("할 일 추가와 수정은 그룹장이 해요. 변경 제안은 준비 중이에요.", style = MaterialTheme.typography.bodyMedium,
                color = DogsTheme.colors.onDoneContainer)
        }
        if (tasks.isEmpty()) EmptyState("등록된 할 일이 없어요",
            if (isLeader) "할 일 추가 버튼으로 매일 하는 돌봄을 만들어 보세요." else "그룹장이 할 일을 만들면 여기에 보여요.")
        val remaining = if (careHistoryContent != null) tasks.filterNot { it.done } else tasks
        val parts = listOf(
            Triple("아침", "6시부터 11시", remaining.filter { parseMinutes(it.time)?.let { m -> m < 11 * 60 } ?: false }),
            Triple("낮", "11시부터 17시", remaining.filter { parseMinutes(it.time)?.let { m -> m in 11 * 60 until 17 * 60 } ?: false }),
            Triple("저녁", "17시부터", remaining.filter { parseMinutes(it.time)?.let { m -> m >= 17 * 60 } ?: false }),
            Triple("시간 미정", null, remaining.filter { parseMinutes(it.time) == null }),
        )
        parts.filter { it.third.isNotEmpty() }.forEach { (name, span, items) ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
                if (span != null) Text(span, style = MaterialTheme.typography.labelMedium, color = DogsTheme.colors.textTertiary)
            }
            Column {
                items.forEachIndexed { i, task ->
                    if (i > 0) ListDivider()
                    // In the cloud group a completed task's note carries who completed it (see GroupApp).
                    val note = if (cloud && task.done) null else task.note.ifBlank { null }
                    TaskRowItem(task.time, task.title, listOfNotNull(petName(task.petId), note).joinToString(", "),
                        task.done, isLate(task), { toggleTask(task.id) },
                        doneText = if (!task.done) null else if (cloud) task.note.ifBlank { "완료" } else "완료",
                        toggleTag = "task-${task.id}")
                }
            }
        }
        Text(if (cloud) "완료 기록은 날짜별로 그룹에 저장돼요. 다른 사람의 변경은 새로고침으로 확인하세요." else "임시 일정은 매일 같은 목록으로 표시돼요. 날짜가 바뀌면 완료 표시가 초기화됩니다.",
            style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
        Spacer(Modifier.height(80.dp))
    }
    val history: (@Composable ColumnScope.() -> Unit)? = careHistoryContent?.let { content -> { content(selectedPet, false); Spacer(Modifier.height(80.dp)) } }
    if (wide && history != null) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScrollColumn(Modifier.weight(1f), list)
        ScrollColumn(Modifier.weight(1f), history)
    } else ScrollColumn(Modifier.widthIn(max = 720.dp)) {
        list()
        if (history != null) { ListDivider(); history() }
    }
}

// ---------------- Group ----------------

@Composable
private fun ColumnScope.GroupTab(
    state: CareState, demo: Boolean, cloudGroup: GroupSnapshot?, isLeader: Boolean, notificationsEnabled: Boolean,
    onRequestNotifications: () -> Unit, onTestNotification: () -> Unit, onEditDog: ((String) -> Unit)?,
    controller: CareController, onExit: () -> Unit, onInvite: () -> Unit,
) {
    val members = if (demo) listOf(state.nickname, "아빠", "엄마", "지훈")
        else cloudGroup?.members?.map { if (it.user_id == cloudGroup.user.id) state.nickname else "구성원 ${it.user_id.take(6)}" }.orEmpty()
            .ifEmpty { listOf(state.nickname) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row { state.pets.take(3).forEachIndexed { i, pet ->
            DogAvatar(pet.name, pet.id, 44.dp, Modifier.offset(x = (-12 * i).dp), ring = MaterialTheme.colorScheme.background)
        } }
        Column {
            Text(state.groupName, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
            Text("멤버 ${members.size}명과 반려견 ${state.pets.size}마리", style = MaterialTheme.typography.labelMedium,
                color = DogsTheme.colors.textTertiary)
        }
    }
    SectionHeader("반려견", count = state.pets.size)
    Column {
        state.pets.forEachIndexed { i, pet ->
            if (i > 0) ListDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (pet.photo.isNotBlank()) LocalPhoto(pet.photo, pet.name, Modifier.size(52.dp).clip(CircleShape))
                else DogAvatar(pet.name, pet.id, 52.dp)
                Text(pet.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                onEditDog?.let { edit -> TextLink("정보 수정", { edit(pet.id) }, trailing = DogsIcons.ChevronRight) }
            }
        }
    }
    if (cloudGroup == null) TextLink("초기 설정 다시 보기", controller::startSetup)
    SectionHeader("함께 돌봐요", count = members.size)
    Column {
        members.forEachIndexed { index, name ->
            if (index > 0) ListDivider()
            val leader = (cloudGroup == null && index == 0) || cloudGroup?.members?.getOrNull(index)?.role == "leader"
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonAvatar(name, 40.dp)
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (leader) Box(Modifier.clip(CircleShape).background(DogsTheme.colors.doneContainer).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("그룹장", style = MaterialTheme.typography.labelSmall, color = DogsTheme.colors.onDoneContainer)
                }
            }
        }
    }
    SecondaryButton("가족 초대 안내", onInvite, Modifier.fillMaxWidth(), icon = DogsIcons.Plus)
    if (cloudGroup != null) Text(if (isLeader) "나는 이 그룹의 그룹장이에요." else "나는 이 그룹의 멤버예요.",
        style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
    SectionHeader("돌봄 알림")
    Text(if (notificationsEnabled) "알림이 허용되어 있어요." else "알림을 허용하면 돌봄 소식을 받을 수 있어요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("알림 권한 설정", onRequestNotifications, height = 44.dp)
        TextLink("테스트 알림 보내기", onTestNotification)
    }
    Text("현재는 테스트 알림만 지원해요. 예약·가족 알림은 서버 연결 후 추가됩니다.",
        style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
    ListDivider(Modifier.padding(top = 8.dp))
    TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        contentPadding = PaddingValues(horizontal = 0.dp)) {
        Icon(DogsIcons.Logout, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        Text(if (demo) "샘플 체험 종료" else "로그아웃", style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(24.dp))
}

// ---------------- Shared pieces ----------------

@Composable private fun PetFilters(pets: List<Pet>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DogChip("전체", selected.isBlank(), { onSelect("") })
        pets.forEach { DogChip(it.name, selected == it.id, { onSelect(it.id) }, dogId = it.id) }
    }
}

@Composable private fun PhotoGrid(photos: List<PetPhoto>, pets: List<Pet>, demo: Boolean, onClick: (PetPhoto) -> Unit) {
    if (photos.isEmpty()) {
        if (demo) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pets.ifEmpty { listOf(Pet("sample", "샘플")) }.let { list -> (0 until 3).map { list[it % list.size] } }.forEach { pet ->
                val (bg, fg) = dogTone(pet.id)
                Box(Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.small).background(bg), contentAlignment = Alignment.Center) {
                    Icon(DogsIcons.Paw, "${pet.name} 샘플 사진", Modifier.size(28.dp), tint = fg.copy(alpha = .5f))
                }
            }
        } else Text("아직 사진이 없어요. 첫 추억을 추가해 보세요.", style = MaterialTheme.typography.bodyMedium,
            color = DogsTheme.colors.textTertiary)
    } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { photo ->
                    val name = pets.firstOrNull { it.id == photo.petId }?.name ?: "반려견"
                    LocalPhoto(photo.path, "$name 사진", Modifier.weight(1f).aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small).clickable { onClick(photo) })
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable fun LocalPhoto(path: String, description: String, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    if (bitmap != null) Image(bitmap!!, description, modifier, contentScale = scale)
    else Box(modifier.background(DogsTheme.colors.photoPlaceholder), contentAlignment = Alignment.Center) {
        Icon(DogsIcons.Paw, description, Modifier.size(24.dp), tint = DogsTheme.colors.textTertiary)
    }
}

/**
 * Dark photo viewer (the only dark surface in the light theme). Uses the platform dialog width so it stays a
 * regular focusable dialog window for accessibility services and UI tests.
 */
@Composable
fun PhotoViewer(title: String, onClose: () -> Unit, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {},
                content: @Composable BoxScope.() -> Unit) {
    val c = DogsTheme.colors
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(c.viewer).padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(DogsIcons.Close, "닫기", tint = c.onViewer) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.onViewer, style = MaterialTheme.typography.titleSmall)
                    if (subtitle != null) Text(subtitle, color = c.onViewerVariant, style = MaterialTheme.typography.labelMedium)
                }
                CompositionLocalProvider(LocalContentColor provides c.onViewer) { actions() }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp), contentAlignment = Alignment.Center, content = content)
        }
    }
}

@Composable private fun AddTaskDialog(pets: List<Pet>, onDismiss: () -> Unit, onAdd: (String, String, String) -> Boolean) {
    var title by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("19:00") }
    var petId by rememberSaveable { mutableStateOf(pets.firstOrNull()?.id.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        title = { Text("할 일 만들기", style = MaterialTheme.typography.titleLarge) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DogsTextField("이름", title, { title = it.take(40) }, placeholder = "예: 저녁 산책")
            DogsTextField("시간 (선택)", time, { time = it.take(5) }, placeholder = "19:30",
                helper = "24시간 형식으로 입력해요. 비우면 시간 미정이에요.",
                error = if (error) "이름을 입력하고, 시간은 HH:mm 형식으로 입력해 주세요." else null)
            Text("반려견", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DogChip("그룹 공통", petId.isBlank(), { petId = "" })
                pets.forEach { pet -> DogChip(pet.name, petId == pet.id, { petId = pet.id }, dogId = pet.id) }
            }
            Text("매일 반복돼요. 이미 한 돌봄은 돌봄 기록에 남겨 주세요.", style = MaterialTheme.typography.bodySmall,
                color = DogsTheme.colors.textTertiary)
        }
    }, confirmButton = { PrimaryButton("할 일 저장", { if (onAdd(title, time, petId)) onDismiss() else error = true }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

// ---------------- Setup ----------------

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
    val back = { if (!saving) { if (step > 0) step-- else onExit() } }
    BackHandler { back() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
                BackTopBar("", onBack = back, enabled = !saving) {
                    Text("${step + 1} / 4", style = MaterialTheme.typography.labelLarge, color = DogsTheme.colors.textTertiary,
                        modifier = Modifier.padding(end = 16.dp))
                }
                LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.primary, trackColor = DogsTheme.colors.divider, drawStopIndicator = {})
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(listOf("어떻게 불러드릴까요?", "우리 강아지를 소개해 주세요", "매일의 돌봄을 골라 주세요", "돌봄 소식을 알려드릴까요?")[step],
                        style = MaterialTheme.typography.headlineMedium)
                    when (step) {
                        0 -> {
                            Text("혼자 시작해도 괜찮아요. 나를 그룹장으로 우리집을 만들어요.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DogsTextField("보호자 이름", nickname, { nickname = it.take(20) }, fieldModifier = Modifier.testTag("setup-nickname"),
                                helper = "그룹 멤버에게 이렇게 보여요")
                            DogsTextField("그룹 이름", group, { group = it.take(30) }, helper = "멤버 모두에게 보이는 이름이에요. 나중에 바꿀 수 있어요.")
                            Text(if (groupSetup) "나를 그룹장으로 그룹과 반려견 정보를 저장해요. 초대 가입은 준비 중이에요." else if (cloudSetup) "초기 설정은 내 계정에 저장돼요. 가족 초대는 추후 연결됩니다." else "현재는 기기 안에서만 저장되는 임시 설정입니다. 가족 초대는 추후 연결됩니다.",
                                style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
                        }
                        1 -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(112.dp).clip(CircleShape).background(DogsTheme.colors.divider)
                                    .border(1.5.dp, DogsTheme.colors.controlOutline, CircleShape)
                                    .clickable(enabled = !importing) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    contentAlignment = Alignment.Center) {
                                    if (photo.isNotBlank()) LocalPhoto(photo, "강아지 프로필", Modifier.fillMaxSize())
                                    else Icon(DogsIcons.Camera, "프로필 사진 선택", tint = DogsTheme.colors.textTertiary)
                                }
                            }
                            TextLink(if (importing) "사진 가져오는 중…" else "프로필 사진 선택 (선택)", {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, Modifier.fillMaxWidth(), enabled = !importing)
                            if (cloudSetup) Text("사진은 아직 이 기기에만 보관돼요.", style = MaterialTheme.typography.bodySmall,
                                color = DogsTheme.colors.textTertiary)
                            DogsTextField("강아지 이름", pet, { pet = it.take(20) }, fieldModifier = Modifier.testTag("setup-pet"))
                        }
                        2 -> {
                            Text("원하는 것만 선택하세요. 나중에 할 일 탭에서 추가할 수 있어요.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column {
                                listOf("아침 급여" to "07:30", "산책" to "19:30", "물 갈아주기" to "12:00").forEachIndexed { i, (title, time) ->
                                    if (i > 0) ListDivider()
                                    val on = title in starters
                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                        .clickable { starters = if (on) starters - title else starters + title },
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Checkbox(on, { starters = if (on) starters - title else starters + title },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        Text(time, style = MaterialTheme.typography.labelMedium, color = DogsTheme.colors.textTertiary)
                                    }
                                }
                            }
                        }
                        3 -> {
                            Text("할 일을 잊지 않도록 알림을 받을 수 있어요. 허용하지 않아도 앱을 사용할 수 있습니다.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (notificationsEnabled) "알림이 허용되어 있어요." else "알림 받기는 선택 사항이에요.",
                                style = MaterialTheme.typography.labelLarge)
                            SecondaryButton("알림 켜기", onRequestNotifications, Modifier.fillMaxWidth(), icon = DogsIcons.Bell)
                            Text("현재 버전에서는 그룹 탭에서 테스트 알림을 보낼 수 있어요.", style = MaterialTheme.typography.bodySmall,
                                color = DogsTheme.colors.textTertiary)
                        }
                    }
                    (saveError ?: error)?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(DogsIcons.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(if (step == 3) "홈으로 시작하기" else "다음", {
                        when {
                            step == 0 && nickname.isBlank() -> error = "보호자 이름을 입력해 주세요."
                            step == 1 && pet.isBlank() -> error = "강아지 이름을 입력해 주세요."
                            step < 3 -> { error = null; step++ }
                            else -> if (!onFinish(nickname, group, pet, photo, starters.toSet())) error = "입력한 정보를 확인해 주세요."
                        }
                    }, Modifier.fillMaxWidth().testTag("setup-next"), enabled = !importing, loading = saving)
                }
            }
        }
    }
}
