package com.teamdogs.dogs

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.teamdogs.dogs.prototype.PrototypeApp
import com.teamdogs.dogs.group.GroupApp
import android.content.MutableContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.teamdogs.dogs.auth.AuthViewModel
import com.teamdogs.dogs.ui.auth.LoginScreen
import com.teamdogs.dogs.ui.theme.DOGSTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: AuthViewModel
    private lateinit var credentialManager: CredentialManager

    private var notificationsEnabled by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotificationStatus()
        if (!it) Toast.makeText(this, "알림 없이도 앱을 사용할 수 있어요.", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationStatus()
    }

    private fun refreshNotificationStatus() {
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("notification_permission", MODE_PRIVATE)
            if (prefs.getBoolean("asked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } else {
                prefs.edit().putBoolean("asked", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun sendTestNotification() {
        refreshNotificationStatus()
        if (!notificationsEnabled) {
            Toast.makeText(this, "알림 허용 후 테스트 알림을 다시 눌러 주세요.", Toast.LENGTH_SHORT).show()
            requestNotifications()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("dogs_care_test", "돌봄 테스트", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(this, "dogs_care_test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DOGS · 돌봄 알림")
            .setContentText("알림이 잘 도착했어요. 함께 돌보는 하루를 시작해 보세요.")
            .setAutoCancel(true).build()
        try { manager.notify(1001, notification) }
        catch (_: SecurityException) { refreshNotificationStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        credentialManager = CredentialManager.create(applicationContext)
        enableEdgeToEdge()
        setContent {
            DOGSTheme {
                var preview by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && intent.getBooleanExtra("preview", false)) }
                val user = authViewModel.state.user
                if (preview || user != null) {
                    val accountKey = if (preview) "demo" else "user_${user!!.uid}"
                    key(accountKey) {
                        if (preview) PrototypeApp(accountKey, true, "", notificationsEnabled,
                            onRequestNotifications = ::requestNotifications,
                            onTestNotification = ::sendTestNotification, onExit = { preview = false })
                        else GroupApp(user!!, notificationsEnabled,
                            onRequestNotifications = ::requestNotifications,
                            onTestNotification = ::sendTestNotification, onExit = ::signOut)
                    }
                } else {
                    LoginScreen(authViewModel.state, ::signIn, ::signOut,
                        onPreview = if (BuildConfig.DEBUG) ({ preview = true }) else null)
                }
            }
        }
    }

    private fun signIn() {
        if (!authViewModel.beginAction()) return
        lifecycleScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(
                    serverClientId = getString(R.string.default_web_client_id),
                ).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val result = credentialManager.getCredential(
                    context = MutableContextWrapper(this@MainActivity),
                    request = request,
                )
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    authViewModel.signInWithGoogle(googleCredential.idToken)
                } else {
                    authViewModel.finishAction("지원하지 않는 로그인 응답입니다. 다시 시도해 주세요.")
                }
            } catch (_: GetCredentialCancellationException) {
                authViewModel.finishAction("로그인을 취소했어요. 준비되면 다시 눌러 주세요.")
            } catch (_: NoCredentialException) {
                authViewModel.finishAction("기기에 Google 계정을 추가한 뒤 다시 시도해 주세요.")
            } catch (_: GoogleIdTokenParsingException) {
                authViewModel.finishAction("Google 로그인 정보를 읽지 못했어요. 다시 시도해 주세요.")
            } catch (_: GetCredentialException) {
                authViewModel.finishAction(
                    "Google 로그인 창을 열지 못했어요. 인터넷·Google Play 서비스와 Firebase의 SHA-1 설정을 확인해 주세요.",
                )
            } catch (error: CancellationException) {
                authViewModel.finishAction()
                throw error
            } catch (_: Exception) {
                authViewModel.finishAction("로그인을 시작하지 못했어요. 앱을 다시 열고 시도해 주세요.")
            }
        }
    }

    private fun signOut() {
        if (!authViewModel.beginAction()) return
        authViewModel.signOut()
        lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                authViewModel.finishAction()
            } catch (_: ClearCredentialException) {
                authViewModel.finishAction("로그아웃됐어요. 다음 로그인에서 계정을 다시 확인해 주세요.")
            } catch (error: CancellationException) {
                authViewModel.finishAction()
                throw error
            }
        }
    }
}
