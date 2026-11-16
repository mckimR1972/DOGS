package com.teamdogs.dogs.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthSession(val profile: SignedInUser, val emailVerified: Boolean)

interface AuthRepository {
    fun currentSession(): AuthSession?
    fun observe(listener: (AuthSession?) -> Unit): () -> Unit
    suspend fun signInGoogle(idToken: String)
    suspend fun signInEmail(email: String, password: String)
    suspend fun createAccount(email: String, password: String)
    suspend fun updateName(name: String)
    suspend fun sendVerification()
    suspend fun reloadSession()
    suspend fun sendPasswordReset(email: String)
    fun signOut()
}

class FirebaseAuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : AuthRepository {
    init { auth.setLanguageCode("ko") }

    override fun currentSession() = auth.currentUser?.session()
    override fun observe(listener: (AuthSession?) -> Unit): () -> Unit {
        val observer = FirebaseAuth.AuthStateListener { listener(currentSession()) }
        auth.addAuthStateListener(observer)
        return { auth.removeAuthStateListener(observer) }
    }

    override suspend fun signInGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitAuth()
    }
    override suspend fun signInEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).awaitAuth()
    }
    override suspend fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).awaitAuth()
    }
    override suspend fun updateName(name: String) {
        requireNotNull(auth.currentUser).updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).awaitAuth()
    }
    override suspend fun sendVerification() {
        requireNotNull(auth.currentUser).sendEmailVerification().awaitAuth()
    }
    override suspend fun reloadSession() {
        val user = auth.currentUser ?: return
        user.reload().awaitAuth()
        // Firestore rules see the refreshed email_verified claim, not a stale cached ID token.
        auth.currentUser?.getIdToken(true)?.awaitAuth()
    }
    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).awaitAuth()
    }
    override fun signOut() = auth.signOut()
}

private fun FirebaseUser.session() = AuthSession(
    SignedInUser(displayName.orEmpty().ifBlank { "보호자" }, email.orEmpty(), uid), isEmailVerified,
)

private suspend fun <T> Task<T>.awaitAuth(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Authentication failed"))
        }
    }
}
