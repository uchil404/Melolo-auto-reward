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

    /** Claim: tombol klaim/collect/ambil — hanya yang verdict CLICK boleh diklik. */
    fun findClaim(
        root: AccessibilityNodeInfo,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String>,
        w: Weights = Weights()
    ): List<ScoredNode> = scoreAll(
        NodeFinder.findClickableByKeywords(root, claimKeywords, resourceIdPatterns),
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
