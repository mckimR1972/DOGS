package com.teamdogs.dogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.teamdogs.dogs.auth.AuthUiState
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.ui.auth.LoginScreen
import com.teamdogs.dogs.ui.theme.DOGSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun signedOutUserCanStartGoogleSignIn() {
        var clicks = 0
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(), { clicks++ }, {}) } }
        compose.onNodeWithText("Google로 로그인").performClick()
        assertEquals(1, clicks)
    }

    @Test fun loadingDisablesRepeatedSignIn() {
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(isLoading = true), {}, {}) } }
        compose.waitUntil(20_000) {
            runCatching { compose.onAllNodesWithText("Google로 로그인").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("Google로 로그인").assertIsNotEnabled()
        compose.onNodeWithText("처리 중이에요…").assertIsDisplayed()
    }

    @Test fun authenticatedUserSeesProfileAndCanSignOut() {
        var clicks = 0
        val user = SignedInUser("테스트 보호자", "test@example.com", "test-uid")
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(user = user), {}, { clicks++ }) } }
        compose.onNodeWithText("test@example.com").assertIsDisplayed()
        compose.onNodeWithText("test-uid").assertIsDisplayed()
        compose.onNodeWithText("로그아웃").performClick()
        assertEquals(1, clicks)
    }

    @Test fun failedSignInShowsMessageAndAllowsRetry() {
        var clicks = 0
        compose.setContent {
            DOGSTheme { LoginScreen(AuthUiState(message = "다시 시도해 주세요."), { clicks++ }, {}) }
        }
        compose.onNodeWithText("다시 시도해 주세요.").assertIsDisplayed()
        compose.onNodeWithText("Google로 로그인").performClick()
        assertEquals(1, clicks)
    }
}
