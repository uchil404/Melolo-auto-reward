package com.melolo.helper
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/** P0+P1: snapshot before/after + amount + chest/ad + task completion (observasi) */
object RewardVerifier {
    data class Before(val json: JSONObject, val amount: String?, val claimedSet: Set<String>)
    fun captureBefore(root: AccessibilityNodeInfo?): Before {
        val j = SnapshotRecorder.record(root)
        val txt = j.toString()
        val amt = Regex("""\d+\s*(coin|koin)?""", RegexOption.IGNORE_CASE).find(txt)?.value
        val claimed = Regex("claimed|diklaim|completed", RegexOption.IGNORE_CASE).findAll(txt).map { it.value.lowercase() }.toSet()
        return Before(j, amt, claimed)
    }
    fun verify(before: Before, afterRoot: AccessibilityNodeInfo?): String {
        val after = SnapshotRecorder.record(afterRoot)
        val changed = before.json.toString() != after.toString()
        val afterTxt = after.toString().lowercase()
        val dup = before.claimedSet.any { afterTxt.contains(it) }
        val amtAfter = Regex("""\d+""").find(afterTxt)?.value
        val amtChanged = amtAfter != before.amount?.let { Regex("""\d+""").find(it)?.value }
        return "changed=$changed amtChanged=$amtChanged dup=$dup"
    }
}
object AdRewardTracker {
    var startMs: Long = 0
    var videoLengthMs: Long = 0 // P1: metadata observasi saja, tidak memalsukan
    fun onAdStart(lenMs: Long) { startMs = System.currentTimeMillis(); videoLengthMs = lenMs }
    fun watchedMs(): Long = if (startMs==0L) 0 else System.currentTimeMillis()-startMs
    fun isFinished(timeoutMs: Long = 60000): Boolean = watchedMs() >= timeoutMs || watchedMs() >= videoLengthMs
}
