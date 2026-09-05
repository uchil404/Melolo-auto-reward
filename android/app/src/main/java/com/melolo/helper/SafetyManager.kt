package com.melolo.helper

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects security challenges, CAPTCHA, verification screens,
 * and suspicious activity prompts. When detected, automation
 * MUST stop — we never attempt to bypass these.
 */
object SafetyManager {

    private val captchaKeywords = listOf(
        "captcha",
        "verify",
        "verification",
        "security check",
        "suspicious activity",
        "robot",
        "human verification",
        "are you a robot",
        "prove you",
        "not a robot",
        "security verification",
        "identity verification",
        "phone verification",
        "sms verification",
        "otp",
        "one time password",
        "login verification",
        "unusual activity",
        "confirm your identity",
        "verification required",
        "security challenge",
        "too many attempts",
        "try again later",
        "account locked",
        "account suspended",
        "rate limited"
    )

    private val captchaPackagePrefixes = listOf(
        "com.google.android.gms",
        "com.android.captcha"
    )

    @Volatile
    var securityDetected: Boolean = false
        private set

    @Volatile
    var lastSecurityReason: String = ""
        private set

    fun reset() {
        securityDetected = false
        lastSecurityReason = ""
    }

    /**
     * Scan the entire node tree for security/challenge indicators.
     * Returns true if a security issue is found.
     */
    fun scanForSecurityThreat(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        val result = scanNodeRecursive(rootNode, 0)
        if (result != null) {
            securityDetected = true
            lastSecurityReason = result
            Logger.safety("SECURITY THREAT DETECTED: $result")
            return true
        }
        return false
    }

    private fun scanNodeRecursive(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > 30) return null

        // Check package name for known captcha providers
        val packageName = node.packageName?.toString() ?: ""
        for (prefix in captchaPackagePrefixes) {
            if (packageName.startsWith(prefix)) {
                return "Captcha provider package detected: $packageName"
            }
        }

        // Check text content
        val text = node.text?.toString()?.lowercase()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""

        for (keyword in captchaKeywords) {
            if (text.contains(keyword)) {
                return "Security keyword '$keyword' found in text: \"$text\""
            }
            if (contentDesc.contains(keyword)) {
                return "Security keyword '$keyword' found in contentDescription: \"$contentDesc\""
            }
        }

        // Check for dialog overlays that might be security prompts
        if (node.className?.toString()?.contains("Dialog", ignoreCase = true) == true) {
            val dialogText = collectDialogText(node)
            for (keyword in captchaKeywords) {
                if (dialogText.contains(keyword)) {
                    return "Security keyword '$keyword' found in dialog"
                }
            }
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childResult = scanNodeRecursive(child, depth + 1)
            if (childResult != null) return childResult
        }

        return null
    }

    private fun collectDialogText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        collectTextRecursive(node, sb, 0)
        return sb.toString().lowercase()
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 20) return
        node.text?.toString()?.let { sb.append(it).append(" ") }
        node.contentDescription?.toString()?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTextRecursive(it, sb, depth + 1) }
        }
    }

    /**
     * Check if a single node looks like a security element.
     */
    fun isNodeSuspicious(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""

        for (keyword in captchaKeywords) {
            if (text.contains(keyword) || contentDesc.contains(keyword)) {
                securityDetected = true
                lastSecurityReason = "Suspicious node: $keyword"
                return true
            }
        }
        return false
    }
}