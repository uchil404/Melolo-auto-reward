package com.melolo.helper
import android.view.accessibility.AccessibilityNodeInfo

/** Watchdog berbasis perubahan UI + recovery berdasarkan state (Batch2) */
object UiWatchdog {
    private var lastHash: String = ""
    private var unchangedCount = 0
    fun onSnapshot(json: org.json.JSONObject): String {
        val h = json.toString().hashCode().toString()
        if (h == lastHash) unchangedCount++ else { lastHash = h; unchangedCount = 0 }
        return if (unchangedCount >= 3) "STUCK" else "OK"
    }
    fun recoveryFor(state: AutomationState): String = when(state) {
        AutomationState.FIND_REWARD, AutomationState.FIND_CLAIM -> "rescan"
        AutomationState.CLICK_CLAIM, AutomationState.WAIT_RESULT -> "retry"
        AutomationState.VERIFY_SUCCESS -> "recheck_ui"
        else -> "restart"
    }
}
