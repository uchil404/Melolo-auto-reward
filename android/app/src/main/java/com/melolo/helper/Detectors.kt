package com.melolo.helper
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/** P1 sisa + P2 */
object RewardDetectors {
    fun chest(root: AccessibilityNodeInfo?): Boolean =
        SnapshotRecorder.record(root).toString().lowercase().contains("chest") ||
        SnapshotRecorder.record(root).toString().lowercase().contains("peti")
    fun adReward(root: AccessibilityNodeInfo?): Boolean =
        AdLifecycleDetector.detect(root) != AdLifecycleDetector.AdState.NO_AD
    fun amount(root: AccessibilityNodeInfo?): String? =
        Regex("""(\d+)\s*(coin|koin)""", RegexOption.IGNORE_CASE).find(SnapshotRecorder.record(root).toString())?.value
}

object TaskListObserver {
    fun list(root: AccessibilityNodeInfo?): List<String> {
        val j = SnapshotRecorder.record(root)
        return j.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("text")?.takeIf { s -> s.isNotBlank() } }
        } ?: emptyList()
    }
    fun typeMap(text: String): TaskKind = TaskClassifier.classify(
        object: AccessibilityNodeInfo(null,0){ override fun getText()=text as CharSequence }
    ).kind
}

// P2 deeper call-site: mapping kelas/metode -> endpoint (observasi statis, update via jadx)
object CallSiteMap {
    data class Site(val clazz: String, val method: String, val endpoint: String)
    val sites = listOf(
        Site("com.worldance.drama.reward","claim","/api/reward/claim"),
        Site("com.worldance.drama.checkin","checkIn","/api/reward/checkin"),
        Site("com.worldance.drama.chest","openChest","/api/reward/chest"),
        Site("com.worldance.drama.ad","finishAd","/api/reward/ad")
    )
}
