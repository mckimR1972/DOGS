package com.teamdogs.dogs.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SignedInUser(val name: String, val email: String, val uid: String)
enum class AuthPage { LOGIN, SIGN_UP, RESET_PASSWORD }

data class AuthUiState(
    val user: SignedInUser? = null,
    val pendingEmail: String? = null,
    val signupComplete: Boolean = false,
    val page: AuthPage = AuthPage.LOGIN,
    val isLoading: Boolean = false,
    val message: String? = null,
    val notice: String? = null,
    val verificationCooldown: Int = 0,
    val resetCooldown: Int = 0,
)

class AuthViewModel(private val repository: AuthRepository = FirebaseAuthRepository()) : ViewModel() {
    var state by mutableStateOf(AuthUiState())
        private set
    private val stopObserving: () -> Unit

    init {
        acceptSession(repository.currentSession())
        stopObserving = repository.observe(::acceptSession)
    }

    private fun acceptSession(session: AuthSession?) {
        val justVerified = session?.emailVerified == true && state.pendingEmail != null &&
            state.pendingEmail == session.profile.email
        val keepCompletion = state.signupComplete && session?.emailVerified == true &&
            state.user?.uid == session.profile.uid
        state = state.copy(
            user = session?.takeIf { it.emailVerified }?.profile,
            pendingEmail = session?.takeUnless { it.emailVerified }?.profile?.email,
            signupComplete = justVerified || keepCompletion,
            notice = if (justVerified) null else state.notice,
        )
    }

    fun continueAfterSignup() {
        if (!state.isLoading && state.user != null && state.signupComplete) {
            state = state.copy(signupComplete = false, page = AuthPage.LOGIN, message = null, notice = null)
        }
    }

    fun navigate(page: AuthPage) {
        if (!state.isLoading) state = state.copy(page = page, message = null, notice = null)
    }
    fun beginAction(): Boolean {
        if (state.isLoading) return false
        state = state.copy(isLoading = true, message = null, notice = null)
        return true
    }
    fun finishAction(message: String? = null) {
        state = state.copy(isLoading = false, message = message)
    }
    private fun action(started: Boolean = false, block: suspend () -> Unit) {
        if (!started && !beginAction()) return
        viewModelScope.launch {
            try { block(); acceptSession(repository.currentSession()); finishAction() }
            catch (error: CancellationException) { finishAction(); throw error }
            catch (error: Exception) {
                if ((error as? FirebaseAuthException)?.errorCode in setOf(
                        "ERROR_USER_DISABLED", "ERROR_USER_NOT_FOUND", "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN"
                    ) && repository.currentSession() != null) repository.signOut()
                acceptSession(repository.currentSession())
                finishAction(authError(error))
            }
        }
    }

    // MainActivity begins the action before opening Credential Manager.
    fun signInWithGoogle(idToken: String) = action(started = true) { repository.signInGoogle(idToken) }

    fun signInWithEmail(email: String, password: String) {
        if (state.isLoading) return
        if (!AuthValidation.email(email) || password.isEmpty()) {
            finishAction("이메일과 비밀번호를 입력해 주세요."); return
        }
        action { repository.signInEmail(email.trim(), password) }
    }

    fun signUp(name: String, email: String, password: String, confirmation: String) {
        if (state.isLoading) return
        AuthValidation.registrationError(name, email, password, confirmation)?.let { finishAction(it); return }
        action {
            repository.createAccount(email.trim(), password)
            // A profile update failure must not encourage creation of another account.
            var nameFailed = false
            try { repository.updateName(name.trim()) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { nameFailed = true }
            repository.sendVerification()
            startCooldown(verification = true)
            state = state.copy(notice = if (nameFailed)
                "인증 메일을 보냈어요. 이름은 초기 설정에서 다시 입력해 주세요."
                else "인증 메일을 보냈어요. 메일함을 확인해 주세요.")
        }
    }

    fun resendVerification() {
        if (state.pendingEmail == null || state.verificationCooldown > 0) return
        action {
            repository.sendVerification()
            startCooldown(verification = true)
            state = state.copy(notice = "인증 메일을 다시 보냈어요. 스팸함도 확인해 주세요.")
        }
    }

    fun checkVerification(automatic: Boolean = false) {
        if (state.pendingEmail == null || state.isLoading) return
        action {
            repository.reloadSession()
            if (repository.currentSession()?.emailVerified != true && !automatic) {
                state = state.copy(notice = "아직 인증이 확인되지 않았어요. 메일의 링크를 누른 뒤 다시 확인해 주세요.")
            }
        }
    }

    fun resetPassword(email: String) {
        if (state.isLoading || state.resetCooldown > 0) return
        if (!AuthValidation.email(email)) { finishAction("이메일 주소를 확인해 주세요."); return }
        action {
            try { repository.sendPasswordReset(email.trim()) }
            catch (error: FirebaseAuthException) {
                // Do not reveal account existence, even with enumeration protection disabled.
                if (error.errorCode != "ERROR_USER_NOT_FOUND") throw error
            }
            startCooldown(verification = false)
            state = state.copy(notice = "가입된 이메일이라면 재설정 링크를 보냈어요. 메일이 보이지 않으면 스팸함을 확인해 주세요.")
        }
    }

    private fun startCooldown(verification: Boolean) {
        if (verification) state = state.copy(verificationCooldown = 60)
        else state = state.copy(resetCooldown = 60)
        viewModelScope.launch {
            repeat(60) {
                delay(1_000)
                state = if (verification) state.copy(verificationCooldown = (state.verificationCooldown - 1).coerceAtLeast(0))
                else state.copy(resetCooldown = (state.resetCooldown - 1).coerceAtLeast(0))
            }
        }
    }

    fun signOut() {
        repository.signOut()
        state = state.copy(user = null, pendingEmail = null, signupComplete = false, page = AuthPage.LOGIN, message = null, notice = null)
    }
    override fun onCleared() { stopObserving() }
}

private fun authError(error: Exception): String = when (error) {
    is FirebaseNetworkException -> "인터넷 연결을 확인하고 다시 시도해 주세요."
    is FirebaseTooManyRequestsException -> "요청이 많아 잠시 제한됐어요. 잠시 후 다시 시도해 주세요."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "이미 가입된 이메일이에요. 기존 Google 로그인 또는 이메일 로그인을 이용해 주세요. 비밀번호를 잊었다면 비밀번호 찾기를 이용해 주세요."
        "ERROR_INVALID_EMAIL" -> "이메일 주소를 확인해 주세요."
        "ERROR_WEAK_PASSWORD" -> "비밀번호가 보안 조건에 맞지 않아요. 더 강한 비밀번호를 입력해 주세요."
        "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
            "이메일 또는 비밀번호를 확인해 주세요. Google로 가입했다면 Google 로그인을 이용해 주세요."
        "ERROR_USER_DISABLED" -> "사용이 중지된 계정이에요. 관리자에게 문의해 주세요."
        "ERROR_OPERATION_NOT_ALLOWED", "ERROR_PASSWORD_LOGIN_DISABLED" ->
            "이 로그인 방법이 아직 준비되지 않았어요. 관리자에게 문의해 주세요."
        "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_REQUIRES_RECENT_LOGIN" ->
            "로그인 시간이 만료됐어요. 다시 로그인해 주세요."
        else -> "요청을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
    else -> "요청을 완료하지 못했어요. 연결 상태를 확인하고 다시 시도해 주세요."
}
