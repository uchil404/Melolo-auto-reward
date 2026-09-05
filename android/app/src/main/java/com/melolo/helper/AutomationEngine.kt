package com.melolo.helper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core automation engine that orchestrates the reward claim workflow.
 *
 * State machine flow:
 *   IDLE → CHECK_SERVICE → OPEN_MELOLO → WAIT_FOR_UI → FIND_REWARD
 *   → OPEN_REWARD → FIND_CLAIM → CLICK_CLAIM → WAIT_RESULT
 *   → VERIFY_SUCCESS → FIND_NEXT_REWARD → FINISHED
 *
 * Failure path:
 *   ERROR → RETRY → WAIT → RECHECK_UI → (retry exhausted) → STOPPED
 */
class AutomationEngine(
    private val context: Context,
    private val config: AutomationConfig,
    private val onStatusUpdate: (String, Int, String, String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val isRunning = AtomicBoolean(false)
    private val isEmergencyStopped = AtomicBoolean(false)

    val stateMachine = StateMachine()
    private var claimCount = 0
    private var lastClaimTime = "NONE"
    private var lastError = "NONE"
    private var rootNode: AccessibilityNodeInfo? = null

    private var automationRunnable: Runnable? = null
    private var stateDeadline: Long = 0
    private fun setDeadline() { stateDeadline = System.currentTimeMillis() + config.pageTimeoutMs }
    private fun isTimedOut() = System.currentTimeMillis() > stateDeadline

    data class AutomationConfig(
        val targetPackage: String,
        val enabled: Boolean = true,
        val maxRetry: Int = 3,
        val clickDelayMs: Long = 1200,
        val pageTimeoutMs: Long = 10000,
        val scanIntervalMs: Long = 1500,
        val maxRuntimeMinutes: Int = 30,
        val rewardKeywords: List<String> = listOf("reward", "hadiah", "daily reward", "check in"),
        val claimKeywords: List<String> = listOf("claim", "klaim", "collect", "ambil"),
        val resourceIdPatterns: List<String> = emptyList(),
        val stopOnCaptcha: Boolean = true,
        val stopOnSecurityCheck: Boolean = true,
        val maxSameAction: Int = 3,
        val confidenceThreshold: Int = 70,
        val coordinateFallbackEnabled: Boolean = false,
        val resumeAfterRestart: Boolean = false
    )

    private var startTime: Long = 0

    fun start(rootNode: AccessibilityNodeInfo?) {
        if (isRunning.get()) {
            Logger.warn("AutomationEngine: already running")
            return
        }

        if (!config.enabled) {
            Logger.warn("AutomationEngine: automation is disabled in config")
            return
        }

        this.rootNode = rootNode
        isRunning.set(true)
        isEmergencyStopped.set(false)
        SafetyManager.reset()
        ClickController.reset()
        VerificationEngine.reset()
        stateMachine.transitionTo(AutomationState.IDLE)
        startTime = System.currentTimeMillis()

        stateMachine.configure(config.maxRetry, config.maxSameAction)
        ClickController.clickDelayMs = config.clickDelayMs
        NodeFinder.coordinateFallbackEnabled = config.coordinateFallbackEnabled
        RewardDetector.confidenceThreshold = config.confidenceThreshold

        Logger.info("AutomationEngine: started")
        Logger.info("  Target: ${config.targetPackage}")
        Logger.info("  Confidence threshold: ${config.confidenceThreshold}%")
        Logger.info("  Max runtime: ${config.maxRuntimeMinutes}min")
        updateStatus()

        executeStep()
    }

    fun stop() {
        isRunning.set(false)
        stateMachine.transitionTo(AutomationState.STOPPED)
        automationRunnable?.let { handler.removeCallbacks(it) }
        Logger.info("AutomationEngine: stopped")
        updateStatus()
    }

    fun emergencyStop() {
        isRunning.set(false)
        isEmergencyStopped.set(true)
        stateMachine.transitionTo(AutomationState.STOPPED)
        automationRunnable?.let { handler.removeCallbacks(it) }
        SafetyManager.securityDetected = true
        SafetyManager.lastSecurityReason = "User-initiated emergency stop"
        Logger.safety("EMERGENCY STOP initiated by user")
        updateStatus()
    }

    fun updateRootNode(node: AccessibilityNodeInfo?) { /* deprecated: pakai freshRoot() */ }
    fun onViewClicked() { Logger.debug("View clicked -> verification window") }
    private fun freshRoot(): AccessibilityNodeInfo? = (context as? RewardAccessibilityService)?.freshRoot()

    fun isRunning(): Boolean = isRunning.get()

    private fun executeStep() {
        if (!isRunning.get() || isEmergencyStopped.get()) return
        if (isTimedOut()) { Logger.warn("State timeout"); stateMachine.transitionTo(AutomationState.ERROR); scheduleNext(config.scanIntervalMs); setDeadline(); return }
        val elapsed = (System.currentTimeMillis() - startTime) / 60000
        if (elapsed >= config.maxRuntimeMinutes) { stateMachine.transitionTo(AutomationState.FINISHED); stop(); return }
        val currentRoot = freshRoot() ?: run { scheduleNext(config.scanIntervalMs); return } // P0: fresh root, jangan pakai field recycle
        var shouldRecycle = true
        try {

        // ALWAYS check security first
        if (config.stopOnCaptcha || config.stopOnSecurityCheck) {
            if (SafetyManager.scanForSecurityThreat(currentRoot)) {
                Logger.safety("Security threat detected — stopping automation")
                stateMachine.transitionTo(AutomationState.STOPPED)
                stop()
                return
            }
        }

        when (stateMachine.currentState) {
            AutomationState.IDLE -> handleIdle(currentRoot)
            AutomationState.CHECK_SERVICE -> handleCheckService(currentRoot)
            AutomationState.OPEN_MELOLO -> handleOpenMelolo(currentRoot)
            AutomationState.WAIT_FOR_UI -> handleWaitForUi(currentRoot)
            AutomationState.FIND_REWARD -> handleFindReward(currentRoot)
            AutomationState.OPEN_REWARD -> handleOpenReward(currentRoot)
            AutomationState.FIND_CLAIM -> handleFindClaim(currentRoot)
            AutomationState.CLICK_CLAIM -> handleClickClaim(currentRoot)
            AutomationState.WAIT_RESULT -> handleWaitResult(currentRoot)
            AutomationState.VERIFY_SUCCESS -> handleVerifySuccess(currentRoot)
            AutomationState.FIND_NEXT_REWARD -> handleFindNextReward(currentRoot)
            AutomationState.FINISHED -> handleFinished()
            AutomationState.ERROR -> handleError(currentRoot)
            AutomationState.RETRY -> handleRetry(currentRoot)
            AutomationState.WAIT -> handleWait(currentRoot)
            AutomationState.RECHECK_UI -> handleRecheckUi(currentRoot)
            AutomationState.STOPPED -> { /* do nothing */ }
        }

        } finally { if(shouldRecycle) try{currentRoot.recycle()}catch(_:Exception){} }
        updateStatus()
    }

    private fun handleIdle(root: AccessibilityNodeInfo) { setDeadline()
        stateMachine.transitionTo(AutomationState.CHECK_SERVICE)
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleCheckService(root: AccessibilityNodeInfo) {
        val packageName = root.packageName?.toString() ?: ""
        if (packageName == config.targetPackage) {
            Logger.info("Target app detected: $packageName")
            stateMachine.transitionTo(AutomationState.FIND_REWARD)
            stateMachine.resetRetry()
        } else {
            // Target not in foreground; need to open it
            Logger.info("Target app not in foreground (current: $packageName)")
            stateMachine.transitionTo(AutomationState.OPEN_MELOLO)
        }
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleOpenMelolo(root: AccessibilityNodeInfo) {
        // The service can't directly launch an app; this is handled by Termux
        // via `am start` or the user manually opening the app.
        // We just wait for the target package to appear.
        val packageName = root.packageName?.toString() ?: ""
        if (packageName == config.targetPackage) {
            stateMachine.transitionTo(AutomationState.WAIT_FOR_UI)
        }
        scheduleNext(config.scanIntervalMs * 2)
    }

    private fun handleWaitForUi(root: AccessibilityNodeInfo) {
        // Wait for UI to stabilize
        stateMachine.transitionTo(AutomationState.FIND_REWARD)
        scheduleNext(config.clickDelayMs)
    }

    private fun handleFindReward(root: AccessibilityNodeInfo) {
        val candidates = RewardDetector.detectRewards(
            root, config.rewardKeywords, config.claimKeywords, config.resourceIdPatterns
        )

        if (candidates.isEmpty()) {
            Logger.info("No reward candidates found above threshold")
            // Try scrolling to find more
            val scrollable = ClickController.findScrollableParent(root)
            if (scrollable != null) {
                ClickController.scrollForward(scrollable)
                Logger.debug("Scrolled forward to find rewards")
                scheduleNext(config.clickDelayMs)
                return
            }
            stateMachine.transitionTo(AutomationState.FINISHED)
            scheduleNext(config.scanIntervalMs)
            return
        }

        Logger.info("Found ${candidates.size} reward candidates")
        val best = candidates.first()
        Logger.info("Best candidate: \"${best.text}\" confidence=${best.confidence}%")

        stateMachine.transitionTo(AutomationState.OPEN_REWARD)
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleOpenReward(root: AccessibilityNodeInfo) {
        // P0 fix: jangan klik generic reward, transisi ke FIND_CLAIM untuk semantic check
        // Hanya reward dengan resource-id Melolo 5.4.4 yang boleh lanjut
        val checkIn = MeloloAdapter.findCheckIn(root)
        if (checkIn.isNotEmpty() && MeloloAdapter.safetyGate(checkIn.first().node)) {
            Logger.info("Check-in Today detected via resource-id")
            stateMachine.transitionTo(AutomationState.FIND_CLAIM)
            scheduleNext(config.scanIntervalMs)
            return
        }
        // Tomorrow -> NO_REWARD, jangan diklaim
        val snap = SnapshotRecorder.record(root).toString().lowercase()
        if (snap.contains("check_in_task_button_tomorrow") || snap.contains("claim_tomorrow")) {
            stateMachine.transitionTo(AutomationState.NO_REWARD)
            scheduleNext(config.scanIntervalMs)
            return
        }
        // P0: resolve actionable + pisah flow
        stateMachine.transitionTo(AutomationState.FIND_CLAIM)
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleFindClaim(root: AccessibilityNodeInfo) {
        // P0 #8: snapshot tepat sebelum resolve, bukan jauh sebelum click
        // akan diambil ulang di handleClickClaim sebelum click

        val claimButtons = RewardDetector.detectClaimButtons(
            root, config.claimKeywords, config.resourceIdPatterns
        )

        if (claimButtons.isEmpty()) {
            Logger.info("No claim buttons found")
            // Try scrolling
            val scrollable = ClickController.findScrollableParent(root)
            if (scrollable != null) {
                ClickController.scrollForward(scrollable)
                scheduleNext(config.clickDelayMs)
                return
            }
            stateMachine.transitionTo(AutomationState.FIND_NEXT_REWARD)
            scheduleNext(config.scanIntervalMs)
            return
        }

        val best = claimButtons.first()
        Logger.info("Found claim button: \"${best.text}\" confidence=${best.confidence}%")
        stateMachine.transitionTo(AutomationState.CLICK_CLAIM)
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleClickClaim(root: AccessibilityNodeInfo) {
        VerificationEngine.takeBeforeSnapshot(root) // P0: tepat sebelum click
        val claimButtons = RewardDetector.detectClaimButtons(
            root, config.claimKeywords, config.resourceIdPatterns
        )

        if (claimButtons.isEmpty()) {
            stateMachine.transitionTo(AutomationState.ERROR)
            scheduleNext(config.scanIntervalMs)
            return
        }

        val best = claimButtons.first()
        val result = ClickController.safeClick(
            best.node, best.confidence, config.confidenceThreshold, stateMachine
        )

        if (result.performed && result.success) {
            Logger.claim("Claim click performed: \"${best.text}\"")
            stateMachine.transitionTo(AutomationState.WAIT_RESULT)
            scheduleNext(config.clickDelayMs * 2)
        } else if (result.performed && !result.success) {
            Logger.error("Claim click failed: ${result.reason}")
            if (stateMachine.recordRetry()) stateMachine.transitionTo(AutomationState.RETRY)
            else stateMachine.transitionTo(AutomationState.STOPPED)
            scheduleNext(config.scanIntervalMs)
        } else {
            Logger.warn("Claim click not performed: ${result.reason}")
            stateMachine.transitionTo(AutomationState.FIND_NEXT_REWARD)
            scheduleNext(config.scanIntervalMs)
        }
    }

    private fun handleWaitResult(root: AccessibilityNodeInfo) {
        // Wait for UI to settle after click
        stateMachine.transitionTo(AutomationState.VERIFY_SUCCESS)
        scheduleNext(config.clickDelayMs)
    }

    private fun handleVerifySuccess(root: AccessibilityNodeInfo) {
        val result = VerificationEngine.verifyAfterClaim(root, stateMachine)
        if (result.verdict == VerificationEngine.Verdict.UNKNOWN) {
            Logger.warn("VERIFY UNKNOWN: ${result.evidence}"); if (stateMachine.recordRetry()) stateMachine.transitionTo(AutomationState.RETRY) else stateMachine.transitionTo(AutomationState.STOPPED); scheduleNext(config.scanIntervalMs); return
        }
        if (result.verdict == VerificationEngine.Verdict.FAILURE) {
            Logger.warn("VERIFY FAILURE: ${result.evidence}"); if (stateMachine.recordRetry()) stateMachine.transitionTo(AutomationState.RETRY) else stateMachine.transitionTo(AutomationState.STOPPED); scheduleNext(config.scanIntervalMs); return
        }
        if (result.verdict == VerificationEngine.Verdict.SUCCESS && result.confidence >= 70) {
            claimCount++
            lastClaimTime = java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            lastError = "NONE"
            stateMachine.resetRetry()
            stateMachine.resetSameAction()
            Logger.claim("CLAIM SUCCESS #$claimCount — ${result.evidence}")
            stateMachine.transitionTo(AutomationState.FIND_NEXT_REWARD)
        } else if (result.success && result.confidence < 50) {
            // Low-confidence success — proceed but note it
            claimCount++
            lastClaimTime = java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            Logger.claim("CLAIM SUCCESS (low confidence) #$claimCount — ${result.evidence}")
            stateMachine.transitionTo(AutomationState.FIND_NEXT_REWARD)
        } else {
            Logger.warn("Verification failed: ${result.evidence}")
            lastError = result.evidence
            if (stateMachine.recordRetry()) {
                stateMachine.transitionTo(AutomationState.RETRY)
            }
        }
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleFindNextReward(root: AccessibilityNodeInfo) {
        val candidates = RewardDetector.detectRewards(
            root, config.rewardKeywords, config.claimKeywords, config.resourceIdPatterns
        )

        if (candidates.isEmpty()) {
            // Try scrolling
            val scrollable = ClickController.findScrollableParent(root)
            if (scrollable != null) {
                ClickController.scrollForward(scrollable)
                Logger.debug("Scrolled forward looking for more rewards")
                stateMachine.transitionTo(AutomationState.FIND_REWARD)
                scheduleNext(config.clickDelayMs * 2)
                return
            }
            stateMachine.transitionTo(AutomationState.FINISHED)
        } else {
            stateMachine.resetRetry()
            stateMachine.resetSameAction()
            stateMachine.transitionTo(AutomationState.OPEN_REWARD)
        }
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleFinished() {
        Logger.info("Automation finished. Total claims: $claimCount")
        isRunning.set(false) // P0: FINISHED jangan jadi STOPPED
        handler.removeCallbacksAndMessages(null)
        updateStatus()
    }

    private fun handleError(root: AccessibilityNodeInfo) {
        Logger.error("Automation error state")
        if (stateMachine.recordRetry()) {
            stateMachine.transitionTo(AutomationState.RETRY)
        }
        scheduleNext(config.scanIntervalMs * 2)
    }

    private fun handleRetry(root: AccessibilityNodeInfo) {
        Logger.info("Retrying... (attempt ${stateMachine.getRetryCount()})")
        stateMachine.transitionTo(AutomationState.WAIT)
        scheduleNext(config.clickDelayMs * 3)
    }

    private fun handleWait(root: AccessibilityNodeInfo) {
        stateMachine.transitionTo(AutomationState.RECHECK_UI)
        scheduleNext(config.scanIntervalMs)
    }

    private fun handleRecheckUi(root: AccessibilityNodeInfo) {
        stateMachine.transitionTo(AutomationState.FIND_REWARD)
        scheduleNext(config.scanIntervalMs)
    }

    private fun scheduleNext(delayMs: Long) {
        handler.removeCallbacksAndMessages(null) // P1: satu pending job saja
        automationRunnable = Runnable { executeStep() }
        handler.postDelayed(automationRunnable!!, delayMs)
    }

    private fun updateStatus() {
        onStatusUpdate(
            stateMachine.currentState.name,
            claimCount,
            lastClaimTime,
            lastError
        )

        TermuxBridge.updateStatus(
            context,
            stateMachine.currentState.name,
            claimCount,
            lastClaimTime,
            lastError,
            true,
            isRunning.get()
        )
    }
}