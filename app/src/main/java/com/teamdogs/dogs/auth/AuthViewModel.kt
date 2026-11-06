package com.teamdogs.dogs.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

data class SignedInUser(val name: String, val email: String, val uid: String)

data class AuthUiState(
    val user: SignedInUser? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    var state by mutableStateOf(AuthUiState(user = auth.currentUser?.toProfile()))
        private set

    // Firebase restores its session on app restart; never store Google ID tokens ourselves.
    private val authListener = FirebaseAuth.AuthStateListener {
        state = state.copy(user = it.currentUser?.toProfile())
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun beginAction(): Boolean {
        if (state.isLoading) return false
        state = state.copy(isLoading = true, message = null)
        return true
    }

    fun finishAction(message: String? = null) {
        state = state.copy(isLoading = false, message = message)
    }

    fun signInWithGoogle(idToken: String) {
        // Firebase validates the Google token before creating an authenticated session.
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                state = state.copy(user = auth.currentUser?.toProfile())
                finishAction()
            } else {
                val message = when (val error = task.exception) {
                    is FirebaseNetworkException -> "인터넷 연결을 확인하고 다시 시도해 주세요."
                    is FirebaseAuthException -> when (error.errorCode) {
                        "ERROR_USER_DISABLED" -> "사용이 중지된 계정입니다. 관리자에게 문의해 주세요."
                        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                            "이미 다른 로그인 방법으로 가입된 이메일입니다. 기존 방법으로 로그인해 주세요."
                        "ERROR_OPERATION_NOT_ALLOWED" -> "Firebase에서 Google 로그인을 사용 설정해 주세요."
                        "ERROR_INVALID_CREDENTIAL" -> "로그인 인증에 실패했어요. Firebase 앱 설정을 확인해 주세요."
                        else -> "로그인에 실패했어요. 다시 시도해 주세요. (${error.errorCode})"
                    }
                    else -> "로그인에 실패했어요. 연결 상태를 확인하고 다시 시도해 주세요."
                }
                finishAction(message)
            }
        }
    }

    fun signOut() {
        auth.signOut()
        state = state.copy(user = null)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
    }
}

private fun FirebaseUser.toProfile() = SignedInUser(
    name = displayName.orEmpty().ifBlank { "보호자" },
    email = email.orEmpty(),
    uid = uid,
)
