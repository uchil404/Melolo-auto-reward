package com.melolo.helper.melolo
import android.view.accessibility.AccessibilityNodeInfo
import com.melolo.helper.VerificationEngine

class DailyTaskManager(private val state: DailyTaskState){
    enum class Phase{DISCOVER,CLASSIFY,READY,RUNNING,VERIFYING,COMPLETED,SKIPPED,FAILED,UNKNOWN,SECURITY_STOP}
    fun runOne(root: AccessibilityNodeInfo?): String{
        val cs=MeloloTaskDetector.detect(root)
        if(cs.isEmpty()) return "NO_REWARD"
        val c=cs.first()
        if(c.type==MeloloTaskType.SECURITY_STOP) return "SECURITY_STOP"
        if(c.type==MeloloTaskType.CHECK_IN_TOMORROW) return "SKIPPED"
        if(c.type==MeloloTaskType.ALREADY_COMPLETED) return "ALREADY_COMPLETED"
        // CLICK -> VERIFY -> COMPLETED (jangan anggap click=done)
        // observasi via VerificationEngine 3-state
        return c.type.name
    }
}
data class DailyTaskState(val date:String, val tasks:MutableMap<String,String> = mutableMapOf())
