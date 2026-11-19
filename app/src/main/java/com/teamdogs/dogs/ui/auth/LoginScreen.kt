package com.teamdogs.dogs.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdogs.dogs.auth.*
import com.teamdogs.dogs.ui.components.*
import com.teamdogs.dogs.ui.theme.DOGSTheme
import com.teamdogs.dogs.ui.theme.DogsTheme

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onNavigate: (AuthPage) -> Unit = {},
    onEmailSignIn: (String, String) -> Unit = { _, _ -> },
    onSignUp: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onResetPassword: (String) -> Unit = {},
    onResendVerification: () -> Unit = {},
    onCheckVerification: () -> Unit = {},
    onContinueAfterSignup: () -> Unit = {},
) {
    val verify = state.pendingEmail != null
    val subPage = !verify && !state.signupComplete && state.user == null && state.page != AuthPage.LOGIN
    BackHandler(enabled = state.isLoading || subPage) {
        if (!state.isLoading) onNavigate(AuthPage.LOGIN)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
                if (subPage) BackTopBar(if (state.page == AuthPage.SIGN_UP) "회원가입" else "비밀번호 찾기",
                    onBack = { onNavigate(AuthPage.LOGIN) }, enabled = !state.isLoading)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = if (subPage) 4.dp else 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when {
                        verify -> VerificationContent(state, onSignOut, onResendVerification, onCheckVerification)
                        state.signupComplete && state.user != null -> SignupCompleteContent(state, onContinueAfterSignup)
                        state.user != null -> {
                            Headline("로그인 완료", "${state.user.name}님, 반가워요!")
                            Text(state.user.email, style = MaterialTheme.typography.bodyMedium)
                            Text(state.user.uid, style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
                            PrimaryButton("로그아웃", onSignOut, Modifier.fillMaxWidth(), enabled = !state.isLoading)
                        }
                        else -> key(state.page) {
                            when (state.page) {
                                AuthPage.LOGIN -> LoginContent(state, onSignIn, onEmailSignIn, onNavigate, onPreview)
                                AuthPage.SIGN_UP -> SignUpContent(state, onSignUp)
                                AuthPage.RESET_PASSWORD -> ResetContent(state, onNavigate, onResetPassword)
                            }
                        }
                    }
                    if (state.isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("처리 중이에요…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.message?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(DogsIcons.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("authError"))
                        }
                    }
                    state.notice?.let {
                        Banner(attention = false, icon = DogsIcons.Check) {
                            Text(it, Modifier.testTag("authNotice"), style = MaterialTheme.typography.bodyMedium,
                                color = DogsTheme.colors.onDoneContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoginContent(state: AuthUiState, google: () -> Unit, emailLogin: (String, String) -> Unit,
                         navigate: (AuthPage) -> Unit, preview: (() -> Unit)?) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not saved into instance state, preferences, or Firestore.
    var password by remember { mutableStateOf("") }
    Spacer(Modifier.height(32.dp))
    Text("dogs", style = MaterialTheme.typography.displaySmall)
    RailMotif(Modifier.width(240.dp), filled = 3)
    Spacer(Modifier.height(4.dp))
    Headline("밥 줬는지, 산책 갔는지\n이제 묻지 않아도 돼요", "가족이 함께 돌본 기록이 한곳에 남아요.")
    Spacer(Modifier.height(4.dp))
    DogsTextField("이메일", email, { email = it }, fieldModifier = Modifier.testTag("loginEmail"),
        enabled = !state.isLoading, email = true)
    DogsTextField("비밀번호", password, { password = it }, fieldModifier = Modifier.testTag("loginPassword"),
        enabled = !state.isLoading, password = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextLink("비밀번호를 잊으셨나요?", { navigate(AuthPage.RESET_PASSWORD) }, enabled = !state.isLoading)
    }
    PrimaryButton("로그인", { emailLogin(email, password) }, Modifier.fillMaxWidth(),
        enabled = !state.isLoading && AuthValidation.email(email) && password.isNotEmpty())
    SecondaryButton("Google로 로그인", google, Modifier.fillMaxWidth(), enabled = !state.isLoading)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Text("처음이신가요?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { navigate(AuthPage.SIGN_UP) }, enabled = !state.isLoading, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("회원가입", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (preview != null) TextLink("샘플로 둘러보기", preview, Modifier.fillMaxWidth(), enabled = !state.isLoading)
}

@Composable
private fun SignUpContent(state: AuthUiState, submit: (String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val validationError = AuthValidation.registrationError(name, email, password, confirmation)
    StepLabel(1, "정보 입력")
    DogsTextField("이름", name, { name = it }, fieldModifier = Modifier.testTag("signupName"), enabled = !state.isLoading,
        isError = attempted && name.trim().length !in 1..20, helper = "그룹 멤버에게 이렇게 보여요")
    DogsTextField("이메일", email, { email = it }, fieldModifier = Modifier.testTag("signupEmail"), enabled = !state.isLoading,
        email = true, isError = attempted && !AuthValidation.email(email))
    val weak = password.isNotEmpty() && !AuthValidation.password(password)
    DogsTextField("비밀번호", password, { password = it }, fieldModifier = Modifier.testTag("signupPassword"),
        enabled = !state.isLoading, password = true, isError = (attempted && !AuthValidation.password(password)) || weak)
    PasswordRules(password)
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    DogsTextField("비밀번호 확인", confirmation, { confirmation = it }, fieldModifier = Modifier.testTag("signupConfirmation"),
        enabled = !state.isLoading, password = true, error = if (mismatch) "비밀번호가 서로 달라요" else null)
    Text("입력한 이메일로 인증 링크를 보내드려요. 이메일 인증을 마치면 회원가입이 완료돼요.",
        style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
    if (attempted && validationError != null) {
        Text(validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("signupValidationError"))
    }
    PrimaryButton(if (state.isLoading) "인증 메일 요청 중…" else "인증 메일 보내기", {
        attempted = true
        focusManager.clearFocus()
        if (validationError == null) submit(name, email, password, confirmation)
    }, Modifier.fillMaxWidth(), enabled = !state.isLoading)
}

@Composable
private fun PasswordRules(password: String) {
    val rules = listOf(
        "8자 이상" to (password.length in 8..128),
        "숫자 포함" to password.any { it.isDigit() },
        "특수문자 포함" to password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
        "공백 없음" to (password.isNotEmpty() && password.none { it.isWhitespace() }),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        rules.forEach { (label, ok) ->
            val color = if (ok) DogsTheme.colors.done else DogsTheme.colors.textTertiary
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(DogsIcons.Check, null, Modifier.size(14.dp), tint = color)
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

@Composable
private fun ResetContent(state: AuthUiState, navigate: (AuthPage) -> Unit, reset: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    Headline("비밀번호를 다시 설정해요", "가입한 이메일로 재설정 링크를 보내드려요. 메일에서 새 비밀번호를 설정한 뒤 로그인해 주세요.")
    DogsTextField("이메일", email, { email = it }, fieldModifier = Modifier.testTag("resetEmail"),
        enabled = !state.isLoading, email = true)
    PrimaryButton(if (state.resetCooldown > 0) "${state.resetCooldown}초 후 다시 보내기" else "재설정 링크 보내기",
        { reset(email) }, Modifier.fillMaxWidth(),
        enabled = !state.isLoading && state.resetCooldown == 0 && AuthValidation.email(email))
    TextLink("로그인으로 돌아가기", { navigate(AuthPage.LOGIN) }, Modifier.fillMaxWidth(), enabled = !state.isLoading)
}

@Composable
private fun VerificationContent(state: AuthUiState, signOut: () -> Unit, resend: () -> Unit, check: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    StepLabel(2, "이메일 인증")
    Headline("이메일 인증을 완료해 주세요")
    Text(state.pendingEmail.orEmpty(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text("메일의 인증 링크를 누른 뒤 이 화면으로 돌아와 주세요. 인증이 확인되면 회원가입이 완료돼요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryButton("인증 여부 확인", check, Modifier.fillMaxWidth(), enabled = !state.isLoading)
    SecondaryButton(if (state.verificationCooldown > 0) "${state.verificationCooldown}초 후 다시 보내기" else "인증 메일 다시 보내기",
        resend, Modifier.fillMaxWidth(), enabled = !state.isLoading && state.verificationCooldown == 0)
    Text("메일이 보이지 않으면 스팸함을 확인해 주세요.", style = MaterialTheme.typography.bodySmall, color = DogsTheme.colors.textTertiary)
    TextLink("다른 계정으로 로그인", signOut, Modifier.fillMaxWidth(), enabled = !state.isLoading)
}

@Composable
private fun SignupCompleteContent(state: AuthUiState, continueToApp: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    RailMotif(Modifier.width(200.dp), filled = 5)
    StepLabel(3, "가입 완료")
    Headline("가입이 완료됐어요", "이메일 인증을 마쳤어요. DOGS에 오신 것을 환영해요!")
    Text(state.user?.email.orEmpty(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Text("이제 보호자와 반려견 정보를 설정하고 함께 돌봄을 시작해 보세요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryButton("돌봄 시작하기", continueToApp, Modifier.fillMaxWidth(), enabled = !state.isLoading)
}

@Composable
private fun StepLabel(number: Int, title: String) {
    Text("$number / 3 · $title", style = MaterialTheme.typography.labelLarge, color = DogsTheme.colors.textTertiary)
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() { DOGSTheme { LoginScreen(AuthUiState(), {}, {}) } }

@Preview(showBackground = true)
@Composable
private fun SignUpPreview() { DOGSTheme { LoginScreen(AuthUiState(page = AuthPage.SIGN_UP), {}, {}) } }
