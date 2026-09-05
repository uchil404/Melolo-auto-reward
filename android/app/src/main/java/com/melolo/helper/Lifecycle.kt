package com.melolo.helper
import android.view.accessibility.AccessibilityNodeInfo

/** Lifecycle: LIST -> ACTION -> DONE + endpoint mapping observability */
object TaskLifecycle {
    enum class Phase { LIST, ACTION, DONE }
    data class Task(val id: String, val phase: Phase, val node: AccessibilityNodeInfo?)
    // Dipanggil dari AutomationEngine: kumpulkan list, pilih action, tandai done
    fun collect(root: AccessibilityNodeInfo?, depth: Int = 0): List<Task> {
        if (root == null) return emptyList()
        val out = mutableListOf<Task>()
        scan(root, out)
        return out
    }
    private fun scan(n: AccessibilityNodeInfo, out: MutableList<Task>) {
        val txt = (n.text?.toString() ?: "").lowercase()
        if (n.isClickable && (txt.contains("claim")||txt.contains("klaim")||txt.contains("collect"))) {
            out.add(Task(n.viewIdResourceName ?: txt, Phase.ACTION, n))
        } else if (txt.contains("claimed")||txt.contains("diklaim")) {
            out.add(Task(n.viewIdResourceName ?: txt, Phase.DONE, n))
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { scan(it, out) }
    }
}

/** Endpoint mapping hanya untuk observability - tidak memanggil API */
object EndpointMap {
    data class Endpoint(val name: String, val path: String, val method: String)
    val all = listOf(
        Endpoint("check_in","/api/reward/checkin","POST"),
        Endpoint("claim","/api/reward/claim","POST"),
        Endpoint("ad_reward","/api/reward/ad","POST"),
        Endpoint("list","/api/reward/list","GET")
    )
    fun logFor(task: String) = all.firstOrNull { task.contains(it.name, true) }
}

/** Login-required detector: butuh login Google lagi */
object LoginRequiredDetector {
    private val kw = listOf("login","sign in","masuk","session expired","sesi habis","auth")
    fun isRequired(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return kw.any { SnapshotRecorder.record(root).toString().lowercase().contains(it) }
    }
}

/** Reward-Ad lifecycle: ad -> watch -> close -> claim */
object AdLifecycleDetector {
    enum class AdState { NO_AD, AD_SHOWING, AD_WATCHING, AD_CLOSEABLE, AD_DONE }
    fun detect(root: AccessibilityNodeInfo?): AdState {
        if (root == null) return AdState.NO_AD
        val dump = SnapshotRecorder.record(root).toString().lowercase()
        return when {
            dump.contains("skip") || dump.contains("close") || dump.contains("tutup") -> AdState.AD_CLOSEABLE
            dump.contains("watch") || dump.contains("tonton") -> AdState.AD_SHOWING
            else -> AdState.NO_AD
        }
    }
}
