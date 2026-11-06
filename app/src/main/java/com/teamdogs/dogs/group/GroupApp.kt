package com.teamdogs.dogs.group

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.FirebaseFirestoreException
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.prototype.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.UUID

data class GroupUiState(val snapshot: GroupSnapshot? = null, val initialized: Boolean = false,
    val busy: Boolean = false, val error: String? = null)

class GroupViewModel(private val repository: GroupRepository) : ViewModel() {
    private val mutable = MutableStateFlow(GroupUiState())
    val state = mutable.asStateFlow()
    init { refresh() }
    private fun run(action: suspend () -> GroupSnapshot?) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { val result = withTimeout(25_000) { action() }
                mutable.value = GroupUiState(result, initialized = true)
            } catch (e: TimeoutCancellationException) {
                mutable.value = mutable.value.copy(busy = false, error = "서버 응답을 확인하지 못했어요. 새로고침 후 다시 확인해 주세요.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when ((e as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> "이 작업의 권한이 없거나 그룹 소속이 변경됐어요. 새로고침해 주세요."
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
                    else -> "저장 또는 조회를 완료하지 못했어요. 입력값과 연결을 확인한 뒤 다시 시도해 주세요."
                }
                mutable.value = if ((e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    GroupUiState(error = message) else mutable.value.copy(busy = false, error = message)
            }
        }
    }
    fun refresh() = run { repository.load() }
    fun create(state: CareState) = run { repository.saveSetup(state) }
    fun add(title: String, time: String, petId: String): Boolean {
        val snapshot = mutable.value.snapshot ?: return false
        if (mutable.value.busy || snapshot.group.leader_id != snapshot.user.id || title.isBlank() ||
            !Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(time)) return false
        val todo = Todo(UUID.randomUUID().toString(), snapshot.group.id, petId.ifBlank { null }, title.trim(),
            RepeatRule(), time, snapshot.user.id)
        run { repository.saveTodo(todo); repository.load() }
        return true
    }
    fun toggle(id: String) {
        val snapshot = mutable.value.snapshot ?: return
        val completed = snapshot.logs.none { it.todo_id == id }
        run { repository.setCompleted(snapshot.group.id, id, snapshot.day, completed); repository.load() }
    }
    fun saveDog(dog: Dog) = run { repository.saveDog(dog); repository.load() }
}

@Composable
fun GroupApp(account: SignedInUser, notificationsEnabled: Boolean, onRequestNotifications: () -> Unit,
    onTestNotification: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val modelStore = remember(account.uid) { ViewModelStore() }
    DisposableEffect(modelStore) { onDispose { modelStore.clear() } }
    val model = remember(modelStore) { ViewModelProvider(modelStore, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GroupViewModel(FirestoreGroupRepository(account)) as T
    })[GroupViewModel::class.java] }
    val ui by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val snapshot = ui.snapshot
    if (!ui.initialized) {
        Scaffold { insets -> Column(Modifier.fillMaxSize().padding(insets).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text("DOGS", style = MaterialTheme.typography.headlineLarge)
            if (ui.busy) { CircularProgressIndicator(); Text("우리 그룹 정보를 불러오고 있어요…") }
            ui.error?.let { Text(it); Button(onClick = model::refresh) { Text("다시 시도") } }
            TextButton(onClick = onExit) { Text("로그아웃") }
        } }
        return
    }
    if (snapshot == null) {
        val setupController = remember(account.uid) { CareController(LocalCareStore(context, "setup_${account.uid}"), false) }
        SetupScreen(setupController.state, account.name, "setup_${account.uid}", notificationsEnabled,
            onRequestNotifications, onExit, onFinish = { nickname, group, pet, photo, starters ->
                val seed = setupController.prepareSetup(nickname, group, pet, photo, starters)
                if (seed == null || ui.busy) false else {
                    // Keep the setup draft and selected photo on this device until the group is created.
                    setupController.restore(seed)
                    model.create(seed); true
                }
            }, saving = ui.busy, saveError = ui.error, cloudSetup = true, groupSetup = true)
        return
    }
    val accountKey = "group_${account.uid}_${snapshot.group.id}"
    val controller = remember(accountKey) { CareController(LocalCareStore(context, accountKey), false) }
    val draft = remember(accountKey) { LocalCareStore(context, "setup_${account.uid}").load() }
    LaunchedEffect(snapshot) {
        val local = controller.state
        fun mapped(id: String) = stableId("${snapshot.group.id}:dog:$id")
        val localPhotos = if (local.ready) local.photos else draft?.photos.orEmpty().map { it.copy(petId = mapped(it.petId)) }
        controller.restore(CareState(ready = true, nickname = snapshot.user.name, groupName = snapshot.group.name,
            pets = snapshot.dogs.map { dog -> Pet(dog.id, dog.name,
                local.pets.find { it.id == dog.id }?.photo?.takeIf { it.isNotBlank() }
                    ?: draft?.pets?.find { mapped(it.id) == dog.id }?.photo.orEmpty()) },
            tasks = snapshot.todos.filter { it.isDue(LocalDate.parse(snapshot.day)) }.map { todo ->
                val log = snapshot.logs.find { it.todo_id == todo.id }
                CareTask(todo.id, todo.title, todo.due_at, todo.dog_id.orEmpty(),
                    if (log == null) todo.note else "${if (log.done_by == account.uid) snapshot.user.name else log.done_by.take(6)} · 완료 기록됨", log != null)
            }, photos = localPhotos.filter { photo -> snapshot.dogs.any { it.id == photo.petId } }, day = snapshot.day))
    }
    var editingDog by remember { mutableStateOf<Dog?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        PrototypeApp(accountKey, false, account.name, notificationsEnabled, onRequestNotifications, onTestNotification,
            onExit, controller, cloudGroup = snapshot, onToggleTask = model::toggle, onCreateTask = model::add,
            onRefresh = model::refresh, onEditDog = { id -> editingDog = snapshot.dogs.find { it.id == id } }, cloudBusy = ui.busy)
    }
    editingDog?.let { dog -> DogDialog(dog, onDismiss = { editingDog = null }, onSave = { model.saveDog(it); editingDog = null }) }
}

@Composable private fun DogDialog(dog: Dog, onDismiss: () -> Unit, onSave: (Dog) -> Unit) {
    var name by remember { mutableStateOf(dog.name) }
    var breed by remember { mutableStateOf(dog.breed) }
    var birth by remember { mutableStateOf(dog.birth.orEmpty()) }
    var weight by remember { mutableStateOf(dog.weight?.toString().orEmpty()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("반려견 정보") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("이름") })
            OutlinedTextField(breed, { breed = it.take(60) }, label = { Text("견종 (선택)") })
            OutlinedTextField(birth, { birth = it.take(10) }, label = { Text("생일 YYYY-MM-DD (선택)") })
            OutlinedTextField(weight, { weight = it.take(8) }, label = { Text("체중 kg (선택)") })
            if (error) Text("이름, 생일 또는 체중을 확인해 주세요.")
        }
    }, confirmButton = { TextButton(onClick = {
        val parsedWeight = weight.toDoubleOrNull()
        val valid = name.isNotBlank() && (birth.isBlank() || runCatching { LocalDate.parse(birth) }.isSuccess) &&
            (weight.isBlank() || (parsedWeight != null && parsedWeight > 0 && parsedWeight <= 200))
        if (valid) onSave(dog.copy(name = name.trim(), breed = breed.trim(), birth = birth.ifBlank { null }, weight = parsedWeight)) else error = true
    }) { Text("저장") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
