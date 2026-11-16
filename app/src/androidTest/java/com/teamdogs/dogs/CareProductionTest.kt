package com.teamdogs.dogs

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.teamdogs.dogs.auth.SignedInUser
import com.teamdogs.dogs.group.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Date
import java.util.UUID

class CareProductionTest {
    @Test fun manualRecordAndOptionalTimeRoundTrip()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("productionCareSmoke")=="true")
        val user=FirebaseAuth.getInstance().currentUser
        assumeTrue("로그인된 테스트 기기에서만 실행",user!=null)
        val repo=FirestoreGroupRepository(SignedInUser("테스트","",user!!.uid))
        val group=repo.load()!!;val id="test-${UUID.randomUUID()}"
        val record=CareRecord(id,group.group.id,"테스트용 돌봄 기록",null,user.uid,Timestamp(Date(System.currentTimeMillis()-60000)))
        val day=record.done_at!!.toDate().toInstant().atZone(java.time.ZoneId.of(group.group.timezone)).toLocalDate().toString()
        try{
            repo.saveCareRecord(record);repo.saveCareRecord(record)
            assertEquals(1,repo.careHistory(group.group.id,day,group.group.timezone).count{it.id==id})
            if(group.group.leader_id==user.uid){
                repo.saveTodo(Todo(id,group.group.id,null,"테스트용 시간 미정",RepeatRule(),"",user.uid))
                assertEquals("",repo.load()!!.todos.first{it.id==id}.due_at)
                repo.setCompleted(group.group.id,id,group.day,true)
                assertTrue(repo.careHistory(group.group.id,group.day,group.group.timezone).any{it.todoId==id && it.title=="테스트용 시간 미정"})
            }
        }finally{
            repo.deleteCareRecord(group.group.id,id)
            if(group.group.leader_id==user.uid){repo.setCompleted(group.group.id,id,group.day,false);repo.deleteTodo(group.group.id,id)}
        }
        assertTrue(repo.careHistory(group.group.id,day,group.group.timezone).none{it.id==id || it.todoId==id})
    }
}
