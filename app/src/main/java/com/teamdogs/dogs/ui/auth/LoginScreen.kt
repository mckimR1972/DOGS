package com.teamdogs.dogs.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.teamdogs.dogs.auth.AuthUiState
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.ui.theme.DOGSTheme

@Composable
fun LoginScreen(state: AuthUiState, onSignIn: () -> Unit, onSignOut: () -> Unit, onPreview: (() -> Unit)? = null) {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DOGS", style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text("함께 돌보는 우리 강아지", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("작은 돌봄을 함께 나누고, 소중한 하루를 기록해요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val user = state.user
                        if (user == null) {
                            Text("반가워요!", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("Google 계정으로 DOGS를 시작해 보세요.")
                            OutlinedButton(
                                onClick = onSignIn,
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Text("Google로 로그인", modifier = Modifier.padding(vertical = 6.dp))
                            }
                        } else {
                            Text("로그인 완료", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("${user.name}님, 반가워요!")
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(user.email.ifBlank { "이메일 정보 없음" })
                                    Text("회원 ID (UID)", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(user.uid, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(onClick = onSignOut, enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth()) {
                                Text("로그아웃", modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("처리 중이에요…", style = MaterialTheme.typography.bodySmall)
                        }
                        state.message?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.user == null && onPreview != null) {
                    TextButton(onClick = onPreview, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                        Text("샘플로 둘러보기")
                    }
                    Text("실제 로그인 없이 임시 화면을 체험할 수 있어요.", style = MaterialTheme.typography.bodySmall)
                }
                Text("로그인 연결을 확인하는 테스트 화면입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    DOGSTheme { LoginScreen(AuthUiState(), {}, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignedInPreview() {
    DOGSTheme {
        LoginScreen(AuthUiState(user = SignedInUser("보호자", "dogs@example.com", "preview-user")), {}, {})
    }
}
