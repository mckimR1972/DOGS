package com.teamdogs.dogs.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.teamdogs.dogs.auth.*
import com.teamdogs.dogs.ui.theme.DOGSTheme

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
    BackHandler(enabled = state.isLoading || (!verify && !state.signupComplete && state.page != AuthPage.LOGIN)) {
        if (!state.isLoading) onNavigate(AuthPage.LOGIN)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when {
                    verify -> VerificationContent(state, onSignOut, onResendVerification, onCheckVerification)
                    state.signupComplete && state.user != null -> SignupCompleteContent(state, onContinueAfterSignup)
                    state.user != null -> {
                        Text("로그인 완료", style = MaterialTheme.typography.headlineSmall)
                        Text("${state.user.name}님, 반가워요!")
                        Text(state.user.email)
                        Text(state.user.uid, style = MaterialTheme.typography.bodySmall)
                        PrimaryAction("로그아웃", !state.isLoading, onSignOut)
                    }
                    else -> key(state.page) {
                        when (state.page) {
                            AuthPage.LOGIN -> LoginContent(state, onSignIn, onEmailSignIn, onNavigate, onPreview)
                            AuthPage.SIGN_UP -> SignUpContent(state, onNavigate, onSignUp)
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
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("authError")) }
                state.notice?.let {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(it, Modifier.padding(16.dp).testTag("authNotice"), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(state: AuthUiState, google: () -> Unit, emailLogin: (String, String) -> Unit,
                         navigate: (AuthPage) -> Unit, preview: (() -> Unit)?) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are deliberately not saved into instance state, preferences, or Firestore.
    var password by remember { mutableStateOf("") }
    Spacer(Modifier.height(20.dp))
    Text("DOGS", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary)
    Text("함께 돌보는 우리 강아지", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("작은 돌봄을 함께 나누고, 소중한 하루를 기록해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    AuthField("이메일", email, { email = it }, "loginEmail", !state.isLoading, email = true)
    AuthField("비밀번호", password, { password = it }, "loginPassword", !state.isLoading, password = true)
    PrimaryAction("로그인", !state.isLoading && AuthValidation.email(email) && password.isNotEmpty()) {
        emailLogin(email, password)
    }
    TextButton(onClick = { navigate(AuthPage.RESET_PASSWORD) }, enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth()) { Text("비밀번호를 잊으셨나요?") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(Modifier.weight(1f)); Text("또는", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
    OutlinedButton(onClick = google, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text("Google로 로그인") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("아직 계정이 없나요?", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { navigate(AuthPage.SIGN_UP) }, enabled = !state.isLoading) { Text("회원가입") }
    }
    if (preview != null) TextButton(onClick = preview, enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth()) { Text("샘플로 둘러보기") }
}

@Composable
private fun SignUpContent(state: AuthUiState, navigate: (AuthPage) -> Unit,
                          submit: (String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val validationError = AuthValidation.registrationError(name, email, password, confirmation)
    BackHeader("회원가입", !state.isLoading) { navigate(AuthPage.LOGIN) }
    SignupStep(1, "정보 입력")
    AuthField("이름", name, { name = it }, "signupName", !state.isLoading,
        error = attempted && name.trim().length !in 1..20)
    Text("그룹 멤버에게 이렇게 보여요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AuthField("이메일", email, { email = it }, "signupEmail", !state.isLoading, email = true,
        error = attempted && !AuthValidation.email(email))
    AuthField("비밀번호", password, { password = it }, "signupPassword", !state.isLoading, password = true,
        error = attempted && !AuthValidation.password(password))
    Text("8~128자 · 숫자 포함 · 특수문자 포함 · 공백 제외", style = MaterialTheme.typography.bodySmall,
        color = if (password.isNotEmpty() && !AuthValidation.password(password)) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant)
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    AuthField("비밀번호 확인", confirmation, { confirmation = it }, "signupConfirmation", !state.isLoading,
        password = true, error = mismatch)
    if (mismatch) Text("비밀번호가 서로 달라요", color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
    Text("입력한 이메일로 인증 링크를 보내드려요. 이메일 인증을 마치면 회원가입이 완료돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (attempted && validationError != null) {
        Text(validationError, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("signupValidationError"))
    }
    PrimaryAction(if (state.isLoading) "인증 메일 요청 중…" else "인증 메일 보내기", !state.isLoading) {
        attempted = true
        focusManager.clearFocus()
        if (validationError == null) submit(name, email, password, confirmation)
    }
}

@Composable
private fun ResetContent(state: AuthUiState, navigate: (AuthPage) -> Unit, reset: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    BackHeader("비밀번호 찾기", !state.isLoading) { navigate(AuthPage.LOGIN) }
    Text("비밀번호를 다시 설정해요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("가입한 이메일로 재설정 링크를 보내드려요. 메일에서 새 비밀번호를 설정한 뒤 로그인해 주세요.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    AuthField("이메일", email, { email = it }, "resetEmail", !state.isLoading, email = true)
    PrimaryAction(if (state.resetCooldown > 0) "${state.resetCooldown}초 후 다시 보내기" else "재설정 링크 보내기",
        !state.isLoading && state.resetCooldown == 0 && AuthValidation.email(email)) { reset(email) }
    TextButton(onClick = { navigate(AuthPage.LOGIN) }, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        Text("로그인으로 돌아가기")
    }
}

@Composable
private fun VerificationContent(state: AuthUiState, signOut: () -> Unit, resend: () -> Unit, check: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    SignupStep(2, "이메일 인증")
    Text("이메일 인증을 완료해 주세요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(state.pendingEmail.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("메일의 인증 링크를 누른 뒤 이 화면으로 돌아와 주세요. 인증이 확인되면 회원가입이 완료돼요.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryAction("인증 여부 확인", !state.isLoading, check)
    OutlinedButton(onClick = resend, enabled = !state.isLoading && state.verificationCooldown == 0,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
        Text(if (state.verificationCooldown > 0) "${state.verificationCooldown}초 후 다시 보내기" else "인증 메일 다시 보내기")
    }
    Text("메일이 보이지 않으면 스팸함을 확인해 주세요.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = signOut, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
        Text("다른 계정으로 로그인")
    }
}

@Composable
private fun SignupCompleteContent(state: AuthUiState, continueToApp: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    SignupStep(3, "가입 완료")
    Text("가입이 완료됐어요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("이메일 인증을 마쳤어요. DOGS에 오신 것을 환영해요!",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(state.user?.email.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("이제 보호자와 반려견 정보를 설정하고 함께 돌봄을 시작해 보세요.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryAction("돌봄 시작하기", !state.isLoading, continueToApp)
}

@Composable
private fun SignupStep(number: Int, title: String) {
    Text("$number / 3 · $title", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun BackHeader(title: String, enabled: Boolean, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back, enabled = enabled, contentPadding = PaddingValues(end = 12.dp)) { Text("‹ 뒤로") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthField(label: String, value: String, change: (String) -> Unit, tag: String, enabled: Boolean,
                      email: Boolean = false, password: Boolean = false, error: Boolean = false) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = value, onValueChange = change, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(12.dp),
            isError = error,
            keyboardOptions = KeyboardOptions(keyboardType = when {
                password -> KeyboardType.Password; email -> KeyboardType.Email; else -> KeyboardType.Text
            }),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (password) ({ TextButton(onClick = { visible = !visible }, enabled = enabled) {
                Text(if (visible) "숨기기" else "보기", style = MaterialTheme.typography.labelMedium)
            } }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(28.dp)) { Text(label) }
}

@Preview(showBackground = true)
@Composable
private fun SignUpPreview() { DOGSTheme { LoginScreen(AuthUiState(page = AuthPage.SIGN_UP), {}, {}) } }
