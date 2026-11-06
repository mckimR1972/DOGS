package com.teamdogs.dogs

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.group.*
import com.teamdogs.dogs.prototype.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class GroupRepositoryTest {
    private val apps = mutableListOf<FirebaseApp>()
    private val databases = mutableListOf<FirebaseFirestore>()
    @Before fun emulatorOnly() { assumeTrue(InstrumentationRegistry.getArguments().getString("firestoreEmulator") == "true") }
    private fun client(): Triple<SignedInUser, FirebaseFirestore, FirestoreGroupRepository> {
        val app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions.Builder().setProjectId("demo-dogs-onboarding").setApplicationId("1:1234567890:android:dogs")
                .setApiKey("fake-api-key").build(), "group-test-${UUID.randomUUID()}")
        apps += app
        val auth = FirebaseAuth.getInstance(app); auth.useEmulator("10.0.2.2",9099)
        val db = FirebaseFirestore.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        db.useEmulator("10.0.2.2",8080); databases += db
        Tasks.await(auth.signInAnonymously(),20,TimeUnit.SECONDS)
        val user=SignedInUser("테스트 보호자","",auth.currentUser!!.uid)
        return Triple(user,db,FirestoreGroupRepository(user,db))
    }
    private fun seed() = CareState(ready=true,nickname="테스트 보호자",groupName="함께 돌봐요",
        pets=listOf(Pet("pet-1","초코","/local/profile.jpg")),
        tasks=listOf(CareTask("task-1","산책","19:30","pet-1",done=true)))
    @After fun cleanup() {
        databases.forEach { Tasks.await(it.terminate(),60,TimeUnit.SECONDS) }; apps.forEach { it.delete() }
    }
    @Test fun firstSetupUsesSeparateDocumentsAndRoundTripsNullableFields() = runBlocking {
        val (user,db,repo)=client(); assertNull(repo.load())
        val result=repo.saveSetup(seed())
        assertEquals(user.uid,result.group.leader_id)
        assertEquals("leader",result.members.single().role)
        assertEquals("",result.dogs.single().photo)
        assertNull(result.dogs.single().birth); assertNull(result.dogs.single().weight)
        assertEquals(result.group.id,result.dogs.single().group_id)
        assertEquals(result.dogs.single().id,result.todos.single().dog_id)
        assertTrue(result.logs.isEmpty()) // Initial input does not create completion records.
        assertEquals(result,repo.load())
    }
    @Test fun setupRetryPreservesEditedGroupDocuments() = runBlocking {
        val (_,_,repo)=client()
        val created=repo.saveSetup(seed())
        repo.saveDog(created.dogs.single().copy(name="변경한 이름",breed="푸들",birth="2020-03-01",weight=4.2))
        val retried=repo.saveSetup(seed())
        assertEquals(created.group.id,retried.group.id)
        assertEquals("변경한 이름",retried.dogs.single().name)
        assertEquals(1,retried.todos.size)
        assertEquals(4.2,retried.dogs.single().weight!!,0.0)
    }
    @Test fun concurrentBootstrapAndCompletionAreIdempotent() = runBlocking {
        val (user,db,repo)=client()
        val second=FirestoreGroupRepository(user,db)
        val results=coroutineScope { listOf(async { repo.saveSetup(seed()) },async { second.saveSetup(seed()) }).awaitAll() }
        assertEquals(results[0].group.id,results[1].group.id)
        val group=results[0]; val todo=group.todos.single()
        coroutineScope { listOf(async { repo.setCompleted(group.group.id,todo.id,group.day,true) },async { second.setCompleted(group.group.id,todo.id,group.day,true) }).awaitAll() }
        val log=repo.load()!!.logs.single()
        assertEquals(user.uid,log.done_by); assertNotNull(log.done_at)
        repo.setCompleted(group.group.id,todo.id,group.day,false)
        assertTrue(repo.load()!!.logs.isEmpty())
    }
    @Test fun commonTodoAndOfflineServerRead() = runBlocking {
        val (user,db,repo)=client(); val group=repo.saveSetup(seed())
        repo.saveTodo(Todo("common",group.group.id,null,"공통 돌봄",RepeatRule("weekly",listOf(1,3,5)),"12:00",user.uid))
        val common=repo.load()!!.todos.first { it.id=="common" }
        assertNull(common.dog_id); assertEquals(listOf(1,3,5),common.repeat_rule.weekdays)
        Tasks.await(db.disableNetwork(),10,TimeUnit.SECONDS)
        assertTrue(runCatching { repo.load() }.isFailure)
        Tasks.await(db.enableNetwork(),10,TimeUnit.SECONDS)
        repo.deleteTodo(group.group.id,"common")
        assertEquals(1,repo.load()!!.todos.size)
    }
    @Test fun outsiderCannotReadOrReplaceMembership() = runBlocking {
        val (_,_,repo)=client(); val g=repo.saveSetup(seed()); val (other,db,_)=client()
        assertTrue(runCatching { Tasks.await(db.document("groups/${g.group.id}").get(Source.SERVER),10,TimeUnit.SECONDS) }.isFailure)
        assertTrue(runCatching { Tasks.await(db.document("groups/${g.group.id}/members/${other.uid}").set(
            mapOf("group_id" to g.group.id,"user_id" to other.uid,"role" to "leader","joined_at" to FieldValue.serverTimestamp())),10,TimeUnit.SECONDS) }.isFailure)
    }
}
