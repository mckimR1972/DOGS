package com.teamdogs.dogs

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import com.teamdogs.dogs.auth.*
import com.teamdogs.dogs.ui.auth.LoginScreen
import com.teamdogs.dogs.ui.theme.DOGSTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EmailAuthScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loginLinksNavigateToRegistrationAndRecovery() {
        var page = AuthPage.LOGIN
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(), {}, {}, onNavigate = { page = it }) } }
        compose.onNodeWithText("회원가입").performScrollTo().performClick()
        assertEquals(AuthPage.SIGN_UP, page)
        compose.onNodeWithText("비밀번호를 잊으셨나요?").performScrollTo().performClick()
        assertEquals(AuthPage.RESET_PASSWORD, page)
    }
    @Test fun registrationValidatesConfirmationAndSubmitsEnteredData() {
        var submitted: List<String>? = null
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(page = AuthPage.SIGN_UP), {}, {},
            onSignUp = { n, e, p, c -> submitted = listOf(n, e, p, c) }) } }
        capture("signup")
        compose.onNodeWithTag("signupName").performTextInput("민지")
        compose.onNodeWithTag("signupEmail").performTextInput("minji@example.com")
        compose.onNodeWithTag("signupPassword").performScrollTo().performTextInput("Dogs123!")
        compose.onNodeWithTag("signupConfirmation").performScrollTo().performTextInput("wrong")
        compose.onNodeWithText("인증 메일 보내기").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("signupValidationError").performScrollTo().assertTextEquals("비밀번호가 서로 달라요.")
        assertNull(submitted)
        compose.onNodeWithTag("signupConfirmation").performScrollTo().performTextReplacement("Dogs123!")
        compose.onNodeWithText("인증 메일 보내기").performScrollTo().performClick()
        assertEquals(listOf("민지", "minji@example.com", "Dogs123!", "Dogs123!"), submitted)
    }
    @Test fun registrationExplainsInvalidEmailAndPasswordWithoutSubmitting() {
        var submissions = 0
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(page = AuthPage.SIGN_UP), {}, {},
            onSignUp = { _, _, _, _ -> submissions++ }) } }
        compose.onNodeWithTag("signupName").performTextInput("민지")
        compose.onNodeWithTag("signupEmail").performTextInput("minji")
        compose.onNodeWithTag("signupPassword").performScrollTo().performTextInput("Dogs1234")
        compose.onNodeWithTag("signupConfirmation").performScrollTo().performTextInput("Dogs1234")
        compose.onNodeWithText("인증 메일 보내기").performScrollTo().performClick()
        compose.onNodeWithTag("signupValidationError").performScrollTo().assertTextEquals("이메일 주소를 확인해 주세요.")
        compose.onNodeWithTag("signupEmail").performScrollTo().performTextReplacement("minji@example.com")
        compose.onNodeWithText("인증 메일 보내기").performScrollTo().performClick()
        compose.onNodeWithTag("signupValidationError").performScrollTo()
            .assertTextEquals("비밀번호는 공백 없이 8~128자, 숫자와 특수문자를 포함해 주세요.")
        assertEquals(0, submissions)
    }
    @Test fun unverifiedSessionOnlyOffersVerificationOrSwitchAccount() {
        var checks = 0
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(pendingEmail = "a@example.com", verificationCooldown = 30),
            {}, {}, onCheckVerification = { checks++ }) } }
        capture("verification")
        compose.onNodeWithText("이메일 인증을 완료해 주세요").assertIsDisplayed()
        compose.onNodeWithText("Google로 로그인").assertDoesNotExist()
        compose.onNodeWithText("30초 후 다시 보내기").assertIsNotEnabled()
        compose.onNodeWithText("인증 여부 확인").performClick(); assertEquals(1, checks)
    }
    @Test fun passwordResetValidatesAddressAndSubmits() {
        var address = ""
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(page = AuthPage.RESET_PASSWORD), {}, {},
            onResetPassword = { address = it }) } }
        capture("reset")
        compose.onNodeWithTag("resetEmail").performTextInput("invalid")
        compose.onNodeWithText("재설정 링크 보내기").assertIsNotEnabled()
        compose.onNodeWithTag("resetEmail").performTextReplacement("minji@example.com")
        compose.onNodeWithText("재설정 링크 보내기").performScrollTo().performClick()
        assertEquals("minji@example.com", address)
    }
    @Test fun signupCompletionOffersContinueOnlyAfterVerification() {
        var continued = false
        compose.setContent { DOGSTheme { LoginScreen(AuthUiState(
            user = SignedInUser("민지", "minji@example.com", "uid"), signupComplete = true), {}, {},
            onContinueAfterSignup = { continued = true }) } }
        compose.onNodeWithText("가입이 완료됐어요").assertIsDisplayed()
        compose.onNodeWithText("3 / 3 · 가입 완료").assertIsDisplayed()
        compose.onNodeWithText("인증 메일 보내기").assertDoesNotExist()
        capture("signup-complete")
        compose.onNodeWithText("돌봄 시작하기").performScrollTo().performClick()
        assertTrue(continued)
    }
    private fun capture(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "auth-screenshots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
