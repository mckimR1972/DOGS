package com.teamdogs.dogs

import androidx.lifecycle.ViewModelStore
import com.google.firebase.FirebaseNetworkException
import com.teamdogs.dogs.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun model(repo: FakeAuth): AuthViewModel = AuthViewModel(repo).also {
        stores += ViewModelStore().apply { put("auth", it) }
    }

    @Test fun registrationKeepsUnverifiedAccountOutOfAppAndSendsMail() = runTest(dispatcher) {
        val repo = FakeAuth(); val vm = model(repo)
        vm.signUp(" 민지 ", " minji@example.com ", "Dogs123!", "Dogs123!")
        runCurrent()
        assertNull(vm.state.user); assertEquals("minji@example.com", vm.state.pendingEmail)
        assertEquals("민지", repo.currentSession()!!.profile.name)
        assertEquals(1, repo.creations); assertEquals(1, repo.verifications)
        vm.resendVerification(); runCurrent(); assertEquals(1, repo.verifications)
        advanceTimeBy(60_000); runCurrent()
        vm.resendVerification(); runCurrent(); assertEquals(2, repo.verifications)
    }
    @Test fun restoredUnverifiedSessionCannotEnterHomeUntilReloadSucceeds() = runTest(dispatcher) {
        val repo = FakeAuth(session = session(false)); val vm = model(repo)
        assertNull(vm.state.user); assertNotNull(vm.state.pendingEmail); assertFalse(vm.state.signupComplete)
        vm.continueAfterSignup(); assertNull(vm.state.user)
        vm.checkVerification(); runCurrent(); assertNull(vm.state.user)
        repo.verifiedOnServer = true
        vm.checkVerification(); runCurrent()
        assertEquals("uid", vm.state.user!!.uid); assertNull(vm.state.pendingEmail)
        assertEquals(2, repo.reloads)
        assertTrue(vm.state.signupComplete)
        vm.checkVerification(automatic = true); runCurrent()
        assertTrue(vm.state.signupComplete)
        vm.continueAfterSignup()
        assertFalse(vm.state.signupComplete); assertNotNull(vm.state.user)
    }
    @Test fun alreadyVerifiedSessionDoesNotShowSignupCompletionAgain() = runTest(dispatcher) {
        val vm = model(FakeAuth(session = session(true)))
        assertNotNull(vm.state.user)
        assertFalse(vm.state.signupComplete)
    }
    @Test fun automaticVerificationShowsCompletionAndLogoutClearsIt() = runTest(dispatcher) {
        val repo = FakeAuth(session = session(false)).apply { verifiedOnServer = true }
        val vm = model(repo)
        vm.checkVerification(automatic = true); runCurrent()
        assertTrue(vm.state.signupComplete)
        vm.signOut()
        assertFalse(vm.state.signupComplete); assertNull(vm.state.user)
    }
    @Test fun sendFailurePreservesCreatedAccountAndRetryDoesNotCreateAnother() = runTest(dispatcher) {
        val repo = FakeAuth().apply { mailFails = true }; val vm = model(repo)
        vm.signUp("민지", "minji@example.com", "Dogs123!", "Dogs123!"); runCurrent()
        assertEquals(1, repo.creations); assertNotNull(vm.state.pendingEmail)
        assertNotNull(vm.state.message); assertFalse(vm.state.isLoading)
        repo.mailFails = false; vm.resendVerification(); runCurrent()
        assertEquals(1, repo.creations); assertNotNull(vm.state.notice)
    }
    @Test fun passwordMismatchDoesNotCreateAccount() = runTest(dispatcher) {
        val repo = FakeAuth(); val vm = model(repo)
        vm.signUp("민지", "minji@example.com", "Dogs123!", "different"); runCurrent()
        assertEquals(0, repo.creations); assertEquals("비밀번호가 서로 달라요.", vm.state.message)
    }
    @Test fun pendingRequestPreventsRepeatedRegistration() = runTest(dispatcher) {
        val repo = FakeAuth().apply { createGate = CompletableDeferred() }; val vm = model(repo)
        repeat(2) { vm.signUp("민지", "minji@example.com", "Dogs123!", "Dogs123!") }
        runCurrent(); assertEquals(1, repo.creations); assertTrue(vm.state.isLoading)
        repo.createGate!!.complete(Unit); runCurrent(); assertFalse(vm.state.isLoading)
    }
    @Test fun verifiedGoogleSessionStillEntersAppAndLogoutClearsGate() = runTest(dispatcher) {
        val repo = FakeAuth(); val vm = model(repo)
        vm.beginAction(); vm.signInWithGoogle("test-token"); runCurrent()
        assertNotNull(vm.state.user); assertNull(vm.state.pendingEmail)
        vm.signOut(); assertNull(vm.state.user); assertNull(vm.state.pendingEmail)
    }
    @Test fun resetUsesGenericConfirmationAndCooldown() = runTest(dispatcher) {
        val repo = FakeAuth(); val vm = model(repo)
        vm.resetPassword("minji@example.com"); runCurrent()
        assertTrue(vm.state.notice!!.startsWith("가입된 이메일이라면"))
        vm.resetPassword("minji@example.com"); runCurrent(); assertEquals(1, repo.resets)
        assertNull(vm.state.user)
    }
    @Test fun passwordPolicyAcceptsExamplesAndRejectsMissingRequirements() {
        assertTrue(AuthValidation.password("Dogs123!"))
        listOf("short1!", "Dogs12345", "Dogspass!", "Dogs 123!").forEach { assertFalse(AuthValidation.password(it)) }
        assertFalse(AuthValidation.email("a@b")); assertFalse(AuthValidation.email("a b@dogs.com"))
        assertTrue(AuthValidation.email(" name+dogs@example.com "))
    }
    private fun session(verified: Boolean) = AuthSession(SignedInUser("민지", "minji@example.com", "uid"), verified)

    private inner class FakeAuth(var session: AuthSession? = null) : AuthRepository {
        private var observer: ((AuthSession?) -> Unit)? = null
        var creations = 0; var verifications = 0; var reloads = 0; var resets = 0
        var mailFails = false; var verifiedOnServer = false
        var createGate: CompletableDeferred<Unit>? = null
        override fun currentSession() = session
        override fun observe(listener: (AuthSession?) -> Unit): () -> Unit { observer = listener; return { observer = null } }
        override suspend fun signInGoogle(idToken: String) { session = session(true); observer?.invoke(session) }
        override suspend fun signInEmail(email: String, password: String) { session = session(verifiedOnServer); observer?.invoke(session) }
        override suspend fun createAccount(email: String, password: String) {
            creations++; createGate?.await(); session = session(false).copy(profile = SignedInUser("", email, "uid")); observer?.invoke(session)
        }
        override suspend fun updateName(name: String) { session = session!!.copy(profile = session!!.profile.copy(name = name)) }
        override suspend fun sendVerification() { if (mailFails) throw FirebaseNetworkException("offline"); verifications++ }
        override suspend fun reloadSession() { reloads++; session = session!!.copy(emailVerified = verifiedOnServer) }
        override suspend fun sendPasswordReset(email: String) { resets++ }
        override fun signOut() { session = null; observer?.invoke(null) }
    }
}
