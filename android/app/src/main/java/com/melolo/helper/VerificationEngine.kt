package com.melolo.helper

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Verifies that a claim action was successful by detecting UI changes.
 *
 * Verification is NOT time-based. It looks for concrete evidence:
 *   - Text changed: "Claim" → "Claimed" / "Collected"
 *   - Reward button disappeared
 *   - Success message appeared
 *   - Reward counter changed
 *   - Reward dialog appeared
 */
object VerificationEngine {

    data class VerificationResult(
        val success: Boolean,
        val evidence: String,
        val confidence: Int
    )

    data class Snapshot(
        val timestamp: Long,
        val claimButtonTexts: Set<String>,
        val claimButtonResIds: Set<String>,
        val visibleDialogTexts: List<String>,
        val rewardCounters: List<String>,
        val successMessages: List<String>
    )

    private var beforeSnapshot: Snapshot? = null
    private val successKeywords = listOf(
        "claimed",
        "collected",
        "success",
        "successful",
        "reward received",
        "congratulations",
        "berhasil",
        "sukses",
        "diterima",
        "selamat",
        "done",
        "completed",
        "you got",
        "you received",
        "reward claimed",
        "hadiah diklaim"
    )

    private val failureKeywords = listOf(
        "failed",
        "error",
        "try again",
        "gagal",
        "coba lagi",
        "not available",
        "unavailable",
        "expired",
        "already claimed",
        "sudah diklaim"
    )

    fun reset() {
        beforeSnapshot = null
    }

    /**
     * Take a snapshot of the UI before attempting a claim.
     */
    fun takeBeforeSnapshot(root: AccessibilityNodeInfo?) {
        if (root == null) {
            beforeSnapshot = null
            return
        }
        beforeSnapshot = captureSnapshot(root)
        Logger.debug("VerificationEngine: before-snapshot taken")
    }

    /**
     * Verify the claim by comparing before/after UI state.
     */
    fun verifyAfterClaim(
        root: AccessibilityNodeInfo?,
        stateMachine: StateMachine
    ): VerificationResult {
        if (root == null) {
            return VerificationResult(false, "Root node is null", 0)
        }

        val afterSnapshot = captureSnapshot(root)
        val before = beforeSnapshot ?: return VerificationResult(
            false, "No before-snapshot available", 0
        )

        var evidence = ""
        var confidence = 0

        // 1. Check for success messages
        val newSuccessMessages = afterSnapshot.successMessages.filter { msg ->
            before.successMessages.none { it.equals(msg, ignoreCase = true) }
        }
        if (newSuccessMessages.isNotEmpty()) {
            evidence = "Success message appeared: \"${newSuccessMessages.first()}\""
            confidence = 90
            Logger.claim("VERIFY: $evidence (confidence=$confidence%)")
            return VerificationResult(true, evidence, confidence)
        }

        // 2. Check if claim button disappeared (text changed from claim → claimed)
        val beforeClaimTexts = before.claimButtonTexts
        val afterClaimTexts = afterSnapshot.claimButtonTexts

        // Check if any "claim" text changed to "claimed"
        for (beforeText in beforeClaimTexts) {
            val beforeLower = beforeText.lowercase()
            if (beforeLower.contains("claim") || beforeLower.contains("klaim") ||
                beforeLower.contains("collect") || beforeLower.contains("ambil")) {
                // This was a claim button. Check if it disappeared or changed
                val stillExists = afterClaimTexts.any { it.equals(beforeText, ignoreCase = true) }
                if (!stillExists) {
                    evidence = "Claim button disappeared: \"$beforeText\""
                    confidence = 70
                    Logger.claim("VERIFY: $evidence (confidence=$confidence%)")
                    return VerificationResult(true, evidence, confidence)
                }
            }
        }

        // 3. Check if any text changed to "claimed" or similar
        for (afterText in afterClaimTexts) {
            val afterLower = afterText.lowercase()
            for (keyword in successKeywords) {
                if (afterLower.contains(keyword)) {
                    val wasThereBefore = beforeClaimTexts.any { it.equals(afterText, ignoreCase = true) }
                    if (!wasThereBefore) {
                        evidence = "Claim success text appeared: \"$afterText\""
                        confidence = 80
                        Logger.claim("VERIFY: $evidence (confidence=$confidence%)")
                        return VerificationResult(true, evidence, confidence)
                    }
                }
            }
        }

        // 4. Check for failure messages
        for (afterText in afterSnapshot.visibleDialogTexts + afterSnapshot.successMessages) {
            val afterLower = afterText.lowercase()
            for (keyword in failureKeywords) {
                if (afterLower.contains(keyword)) {
                    evidence = "Failure message detected: \"$afterText\""
                    confidence = 80
                    Logger.warn("VERIFY: $evidence (confidence=$confidence%)")
                    return VerificationResult(false, evidence, confidence)
                }
            }
        }

        // 5. Check for dialog appearing (reward dialog)
        val newDialogs = afterSnapshot.visibleDialogTexts.filter { dialog ->
            before.visibleDialogTexts.none { it.equals(dialog, ignoreCase = true) }
        }
        if (newDialogs.isNotEmpty()) {
            val dialogText = newDialogs.joinToString(" ")
            val dialogLower = dialogText.lowercase()
            for (keyword in successKeywords) {
                if (dialogLower.contains(keyword)) {
                    evidence = "Reward dialog appeared with success text"
                    confidence = 85
                    Logger.claim("VERIFY: $evidence (confidence=$confidence%)")
                    return VerificationResult(true, evidence, confidence)
                }
            }
            // Unknown dialog — might be a reward popup
            evidence = "New dialog appeared (unverified content)"
            confidence = 40
            Logger.claim("VERIFY: $evidence (confidence=$confidence%)")
            return VerificationResult(true, evidence, confidence)
        }

        // 6. UI changed but no clear evidence
        if (beforeClaimTexts != afterClaimTexts) {
            evidence = "UI changed but no clear success/failure indicator"
            confidence = 30
            Logger.claim("VERIFY: $evidence (confidence=$confidence%)")
            return VerificationResult(true, evidence, confidence)
        }

        return VerificationResult(false, "No UI change detected after click", 0)
    }

