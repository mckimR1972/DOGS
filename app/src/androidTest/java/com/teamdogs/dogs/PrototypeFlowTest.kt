package com.teamdogs.dogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.teamdogs.dogs.prototype.*
import com.teamdogs.dogs.ui.theme.DOGSTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import androidx.exifinterface.media.ExifInterface

private class TestCareStore(var value: CareState? = null) : CareStore {
    override fun load() = value
    override fun save(state: CareState) { value = state }
}

class PrototypeFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeCompletionUpdatesTodoAndPetFilter() {
        val controller = CareController(TestCareStore(), true)
        compose.setContent { DOGSTheme { PrototypeApp("test", true, "", false, {}, {}, {}, controller) } }
        compose.onNodeWithTag("care-progress").assertTextContains("4개를 마쳤어요", substring = true)
        compose.onNodeWithTag("complete-next").performClick()
        compose.onNodeWithTag("care-progress").assertTextContains("5개를 마쳤어요", substring = true)
        compose.onNodeWithTag("tab-1").performClick()
        compose.onAllNodesWithText("콩이").onFirst().performClick()
        compose.onNodeWithText("3 / 3 완료").assertIsDisplayed()
    }

    @Test fun allTabsAreReachableWithoutGoogleOrAutomaticPermissionPrompt() {
        var requests = 0
        val controller = CareController(TestCareStore(), true)
        compose.setContent { DOGSTheme { PrototypeApp("test", true, "", false, { requests++ }, {}, {}, controller) } }
        compose.onNodeWithTag("tab-2").performClick()
        compose.onNodeWithText("우리의 사진첩").assertIsDisplayed()
        compose.onNodeWithTag("tab-3").performClick()
        compose.onNodeWithText("함께 돌봐요").assertIsDisplayed()
        assertEquals(0, requests)
        compose.onNodeWithText("알림 권한 설정").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test fun onboardingValidatesNamesAndCanFinishWithoutNotifications() {
        val controller = CareController(TestCareStore(), false)
        compose.setContent { DOGSTheme { PrototypeApp("new", false, "", false, {}, {}, {}, controller) } }
        compose.onNodeWithTag("setup-next").performClick()
        compose.onNodeWithText("보호자 이름을 입력해 주세요.").assertIsDisplayed()
        compose.onNodeWithTag("setup-nickname").performTextInput("민지")
        compose.onNodeWithTag("setup-next").performScrollTo().performClick()
        // Wait for the next step to attach after the focused text field is removed.
        compose.waitUntil(20_000) {
            runCatching { compose.onAllNodesWithTag("setup-pet").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithTag("setup-pet").performTextInput("초코")
        compose.onNodeWithTag("setup-next").performScrollTo().performClick()
        compose.onNodeWithTag("setup-next").performScrollTo().performClick()
        compose.onNodeWithText("알림 받기는 선택 사항이에요.").assertIsDisplayed()
        compose.onNodeWithTag("setup-next").performScrollTo().performClick()
        compose.onNodeWithTag("tab-0").assertIsDisplayed()
        compose.runOnIdle { assertTrue(controller.state.ready); assertEquals(3, controller.state.tasks.size) }
    }

    @Test fun localStoresAreSeparatedByAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstKey = "test_${UUID.randomUUID()}"
        val secondKey = "test_${UUID.randomUUID()}"
        try {
            val first = LocalCareStore(context, firstKey)
            first.save(sampleCareState())
            assertEquals(6, first.load()!!.tasks.size)
            assertNull(LocalCareStore(context, secondKey).load())
        } finally {
            context.deleteSharedPreferences("dogs_prototype_$firstKey")
            context.deleteSharedPreferences("dogs_prototype_$secondKey")
        }
    }

    @Test fun photoImportResizesAndHonorsPortraitOrientation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "test_${UUID.randomUUID()}"
        val source = File(context.cacheDir, "$key.jpg")
        val bitmap = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val path = importLocalPhoto(context, Uri.fromFile(source), key)
            assertTrue(File(path).length() > 0)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            assertEquals(800, options.outWidth)
            assertEquals(1600, options.outHeight)
        } finally {
            source.delete()
            File(context.filesDir, "prototype_photos/$key").deleteRecursively()
        }
    }
}
