package com.teamdogs.dogs

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.teamdogs.dogs.auth.FirebaseAuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.android.gms.tasks.Tasks
import com.teamdogs.dogs.group.FirestoreGroupRepository
import com.teamdogs.dogs.prototype.CareState
import com.teamdogs.dogs.prototype.Pet
import com.teamdogs.dogs.prototype.CareTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Named demo app only: cannot create users or send messages in the team's real project. */
class EmailAuthRepositoryTest {
    @Test fun signupVerifyReloadResetAndLoginAgainstEmulator() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("authEmulator") == "true")
        val app = FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().targetContext,
            FirebaseOptions.Builder().setProjectId("demo-dogs-onboarding")
                .setApplicationId("1:1234567890:android:dogs").setApiKey("fake-api-key").build(),
            "email-test-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        val repo = FirebaseAuthRepository(auth)
        val db = FirebaseFirestore.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        db.useEmulator("10.0.2.2", 8080)
        val email = "sdk-${UUID.randomUUID()}@example.com"
        try {
            repo.createAccount(email, "Dogs123!")
            repo.updateName("테스트 보호자")
            val uid = repo.currentSession()!!.profile.uid
            assertFalse(repo.currentSession()!!.emailVerified)
            assertEquals("테스트 보호자", repo.currentSession()!!.profile.name)
            repo.sendVerification()
            val verifyCode = code(email, "VERIFY_EMAIL")
            request("/identitytoolkit.googleapis.com/v1/accounts:update?key=fake", JSONObject().put("oobCode", verifyCode))
            repo.reloadSession()
            assertTrue(repo.currentSession()!!.emailVerified)
            val groups = FirestoreGroupRepository(repo.currentSession()!!.profile, db)
            val group = groups.saveSetup(CareState(ready = true, nickname = "테스트 보호자", groupName = "이메일 그룹",
                pets = listOf(Pet("pet-1", "모카")),
                tasks = listOf(CareTask("task-1", "산책", "19:30", "pet-1"))))
            assertEquals(uid, group.user.id)
            assertEquals(email, group.user.email)
            repo.signOut(); assertNull(repo.currentSession())
            repo.signInEmail(email, "Dogs123!")
            assertEquals(uid, repo.currentSession()!!.profile.uid)
            assertTrue(repo.currentSession()!!.emailVerified)
            repo.signOut()
            repo.sendPasswordReset(email)
            val resetCode = code(email, "PASSWORD_RESET")
            request("/identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=fake",
                JSONObject().put("oobCode", resetCode).put("newPassword", "NewDogs456!"))
            val oldPasswordError = runCatching { repo.signInEmail(email, "Dogs123!") }.exceptionOrNull()
            assertTrue(oldPasswordError is FirebaseAuthException)
            repo.signInEmail(email, "NewDogs456!")
            assertEquals(uid, repo.currentSession()!!.profile.uid)
            assertEquals(group.group.id, groups.load()!!.group.id)
            repo.signOut()
            val duplicate = runCatching { repo.createAccount(email, "Dogs123!") }.exceptionOrNull()
            assertEquals("ERROR_EMAIL_ALREADY_IN_USE", (duplicate as FirebaseAuthException).errorCode)
            assertNull(repo.currentSession())
        } finally { auth.signOut(); Tasks.await(db.terminate(), 30, TimeUnit.SECONDS); app.delete() }
    }

    private fun code(email: String, type: String): String {
        val codes = request("/emulator/v1/projects/demo-dogs-onboarding/oobCodes").getJSONArray("oobCodes")
        return (0 until codes.length()).map { codes.getJSONObject(it) }
            .last { it.getString("email") == email && it.getString("requestType") == type }.getString("oobCode")
    }
    private fun request(path: String, body: JSONObject? = null): JSONObject {
        val connection = URL("http://10.0.2.2:9099$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(connection.responseCode == 200) { "Emulator HTTP ${connection.responseCode}" }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}
