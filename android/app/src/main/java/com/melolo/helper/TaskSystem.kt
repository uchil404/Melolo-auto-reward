package com.melolo.helper

import android.view.accessibility.AccessibilityNodeInfo

/** Task classification + check-in detector + metadata (Batch 1) */
object TaskClassifier {
    enum class TaskType { CHECK_IN, DAILY_REWARD, AD_REWARD, CLAIMABLE, UNKNOWN }
    data class TaskMeta(val type: TaskType, val claimed: Boolean, val text: String, val amount: String?)

    private val claimedKw = listOf("claimed","diklaim","collected","sudah","received","done","completed")
    private val checkInKw = listOf("check in","check-in","daily","hadiah harian","absen")
    private val adKw = listOf("watch","tonton","video","ad","iklan")
    private val amountRe = Regex("""[+\s]*(\d+)\s*(coin|koin|point|poin)?""", RegexOption.IGNORE_CASE)

    fun classify(node: AccessibilityNodeInfo, ctx: String): TaskMeta {
        val t = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "") + " " + ctx
        val tl = t.lowercase()
        val claimed = claimedKw.any { tl.contains(it) }
        val type = when {
            checkInKw.any { tl.contains(it) } -> TaskType.CHECK_IN
            adKw.any { tl.contains(it) } -> TaskType.AD_REWARD
            tl.contains("reward") || tl.contains("hadiah") -> TaskType.DAILY_REWARD
            else -> TaskType.UNKNOWN
        }
        val m = amountRe.find(t)
        return TaskMeta(type, claimed, t.trim(), m?.groupValues?.get(1))
    }
}

/** Check-in date tracking: jangan klaim 2x di hari sama */
object CheckInTracker {
    private const val PREF = "melolo_checkin"
    private const val KEY = "last_date"
    fun isAlreadyToday(ctx: android.content.Context): Boolean {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return ctx.getSharedPreferences(PREF, 0).getString(KEY, "") == d
    }
    fun markToday(ctx: android.content.Context) {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY, d).apply()
    }
}

/** Claim semantic action + duplicate prevention */
object ClaimAction {
    private val done = mutableSetOf<String>()
    fun canClaim(id: String): Boolean = done.add(id) // false = duplicate
    fun reset() = done.clear()
    fun perform(node: AccessibilityNodeInfo, sm: StateMachine): ClickController.ClickResult {
        val id = (node.viewIdResourceName ?: "") + "|" + (node.text ?: "")
        if (!canClaim(id)) return ClickController.ClickResult(false,false,"duplicate claim blocked: $id")
        return ClickController.safeClick(node, 90, 70, sm)
    }
}
