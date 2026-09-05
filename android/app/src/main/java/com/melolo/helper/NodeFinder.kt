package com.melolo.helper

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Finds and inspects AccessibilityNodeInfo nodes in the UI tree.
 *
 * Selector priority:
 *   1. resource-id
 *   2. contentDescription
 *   3. text
 *   4. view hierarchy traversal
 *   5. coordinate fallback (configurable, disabled by default)
 */
object NodeFinder {

    data class NodeInfo(
        val className: String,
        val text: String,
        val contentDescription: String,
        val resourceId: String,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val isVisible: Boolean,
        val bounds: Rect?,
        val depth: Int,
        val childCount: Int
    )

    data class FindResult(
        val node: AccessibilityNodeInfo?,
        val matchType: MatchType,
        val confidence: Int,
        val matchedKeyword: String
    )

    enum class MatchType {
        RESOURCE_ID,
        CONTENT_DESCRIPTION,
        TEXT,
        HIERARCHY,
        COORDINATE_FALLBACK
    }

    @Volatile
    var coordinateFallbackEnabled: Boolean = false

    /** P0: matched node -> actionable (clickable parent/child) */
    fun resolveActionable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isEnabled) return node
        // coba child clickable dulu, lalu parent
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { c -> if (c.isClickable && c.isEnabled) return c }
        }
        var p = node.parent
        var d = 0
        while (p != null && d < 5) {
            if (p.isClickable && p.isEnabled) return p
            p = p.parent; d++
        }
        return null
    }

    /**
     * Find a node by resource-id (exact or partial match).
     */
    fun findByResourceId(root: AccessibilityNodeInfo?, resourceId: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()
    }

    /**
     * Find nodes by text content (exact or containing).
     */
    fun findByText(root: AccessibilityNodeInfo?, text: String, exact: Boolean = false): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return if (exact) {
            root.findAccessibilityNodeInfosByText(text)
        } else {
            findNodesContainingText(root, text)
        }
    }

    /**
     * Find nodes by contentDescription.
     */
    fun findByContentDescription(root: AccessibilityNodeInfo?, description: String): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return findNodesByContentDescription(root, description)
    }

    /**
     * Find all clickable nodes matching any of the given keywords.
     * Uses priority-based selector matching.
     */
    fun findClickableByKeywords(
        root: AccessibilityNodeInfo?,
        keywords: List<String>,
        resourceIdPatterns: List<String> = emptyList()
    ): List<FindResult> {
        if (root == null) return emptyList()

        val results = mutableListOf<FindResult>()
        findClickableRecursive(root, keywords, resourceIdPatterns, 0, results)
        return results.sortedByDescending { it.confidence }
    }

    private fun findClickableRecursive(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        resourceIdPatterns: List<String>,
        depth: Int,
        results: MutableList<FindResult>
    ) {
        if (depth > 30) return

        // P0 fix: resolve actionable node dulu, filter non-clickable
        val actionable = resolveActionable(node)
        // Check resource-id patterns first (highest priority) - hanya jika actionable
        val resId = (actionable?.viewIdResourceName ?: node.viewIdResourceName)?.lowercase() ?: ""
        for (pattern in resourceIdPatterns) {
            if (resId.contains(pattern.lowercase())) {
                val target = actionable ?: node
                if (!target.isClickable) { /* tetap skor tapi flag */ }
                results.add(
                    FindResult(
                        node = target,
                        matchType = MatchType.RESOURCE_ID,
                        confidence = 50,
                        matchedKeyword = "resource-id:$pattern"
                    )
                )
                return
            }
        }

        // Check contentDescription
        val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        for (keyword in keywords) {
            if (contentDesc.contains(keyword.lowercase())) {
                results.add(
                    FindResult(
                        node = node,
                        matchType = MatchType.CONTENT_DESCRIPTION,
                        confidence = 30,
                        matchedKeyword = keyword
                    )
                )
                return
            }
        }

        // Check text
        val text = node.text?.toString()?.lowercase()?.trim() ?: ""
        for (keyword in keywords) {
            if (text.contains(keyword.lowercase())) {
                results.add(
                    FindResult(
                        node = node,
                        matchType = MatchType.TEXT,
                        confidence = if (text == keyword.lowercase()) 30 else 15,
                        matchedKeyword = keyword
                    )
                )
                return
            }
        }

        // Recurse children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findClickableRecursive(child, keywords, resourceIdPatterns, depth + 1, results)
            }
        }
    }

    /**
     * Collect full node hierarchy for inspection/debugging.
     */
    fun collectHierarchy(root: AccessibilityNodeInfo?): List<NodeInfo> {
        if (root == null) return emptyList()
        val list = mutableListOf<NodeInfo>()
        collectHierarchyRecursive(root, 0, list)
        return list
    }

    private fun collectHierarchyRecursive(
        node: AccessibilityNodeInfo,
        depth: Int,
        list: MutableList<NodeInfo>
    ) {
        if (depth > 30) return

        list.add(
            NodeInfo(
                className = node.className?.toString()?.substringAfterLast(".") ?: "unknown",
                text = node.text?.toString() ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                resourceId = node.viewIdResourceName ?: "",
                isClickable = node.isClickable,
                isEnabled = node.isEnabled,
                isVisible = node.isVisibleToUser,
                bounds = Rect().also { node.getBoundsInScreen(it) },
                depth = depth,
                childCount = node.childCount
            )
        )

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectHierarchyRecursive(child, depth + 1, list)
            }
        }
    }

    /**
     * Format hierarchy as a tree string for display.
     */
    fun formatHierarchy(hierarchy: List<NodeInfo>): String {
        val sb = StringBuilder()
        sb.appendLine("Accessibility Node Hierarchy")
        sb.appendLine("============================")
        sb.appendLine("Total nodes: ${hierarchy.size}")
        sb.appendLine()

        for (node in hierarchy) {
            val indent = "  ".repeat(node.depth)
            sb.appendLine("${indent}Node")
            sb.appendLine("${indent}├── className: ${node.className}")
            if (node.text.isNotEmpty()) {
                sb.appendLine("${indent}├── text: \"${node.text}\"")
            }
            if (node.contentDescription.isNotEmpty()) {
                sb.appendLine("${indent}├── contentDescription: \"${node.contentDescription}\"")
            }
            if (node.resourceId.isNotEmpty()) {
                sb.appendLine("${indent}├── resourceId: ${node.resourceId}")
            }
            sb.appendLine("${indent}├── clickable: ${node.isClickable}")
            sb.appendLine("${indent}├── enabled: ${node.isEnabled}")
            sb.appendLine("${indent}├── visible: ${node.isVisible}")
            if (node.bounds != null) {
                sb.appendLine("${indent}├── bounds: ${node.bounds}")
            }
            sb.appendLine("${indent}└── children: ${node.childCount}")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Check if a node is still valid (attached to window, not recycled).
     */
    fun isNodeValid(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return try {
            // Accessing a property will throw if the node is recycled
            node.className != null
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- Private helpers ---

    private fun findNodesContainingText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesContainingTextRecursive(root, text.lowercase(), 0, results)
        return results
    }

    private fun findNodesContainingTextRecursive(
        node: AccessibilityNodeInfo,
        text: String,
        depth: Int,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (depth > 30) return
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        if (nodeText.contains(text)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodesContainingTextRecursive(child, text, depth + 1, results)
            }
        }
    }

    private fun findNodesByContentDescription(root: AccessibilityNodeInfo, desc: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByContentDescRecursive(root, desc.lowercase(), 0, results)
        return results
    }

    private fun findNodesByContentDescRecursive(
        node: AccessibilityNodeInfo,
        desc: String,
        depth: Int,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (depth > 30) return
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nodeDesc.contains(desc)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodesByContentDescRecursive(child, desc, depth + 1, results)
            }
        }
    }
}