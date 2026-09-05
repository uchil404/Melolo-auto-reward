package com.melolo.helper

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Performs safe clicks on AccessibilityNodeInfo nodes.
 *
 * Before every click:
 *   1. Verify node is still valid (not recycled)
 *   2. Verify node is clickable
 *   3. Verify node is enabled
 *   4. Verify confidence is sufficient
 *   5. Verify node hasn't been clicked before
 *   6. Verify no security challenge is active
 *
 * After click: wait, scan, verify.
 */
object ClickController {

    private val clickedNodes = mutableSetOf<String>()
    private val clickHistory = mutableListOf<ClickRecord>()

    data class ClickRecord(
        val timestamp: Long,
        val text: String,
        val resourceId: String,
        val className: String,
        val success: Boolean,
        val errorReason: String?
    )

    data class ClickResult(
        val success: Boolean,
        val performed: Boolean,
        val reason: String
    )

    @Volatile
    var clickDelayMs: Long = 1200

    fun reset() {
        clickedNodes.clear()
        clickHistory.clear()
    }

    /**
     * Perform a safe click on a node.
     * Returns ClickResult indicating success/failure and reason.
     */
    fun safeClick(
        node: AccessibilityNodeInfo?,
        confidence: Int,
        confidenceThreshold: Int,
        stateMachine: StateMachine
    ): ClickResult {
        // 1. Check null
        if (node == null) {
            return ClickResult(false, false, "Node is null")
        }

        // 2. Check if automation is stopped
        if (stateMachine.isStopped()) {
            return ClickResult(false, false, "Automation is stopped")
        }

        // 3. Check security
        if (SafetyManager.securityDetected) {
            return ClickResult(false, false, "Security challenge detected: ${SafetyManager.lastSecurityReason}")
        }

        // 4. Check node validity
        if (!NodeFinder.isNodeValid(node)) {
            return ClickResult(false, false, "Node is recycled/invalid")
        }

        // 5. Resolve actionable + re-validate parent (P0 #4)
        val actionable = NodeFinder.resolveActionable(node) ?: node
        if (actionable !== node) {
            // re-validate resolved node sepenuhnya
            if (!actionable.isEnabled || !actionable.isVisibleToUser) return ClickResult(false,false,"Resolved node not enabled/visible")
            if (SafetyManager.isNodeSuspicious(actionable)) return ClickResult(false,false,"Resolved node suspicious")
            return performClick(actionable, confidence, confidenceThreshold, stateMachine)
        }
        if (!node.isClickable) return ClickResult(false, false, "Node is not clickable and no actionable found")

        // 6. Check enabled
        if (!node.isEnabled) {
            return ClickResult(false, false, "Node is not enabled")
        }

        // 7. Check confidence
        if (confidence < confidenceThreshold) {
            return ClickResult(false, false, "Confidence $confidence% below threshold $confidenceThreshold%")
        }

        // 8. Check duplicate
        val nodeId = buildNodeId(node)
        if (clickedNodes.contains(nodeId)) {
            Logger.warn("ClickController: node already clicked: $nodeId")
            return ClickResult(false, false, "Node already clicked in this session")
        }

        // 9. Check safety on this specific node
        if (SafetyManager.isNodeSuspicious(node)) {
            return ClickResult(false, false, "Node appears suspicious/security-related")
        }

        // 10. Record action in state machine
        if (!stateMachine.recordAction("click:${nodeId}")) {
            return ClickResult(false, false, "Same action limit exceeded")
        }

        return performClick(node, confidence, confidenceThreshold, stateMachine)
    }

    private fun performClick(
        node: AccessibilityNodeInfo,
        confidence: Int,
        confidenceThreshold: Int,
        stateMachine: StateMachine
    ): ClickResult {
        val nodeId = buildNodeId(node)
        val text = node.text?.toString() ?: ""
        val resId = node.viewIdResourceName ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: ""

        Logger.claim("Attempting click: text=\"$text\" resId=$resId confidence=$confidence%")

        return try {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            val record = ClickRecord(
                timestamp = System.currentTimeMillis(),
                text = text,
                resourceId = resId,
                className = className,
                success = success,
                errorReason = if (success) null else "performAction returned false"
            )
            clickHistory.add(record)

            if (success) {
                clickedNodes.add(nodeId)
                Logger.claim("Click SUCCESS: text=\"$text\" resId=$resId")
                ClickResult(true, true, "Click performed successfully")
            } else {
                Logger.error("Click FAILED: text=\"$text\" resId=$resId — performAction returned false")
                ClickResult(false, true, "performAction returned false")
            }
        } catch (e: Exception) {
            Logger.error("Click EXCEPTION: ${e.message}")
            clickHistory.add(
                ClickRecord(
                    timestamp = System.currentTimeMillis(),
                    text = text,
                    resourceId = resId,
                    className = className,
                    success = false,
                    errorReason = e.message
                )
            )
            ClickResult(false, true, "Exception: ${e.message}")
        }
    }

    /**
     * Perform scroll forward on a scrollable node.
     */
    fun scrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isScrollable) return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (e: Exception) {
            Logger.error("Scroll forward failed: ${e.message}")
            false
        }
    }

    /**
     * Perform scroll backward on a scrollable node.
     */
    fun scrollBackward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isScrollable) return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        } catch (e: Exception) {
            Logger.error("Scroll backward failed: ${e.message}")
            false
        }
    }

    /**
     * Find first scrollable ancestor of a node.
     */
    fun findScrollableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 20) {
            if (current.isScrollable) return current
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Find first clickable ancestor of a node.
     */
    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 20) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    fun getClickHistory(): List<ClickRecord> = clickHistory.toList()
    fun getClickedCount(): Int = clickHistory.size
    fun getSuccessfulClicks(): Int = clickHistory.count { it.success }

    private fun buildNodeId(node: AccessibilityNodeInfo): String {
        val resId = node.viewIdResourceName ?: ""
        if (resId.isNotEmpty()) return "rid=$resId"
        val text = node.text?.toString()?.take(50) ?: ""
        val cd = node.contentDescription?.toString()?.take(30) ?: ""
        return "text=$text|cd=$cd|cls=${node.className?.toString()?.substringAfterLast(".")}"
    }
}