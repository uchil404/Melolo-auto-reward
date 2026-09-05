package com.melolo.helper.melolo
import android.view.accessibility.AccessibilityNodeInfo
import com.melolo.helper.VerificationEngine

class DailyTaskManager(private val state: DailyTaskState){
    enum class Phase{DISCOVER,CLASSIFY,READY,RUNNING,VERIFYING,COMPLETED,SKIPPED,FAILED,UNKNOWN,SECURITY_STOP}
    fun runOne(root: AccessibilityNodeInfo?, verify: (()-> com.melolo.helper.VerificationEngine.VerificationResult)? = null): String{
        val cs=MeloloTaskDetector.detect(root)
        if(cs.isEmpty()) return "NO_REWARD"
        val c=cs.first()
        if(c.type==MeloloTaskType.SECURITY_STOP) return "SECURITY_STOP"
        if(c.type==MeloloTaskType.CHECK_IN_TOMORROW) return "SKIPPED"
        if(c.type==MeloloTaskType.ALREADY_COMPLETED) return "ALREADY_COMPLETED"
        val vr=verify?.invoke()
        if(vr!=null && vr.verdict== com.melolo.helper.VerificationEngine.Verdict.UNKNOWN) return "UNKNOWN"
        if(vr!=null && vr.verdict== com.melolo.helper.VerificationEngine.Verdict.FAILURE) return "FAILED"
        state.tasks[c.type.name]="COMPLETED"
        return "COMPLETED"
    }
    fun isAllDone(): Boolean = state.tasks.isNotEmpty() && state.tasks.values.all{it=="COMPLETED"}
}
data class DailyTaskState(val date:String, val tasks:MutableMap<String,String> = mutableMapOf())
