package com.teamdogs.dogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.teamdogs.dogs.ui.components.DogsTextField
import com.teamdogs.dogs.ui.theme.DOGSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DogsTextFieldTest {
    @get:Rule val compose = createComposeRule()

    // The label is drawn above the field; it must still identify the editable node for TalkBack and text selectors.
    @Test fun visibleLabelFindsTheEditableField() {
        var typed = ""
        compose.setContent {
            DOGSTheme {
                var value by remember { mutableStateOf("") }
                DogsTextField("보호자 이름", value, { value = it; typed = it }, helper = "그룹 멤버에게 이렇게 보여요")
            }
        }
        compose.onNodeWithText("보호자 이름").performTextInput("민지")
        compose.runOnIdle { assertEquals("민지", typed) }
    }
}
