package com.teamdogs.dogs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.teamdogs.dogs.group.*
import com.teamdogs.dogs.ui.theme.DOGSTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class CareRecordScreenTest {
    @get:Rule val compose=createComposeRule()
    private fun group()=GroupSnapshot(User(id="me"),Group(id="g",leader_id="me"),emptyList(),listOf(Dog(id="dog",name="모카")),emptyList(),emptyList(),"2026-11-16")
    @Test fun pastRecordDefaultsToCommonAndUsesActualTime(){
        var saved:CareRecord?=null
        compose.setContent{DOGSTheme{CareRecordDialog(group(),"me","",false,null,{}, {saved=it})}}
        compose.onNodeWithText("어떤 돌봄을 했나요?").performTextInput("간식 줬어요")
        compose.onNodeWithText("한 날짜 YYYY-MM-DD").performTextReplacement("2026-11-15")
        compose.onNodeWithText("한 시각 HH:mm").performTextReplacement("12:30")
        compose.onNodeWithText("기록하기").performClick()
        compose.runOnIdle{
            assertEquals("간식 줬어요",saved?.title);assertNull(saved?.dog_id);assertEquals("me",saved?.done_by)
            assertEquals("2026-11-15T12:30",saved!!.done_at!!.toDate().toInstant().atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime().toString())
        }
    }
    @Test fun invalidDateCannotCreateRecordOrCrash(){
        var saved:CareRecord?=null
        compose.setContent{DOGSTheme{CareRecordDialog(group(),"me","",false,null,{}, {saved=it})}}
        compose.onNodeWithText("어떤 돌봄을 했나요?").performTextInput("간식")
        compose.onNodeWithText("한 날짜 YYYY-MM-DD").performTextReplacement("0000-01-01")
        compose.onNodeWithText("기록하기").performClick()
        compose.onNodeWithText("내용과 실제로 한 날짜·시각을 확인해 주세요. 미래 시각은 기록할 수 없어요.").assertExists()
        compose.runOnIdle{assertNull(saved)}
    }
}
