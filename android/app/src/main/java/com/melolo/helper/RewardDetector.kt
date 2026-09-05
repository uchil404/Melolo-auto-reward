package com.melolo.helper

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Scores candidate nodes for reward/claim relevance using a
 * configurable confidence system. Only nodes above the threshold
 * are considered actionable.
 *
 * Scoring:
 *   resource-id match  = +50
 *   contentDescription = +30
 *   exact text         = +30
 *   keyword match      = +15
 *   clickable          = +10
 *   enabled            = +10
 */
object RewardDetector {

    data class Candidate(
        val node: AccessibilityNodeInfo,
        val text: String,
        val contentDescription: String,
        val resourceId: String,
        val className: String,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val confidence: Int,
        val matchReasons: List<String>
    )

    var confidenceThreshold: Int = 70

    /**
     * Detect reward-related nodes in the tree.
     * Returns candidates sorted by confidence (highest first).
     */
    fun detectRewards(
        root: AccessibilityNodeInfo?,
        rewardKeywords: List<String>,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String> = emptyList()
    ): List<Candidate> {
        if (root == null) return emptyList()

        val allKeywords = rewardKeywords + claimKeywords
        val candidates = mutableListOf<Candidate>()
        scanNodeRecursive(root, allKeywords, rewardKeywords, claimKeywords, resourceIdPatterns, 0, candidates)

        return candidates
            .filter { it.confidence >= confidenceThreshold }
            .sortedByDescending { it.confidence }
    }

    /**
     * Detect only claim buttons (not reward sections).
     */
    fun detectClaimButtons(
        root: AccessibilityNodeInfo?,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String> = emptyList()
    ): List<Candidate> {
        if (root == null) return emptyList()

        val candidates = mutableListOf<Candidate>()
        scanForClaimButtons(root, claimKeywords, resourceIdPatterns, 0, candidates)

        return candidates
            .filter { it.confidence >= confidenceThreshold }
            .sortedByDescending { it.confidence }
    }

    private fun scanNodeRecursive(
        node: AccessibilityNodeInfo,
        allKeywords: List<String>,
        rewardKeywords: List<String>,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String>,
        depth: Int,
        candidates: MutableList<Candidate>
    ) {
        if (depth > 30) return

        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val resId = node.viewIdResourceName ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: ""

        var confidence = 0
        val reasons = mutableListOf<String>()

        // Resource-id match: +50
        for (pattern in resourceIdPatterns) {
            if (resId.lowercase().contains(pattern.lowercase())) {
                confidence += 50
                reasons.add("resource-id matches '$pattern'")
                break
            }
        }

        // ContentDescription match: +30
        val contentDescLower = contentDesc.lowercase()
        for (keyword in allKeywords) {
            if (contentDescLower.contains(keyword.lowercase())) {
                confidence += 30
                reasons.add("contentDescription contains '$keyword'")
                break
            }
        }

        // Exact text match: +30
        val textLower = text.lowercase()
        for (keyword in allKeywords) {
            if (textLower == keyword.lowercase()) {
                confidence += 30
                reasons.add("exact text match '$keyword'")
                break
            }
        }

        // Keyword match in text: +15
        if (confidence < 30) { // only if not already matched exactly
            for (keyword in allKeywords) {
                if (textLower.contains(keyword.lowercase())) {
                    confidence += 15
                    reasons.add("text contains '$keyword'")
                    break
                }
            }
        }

        // Clickable: +10
        if (node.isClickable) {
            confidence += 10
            reasons.add("clickable")
        }

        // Enabled: +10
        if (node.isEnabled) {
            confidence += 10
            reasons.add("enabled")
        }

        if (confidence > 0) {
            candidates.add(
                Candidate(
                    node = node,
                    text = text,
                    contentDescription = contentDesc,
                    resourceId = resId,
                    className = className,
                    isClickable = node.isClickable,
                    isEnabled = node.isEnabled,
                    confidence = confidence,
                    matchReasons = reasons
                )
            )
        }

        // Recurse children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                scanNodeRecursive(
                    child, allKeywords, rewardKeywords, claimKeywords,
                    resourceIdPatterns, depth + 1, candidates
                )
            }
        }
    }

    private fun scanForClaimButtons(
        node: AccessibilityNodeInfo,
        claimKeywords: List<String>,
        resourceIdPatterns: List<String>,
        depth: Int,
        candidates: MutableList<Candidate>
    ) {
        if (depth > 30) return

        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val resId = node.viewIdResourceName ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: ""

        var confidence = 0
        val reasons = mutableListOf<String>()

        // Resource-id match: +50
        for (pattern in resourceIdPatterns) {
            if (resId.lowercase().contains(pattern.lowercase())) {
                confidence += 50
                reasons.add("resource-id matches '$pattern'")
                break
            }
        }

        // ContentDescription contains claim keyword: +30
        val contentDescLower = contentDesc.lowercase()
        for (keyword in claimKeywords) {
            if (contentDescLower.contains(keyword.lowercase())) {
                confidence += 30
                reasons.add("contentDescription contains '$keyword'")
                break
            }
        }

        // Exact text match with claim keyword: +30
        val textLower = text.lowercase()
        for (keyword in claimKeywords) {
            if (textLower == keyword.lowercase()) {
                confidence += 30
                reasons.add("exact text match '$keyword'")
                break
            }
        }

        // Keyword match in text: +15
        if (confidence < 30) {
            for (keyword in claimKeywords) {
                if (textLower.contains(keyword.lowercase())) {
                    confidence += 15
                    reasons.add("text contains '$keyword'")
                    break
                }
            }
        }

        // Clickable: +10
        if (node.isClickable) {
            confidence += 10
            reasons.add("clickable")
        }

        // Enabled: +10
        if (node.isEnabled) {
            confidence += 10
            reasons.add("enabled")
        }

        if (confidence > 0) {
            candidates.add(
                Candidate(
                    node = node,
                    text = text,
                    contentDescription = contentDesc,
                    resourceId = resId,
                    className = className,
                    isClickable = node.isClickable,
                    isEnabled = node.isEnabled,
                    confidence = confidence,
                    matchReasons = reasons
                )
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                scanForClaimButtons(child, claimKeywords, resourceIdPatterns, depth + 1, candidates)
            }
        }
    }

    /**
     * Format a candidate for display (test mode / inspect).
     */
    fun formatCandidate(candidate: Candidate, index: Int): String {
        val sb = StringBuilder()
        sb.appendLine("Candidate #${index + 1}")
        sb.appendLine("Text: \"${candidate.text}\"")
        sb.appendLine("Content Description: \"${candidate.contentDescription}\"")
        sb.appendLine("Resource ID: ${candidate.resourceId.ifEmpty { "N/A" }}")
        sb.appendLine("Class: ${candidate.className}")
        sb.appendLine("Clickable: ${candidate.isClickable}")
        sb.appendLine("Enabled: ${candidate.isEnabled}")
        sb.appendLine("Confidence: ${candidate.confidence}%")
        sb.appendLine("Reasons: ${candidate.matchReasons.joinToString(", ")}")
        sb.appendLine("Action: ${if (candidate.confidence >= confidenceThreshold) "WOULD CLICK" else "WOULD SKIP (below threshold)"}")
        return sb.toString()
    }
}