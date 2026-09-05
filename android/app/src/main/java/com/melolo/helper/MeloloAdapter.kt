package com.melolo.helper

import android.view.accessibility.AccessibilityNodeInfo

/**
 * MeloloAdapter — satu-satunya lapisan yang tahu tentang UI spesifik
 * com.worldance.drama (Melolo/Drama short app).
 *
 * AutomationEngine -> MeloloAdapter -> { Home | Reward | Claim } -> NodeFinder
 *
 * Ketika UI Melolo berubah setelah update APK, perbaiki KELAS INI SAJA,
 * bukan engine / state machine / detector generik.
 */
object MeloloAdapter {

    const val PACKAGE = "com.worldance.drama"

    /** Bobot selector — mirror dari termux/snapshot.py agar konsisten. */
    data class Weights(
        val resourceId: Int = 100,
        val viewId: Int = 100,
        val contentDesc: Int = 80,
        val clickable: Int = 40,
        val enabled: Int = 30,
        val visible: Int = 20,
        val keyword: Int = 20,
        val relation: Int = 20,
        val bounds: Int = 10,
        val coordFallback: Int = -50
    )

    enum class Verdict { CLICK, VERIFY, SKIP }

    data class ScoredNode(
        val node: AccessibilityNodeInfo,
        val score: Int,
        val verdict: Verdict,
        val reason: String
    )

    // -- Screen adapters -------------------------------------------------

    /** Home: tab navigasi bawah / tombol masuk ke pusat reward. */
    fun findHomeEntry(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        resourceIdPatterns: List<String>,
        w: Weights = Weights()
    ): List<ScoredNode> = scoreAll(
        NodeFinder.findClickableByKeywords(root, keywords, resourceIdPatterns),
        w
    )

    /** Reward: kartu/hadiah harian, check-in, misi. */
    fun findReward(
        root: AccessibilityNodeInfo,
        rewardKeywords: List<String>,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String>,
        w: Weights = Weights()
    ): List<ScoredNode> {
        val all = NodeFinder.findClickableByKeywords(
            root, rewardKeywords + claimKeywords, resourceIdPatterns
        )
        return scoreAll(all, w)
    }

    /** P0: Resource-ID based Check-in (prioritas tertinggi), pisah dari generic reward */
    val checkInResIds = listOf("check_in","checkin","daily_check","check_in_btn","btn_check_in","hadiah_harian")
    val claimSelectors = listOf("claim","klaim","collect","ambil","reward_claim")
    val amountSelectors = listOf("reward_amount","coin","koin","amount")
    val stateSelectors = listOf("claimed","diklaim","done","completed")
    // P0 TODAY vs TOMORROW, P0 ALREADY_COMPLETED
    fun todayVsTomorrow(root: AccessibilityNodeInfo): String {
        val txt = SnapshotRecorder.record(root).toString().lowercase()
        val today = txt.contains("today") || txt.contains("hari ini")
        val tomorrow = txt.contains("tomorrow") || txt.contains("besok")
        return when { today && !tomorrow -> "TODAY"; tomorrow && !today -> "TOMORROW"; else -> "UNKNOWN" }
    }
    fun isAlreadyCompleted(root: AccessibilityNodeInfo): Boolean =
        SnapshotRecorder.record(root).toString().lowercase().let { t ->
            listOf("already completed","sudah selesai","already claimed","sudah diklaim","completed").any { t.contains(it) }
        }
    // P0 Safety gate sebelum setiap click (Batch F)
    fun safetyGate(node: AccessibilityNodeInfo): Boolean {
        if (isRisk(node)) return false
        if (!node.isEnabled || !node.isVisibleToUser) return false
        return true
    }
    fun isRisk(node: AccessibilityNodeInfo): Boolean {
        val t = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        return listOf("captcha","verify","security","suspicious","robot").any { t.contains(it) }
    }
    fun findCheckIn(root: AccessibilityNodeInfo): List<ScoredNode> {
        val res = NodeFinder.findClickableByKeywords(root, checkInResIds, checkInResIds)
        return scoreAll(res).filter { it.verdict == Verdict.CLICK }
    }

    /** Claim: tombol klaim/collect/ambil — hanya yang verdict CLICK boleh diklik. */
    fun findClaim(
        root: AccessibilityNodeInfo,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String>,
        w: Weights = Weights()
    ): List<ScoredNode> = scoreAll(
        NodeFinder.findClickableByKeywords(root, claimKeywords + claimSelectors, resourceIdPatterns),
        w
    ).filter { it.verdict != Verdict.SKIP }

    // -- Scoring ----------------------------------------------------------

    fun scoreResult(r: NodeFinder.FindResult, w: Weights = Weights()): ScoredNode {
        var s = 0
        val why = StringBuilder()
        val n = r.node
        if (n == null) return ScoredNode(n as AccessibilityNodeInfo, -999, Verdict.SKIP, "null")
        val resId = n.viewIdResourceName ?: ""
        if (resId.isNotEmpty()) { s += w.resourceId; why.append("rid ") }
        if (n.contentDescription?.isNotEmpty() == true) { s += w.contentDesc; why.append("desc ") }
        if (n.isClickable) { s += w.clickable; why.append("click ") }
        if (n.isEnabled) { s += w.enabled; why.append("en ") }
        if (n.isVisibleToUser) { s += w.visible; why.append("vis ") }
        when (r.matchType) {
            NodeFinder.MatchType.RESOURCE_ID -> { s += w.keyword; why.append("rid-match ") }
            NodeFinder.MatchType.CONTENT_DESCRIPTION -> { s += w.keyword; why.append("kw ") }
            NodeFinder.MatchType.TEXT -> { s += w.keyword; why.append("kw ") }
            NodeFinder.MatchType.HIERARCHY -> { s += w.relation; why.append("rel ") }
            NodeFinder.MatchType.COORDINATE_FALLBACK -> { s += w.coordFallback; why.append("fallback ") }
        }
        // confidence lama (0-50) dipetakan ke skala 0-~30 tambahan
        s += (r.confidence / 2)
        val v = when {
            s >= 85 -> Verdict.CLICK
            s >= 70 -> Verdict.VERIFY
            else -> Verdict.SKIP
        }
        return ScoredNode(n, s, v, why.toString().trim())
    }

    private fun scoreAll(
        results: List<NodeFinder.FindResult>,
        w: Weights
    ): List<ScoredNode> = results.map { scoreResult(it, w) }
        .sortedByDescending { it.score }
}
