package com.melolo.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Accessibility Service for the Melolo Reward Helper.
 *
 * Monitors window state changes, content changes, and user interactions
 * in the target application. Provides UI tree inspection and automation
 * capabilities.
 */
class RewardAccessibilityService : AccessibilityService() {

    private var automationEngine: AutomationEngine? = null
    private var isInspectMode: Boolean = false
    private var isTestMode: Boolean = false
    private var claimCount: Int = 0
    private var lastClaimTime: String = "NONE"
    private var lastError: String = "NONE"

    // Config — loaded from SharedPreferences, overridable by Termux broadcasts
    private var config: AutomationEngine.AutomationConfig = AutomationEngine.AutomationConfig(
        targetPackage = "",
        enabled = false
    )

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        this.serviceInfo = info

        Logger.info("AccessibilityService connected")
        loadConfig()
        updateStatusBroadcast()

        // Register Termux command receiver
        TermuxBridge.registerReceiver(this) { command, extras ->
            handleTermuxCommand(command, extras)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rootNode = rootInActiveWindow ?: return

        // Update automation engine's root node
        automationEngine?.updateRootNode(rootNode)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: ""
                val className = event.className?.toString() ?: ""
                Logger.debug("Window changed: $packageName / $className")

                if (isInspectMode) {
                    // In inspect mode, dump hierarchy on every window change
                    dumpHierarchy(rootNode)
                }

                if (isTestMode) {
                    runTestScan(rootNode)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isInspectMode) {
                    dumpHierarchy(rootNode)
                }
            }
        }

        rootNode.recycle()
    }

    override fun onInterrupt() {
        Logger.warn("AccessibilityService interrupted")
        automationEngine?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        automationEngine?.stop()
        TermuxBridge.unregisterReceiver(this)
        Logger.info("AccessibilityService destroyed")
    }

    // --- Public API for MainActivity ---

    fun isServiceReady(): Boolean = rootInActiveWindow != null

    fun getCurrentPackageName(): String {
        return rootInActiveWindow?.packageName?.toString() ?: ""
    }

    fun getHierarchyDump(): String {
        val root = rootInActiveWindow ?: return "No window available"
        val hierarchy = NodeFinder.collectHierarchy(root)
        root.recycle()
        return NodeFinder.formatHierarchy(hierarchy)
    }

    fun startAutomation() {
        if (config.targetPackage.isBlank()) {
            Logger.error("Cannot start automation: target_package not configured")
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            Logger.error("Cannot start automation: no window available")
            return
        }

        automationEngine = AutomationEngine(
            this, config
        ) { state, claims, lastClaim, error ->
            claimCount = claims
            lastClaimTime = lastClaim
            lastError = error
            updateStatusBroadcast()
        }

        automationEngine?.start(root)
        root.recycle()
    }

    fun stopAutomation() {
        automationEngine?.stop()
        updateStatusBroadcast()
    }

    fun emergencyStop() {
        automationEngine?.emergencyStop()
        isInspectMode = false
        isTestMode = false
        updateStatusBroadcast()
    }

    fun startInspectMode() {
        isInspectMode = true
        isTestMode = false
        Logger.info("Inspect mode enabled")
        val root = rootInActiveWindow
        if (root != null) {
            dumpHierarchy(root)
            root.recycle()
        }
    }

    fun stopInspectMode() {
        isInspectMode = false
        Logger.info("Inspect mode disabled")
    }

    fun startTestMode() {
        isTestMode = true
        isInspectMode = false
        Logger.info("Test mode enabled")
        val root = rootInActiveWindow
        if (root != null) {
            runTestScan(root)
            root.recycle()
        }
    }

    fun stopTestMode() {
        isTestMode = false
        Logger.info("Test mode disabled")
    }

    fun getStatus(): Map<String, String> {
        return mapOf(
            "service_running" to true.toString(),
            "automation_running" to (automationEngine?.isRunning() ?: false).toString(),
            "state" to (automationEngine?.stateMachine?.currentState?.name ?: "IDLE"),
            "claims" to claimCount.toString(),
            "last_claim" to lastClaimTime,
            "last_error" to lastError,
            "inspect_mode" to isInspectMode.toString(),
            "test_mode" to isTestMode.toString(),
            "target_package" to config.targetPackage,
            "current_package" to getCurrentPackageName()
        )
    }

    fun updateConfig(newConfig: AutomationEngine.AutomationConfig) {
        config = newConfig
        saveConfig()
        Logger.info("Configuration updated")
    }

    // --- Private ---

    private fun handleTermuxCommand(
        command: TermuxBridge.Command,
        extras: Map<String, String>
    ) {
        when (command) {
            TermuxBridge.Command.START -> {
                Logger.info("Termux command: START")
                startAutomation()
            }
            TermuxBridge.Command.STOP -> {
                Logger.info("Termux command: STOP")
                stopAutomation()
            }
            TermuxBridge.Command.INSPECT -> {
                Logger.info("Termux command: INSPECT")
                startInspectMode()
            }
            TermuxBridge.Command.EMERGENCY_STOP -> {
                Logger.info("Termux command: EMERGENCY_STOP")
                emergencyStop()
            }
            TermuxBridge.Command.TEST -> {
                Logger.info("Termux command: TEST")
                startTestMode()
            }
            TermuxBridge.Command.STATUS -> {
                updateStatusBroadcast()
            }
            TermuxBridge.Command.CONFIG -> {
                // Reload config from SharedPreferences
                loadConfig()
                Logger.info("Config reloaded")
            }
            TermuxBridge.Command.UNKNOWN -> {
                Logger.warn("Unknown Termux command: ${extras["command"]}")
            }
        }
    }

    private fun dumpHierarchy(rootNode: AccessibilityNodeInfo) {
        val hierarchy = NodeFinder.collectHierarchy(rootNode)
        val formatted = NodeFinder.formatHierarchy(hierarchy)
        Logger.info("--- Hierarchy Dump ---\n$formatted")

        // Also send via broadcast for Termux
        val intent = Intent("com.melolo.helper.HIERARCHY_DUMP").apply {
            putExtra("hierarchy", formatted)
        }
        sendBroadcast(intent)
    }

    private fun runTestScan(rootNode: AccessibilityNodeInfo) {
        Logger.info("--- Test Mode Scan ---")

        val candidates = RewardDetector.detectRewards(
            rootNode,
            config.rewardKeywords,
            config.claimKeywords,
            config.resourceIdPatterns
        )

        if (candidates.isEmpty()) {
            Logger.info("No reward candidates found above threshold (${RewardDetector.confidenceThreshold}%)")
        } else {
            Logger.info("Found ${candidates.size} candidates:")
            candidates.forEachIndexed { index, candidate ->
                Logger.info(RewardDetector.formatCandidate(candidate, index))
            }
        }

        // Also detect claim buttons
        val claimButtons = RewardDetector.detectClaimButtons(
            rootNode, config.claimKeywords, config.resourceIdPatterns
        )
        if (claimButtons.isNotEmpty()) {
            Logger.info("--- Claim Button Candidates ---")
            claimButtons.forEachIndexed { index, candidate ->
                Logger.info(RewardDetector.formatCandidate(candidate, index))
            }
        }
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("melolo_helper_config", MODE_PRIVATE)
        config = AutomationEngine.AutomationConfig(
            targetPackage = prefs.getString("target_package", "") ?: "",
            enabled = prefs.getBoolean("enabled", false),
            maxRetry = prefs.getInt("max_retry", 3),
            clickDelayMs = prefs.getLong("click_delay_ms", 1200),
            pageTimeoutMs = prefs.getLong("page_timeout_ms", 10000),
            scanIntervalMs = prefs.getLong("scan_interval_ms", 1500),
            maxRuntimeMinutes = prefs.getInt("max_runtime_minutes", 30),
            rewardKeywords = prefs.getString("reward_keywords", "reward,hadiah,daily reward,check in")
                ?.split(",")?.map { it.trim() } ?: listOf("reward", "hadiah", "daily reward", "check in"),
            claimKeywords = prefs.getString("claim_keywords", "claim,klaim,collect,ambil")
                ?.split(",")?.map { it.trim() } ?: listOf("claim", "klaim", "collect", "ambil"),
            resourceIdPatterns = prefs.getString("resource_id_patterns", "")
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            stopOnCaptcha = prefs.getBoolean("stop_on_captcha", true),
            stopOnSecurityCheck = prefs.getBoolean("stop_on_security_check", true),
            maxSameAction = prefs.getInt("max_same_action", 3),
            confidenceThreshold = prefs.getInt("confidence_threshold", 70),
            coordinateFallbackEnabled = prefs.getBoolean("coordinate_fallback_enabled", false),
            resumeAfterRestart = prefs.getBoolean("resume_after_restart", false)
        )
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("melolo_helper_config", MODE_PRIVATE)
        prefs.edit().apply {
            putString("target_package", config.targetPackage)
            putBoolean("enabled", config.enabled)
            putInt("max_retry", config.maxRetry)
            putLong("click_delay_ms", config.clickDelayMs)
            putLong("page_timeout_ms", config.pageTimeoutMs)
            putLong("scan_interval_ms", config.scanIntervalMs)
            putInt("max_runtime_minutes", config.maxRuntimeMinutes)
            putString("reward_keywords", config.rewardKeywords.joinToString(","))
            putString("claim_keywords", config.claimKeywords.joinToString(","))
            putString("resource_id_patterns", config.resourceIdPatterns.joinToString(","))
            putBoolean("stop_on_captcha", config.stopOnCaptcha)
            putBoolean("stop_on_security_check", config.stopOnSecurityCheck)
            putInt("max_same_action", config.maxSameAction)
            putInt("confidence_threshold", config.confidenceThreshold)
            putBoolean("coordinate_fallback_enabled", config.coordinateFallbackEnabled)
            putBoolean("resume_after_restart", config.resumeAfterRestart)
            apply()
        }
    }

    private fun updateStatusBroadcast() {
        TermuxBridge.updateStatus(
            this,
            automationEngine?.stateMachine?.currentState?.name ?: "IDLE",
            claimCount,
            lastClaimTime,
            lastError,
            true,
            automationEngine?.isRunning() ?: false
        )
    }
}