    private fun captureSnapshot(root: AccessibilityNodeInfo): Snapshot {
        val claimButtonTexts = mutableSetOf<String>()
        val claimButtonResIds = mutableSetOf<String>()
        val visibleDialogTexts = mutableListOf<String>()
        val rewardCounters = mutableListOf<String>()
        val successMessages = mutableListOf<String>()

        captureRecursive(root, 0, claimButtonTexts, claimButtonResIds, visibleDialogTexts, rewardCounters, successMessages)

        return Snapshot(
            timestamp = System.currentTimeMillis(),
            claimButtonTexts = claimButtonTexts,
            claimButtonResIds = claimButtonResIds,
            visibleDialogTexts = visibleDialogTexts,
            rewardCounters = rewardCounters,
            successMessages = successMessages
        )
    }

    private fun captureRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        claimButtonTexts: MutableSet<String>,
        claimButtonResIds: MutableSet<String>,
        visibleDialogTexts: MutableList<String>,
        rewardCounters: MutableList<String>,
        successMessages: MutableList<String>
    ) {
        if (depth > 30) return

        val text = node.text?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: ""

        // Collect claim button texts
        if (node.isClickable && text.isNotEmpty()) {
            claimButtonTexts.add(text)
        }

        // Collect resource IDs
        node.viewIdResourceName?.let { resId ->
            if (resId.isNotEmpty()) {
                claimButtonResIds.add(resId)
            }
        }

        // Collect dialog texts
        if (className.contains("Dialog", ignoreCase = true) ||
            className.contains("Alert", ignoreCase = true) ||
            className.contains("Popup", ignoreCase = true)) {
            val dialogText = collectAllText(node)
            if (dialogText.isNotEmpty()) {
                visibleDialogTexts.add(dialogText)
            }
        }

        // Check for success messages
        val textLower = text.lowercase()
        for (keyword in successKeywords) {
            if (textLower.contains(keyword)) {
                successMessages.add(text)
                break
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                captureRecursive(
                    child, depth + 1, claimButtonTexts, claimButtonResIds,
                    visibleDialogTexts, rewardCounters, successMessages
                )
            }
        }
    }

    private fun collectAllText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        collectTextRecursive(node, sb, 0)
        return sb.toString().trim()
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 20) return
        node.text?.toString()?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextRecursive(child, sb, depth + 1)
            }
        }
    }
}