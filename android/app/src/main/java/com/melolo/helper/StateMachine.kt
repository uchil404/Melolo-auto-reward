package com.melolo.helper

/**
 * Deterministic state machine governing the automation lifecycle.
 * All state transitions are logged and observable.
 */
enum class AutomationState {
    IDLE,
    CHECK_SERVICE,
    OPEN_MELOLO,
    WAIT_FOR_UI,
    FIND_REWARD,
    OPEN_REWARD,
    FIND_CLAIM,
    CLICK_CLAIM,
    WAIT_RESULT,
    VERIFY_SUCCESS,
    FIND_NEXT_REWARD,
    FINISHED,
    ERROR,
    RETRY,
    WAIT,
    RECHECK_UI,
    STOPPED
}

class StateMachine {

    @Volatile
    var currentState: AutomationState = AutomationState.IDLE
        private set

    private var previousState: AutomationState = AutomationState.IDLE
    private var retryCount: Int = 0
    private val maxRetry: Int = 3
    private var sameActionCount: Int = 0
    private var lastAction: String = ""
    private val maxSameAction: Int = 3

    private val listeners = mutableListOf<(AutomationState, AutomationState) -> Unit>()

    fun addStateChangeListener(listener: (AutomationState, AutomationState) -> Unit) {
        listeners.add(listener)
    }

    fun removeStateChangeListener(listener: (AutomationState, AutomationState) -> Unit) {
        listeners.remove(listener)
    }

    /** Tabel transisi eksplisit (P1): state -> himpunan state legal berikutnya. */
    private val transitions: Map<AutomationState, Set<AutomationState>> = mapOf(
        AutomationState.IDLE to setOf(AutomationState.CHECK_SERVICE, AutomationState.STOPPED),
        AutomationState.CHECK_SERVICE to setOf(AutomationState.OPEN_MELOLO, AutomationState.FIND_REWARD, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.OPEN_MELOLO to setOf(AutomationState.WAIT_FOR_UI, AutomationState.CHECK_SERVICE, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.WAIT_FOR_UI to setOf(AutomationState.FIND_REWARD, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.FIND_REWARD to setOf(AutomationState.OPEN_REWARD, AutomationState.FIND_NEXT_REWARD, AutomationState.FINISHED, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.OPEN_REWARD to setOf(AutomationState.WAIT_FOR_UI, AutomationState.FIND_CLAIM, AutomationState.FIND_NEXT_REWARD, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.FIND_CLAIM to setOf(AutomationState.CLICK_CLAIM, AutomationState.FIND_NEXT_REWARD, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.CLICK_CLAIM to setOf(AutomationState.WAIT_RESULT, AutomationState.RETRY, AutomationState.ERROR, AutomationState.STOPPED),
        AutomationState.WAIT_RESULT to setOf(AutomationState.VERIFY_SUCCESS, AutomationState.RETRY, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.VERIFY_SUCCESS to setOf(AutomationState.FIND_NEXT_REWARD, AutomationState.RETRY, AutomationState.STOPPED),
        AutomationState.FIND_NEXT_REWARD to setOf(AutomationState.OPEN_REWARD, AutomationState.FIND_REWARD, AutomationState.FINISHED, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.ERROR to setOf(AutomationState.RETRY, AutomationState.STOPPED),
        AutomationState.RETRY to setOf(AutomationState.WAIT, AutomationState.STOPPED),
        AutomationState.WAIT to setOf(AutomationState.RECHECK_UI, AutomationState.STOPPED),
        AutomationState.RECHECK_UI to setOf(AutomationState.FIND_REWARD, AutomationState.STOPPED, AutomationState.ERROR),
        AutomationState.FINISHED to setOf(AutomationState.IDLE),
        AutomationState.STOPPED to setOf(AutomationState.IDLE)
    )

    /** Terminal state: tidak ada jalan keluar kecuali reset ke IDLE. */
    fun isTerminal(): Boolean =
        currentState == AutomationState.FINISHED || currentState == AutomationState.STOPPED

    @Synchronized
    fun transitionTo(newState: AutomationState): Boolean {
        if (isTerminal() && newState != AutomationState.IDLE) {
            Logger.warn("StateMachine: refusing $currentState → $newState (terminal)")
            return false
        }
        val allowed = transitions[currentState] ?: emptySet()
        if (newState != currentState && newState !in allowed) {
            Logger.warn("StateMachine: invalid transition $currentState → $newState, blocked")
            return false
        }

        previousState = currentState
        currentState = newState
        Logger.state("$previousState → $newState")

        for (listener in listeners) {
            try {
                listener(previousState, newState)
            } catch (_: Exception) {
                // best-effort notification
            }
        }

        return true
    }

    @Synchronized
    fun recordAction(action: String): Boolean {
        if (action == lastAction) {
            sameActionCount++
            if (sameActionCount >= maxSameAction) {
                Logger.safety("Same action repeated $sameActionCount times: '$action' — stopping")
                transitionTo(AutomationState.STOPPED)
                return false
            }
        } else {
            sameActionCount = 1
            lastAction = action
        }
        return true
    }

    @Synchronized
    fun recordRetry(): Boolean {
        retryCount++
        if (retryCount >= maxRetry) {
            Logger.warn("StateMachine: max retry ($maxRetry) exhausted")
            transitionTo(AutomationState.STOPPED)
            return false
        }
        transitionTo(AutomationState.RETRY)
        return true
    }

    @Synchronized
    fun resetRetry() {
        retryCount = 0
    }

    @Synchronized
    fun resetSameAction() {
        sameActionCount = 0
        lastAction = ""
    }

    fun isRunning(): Boolean =
        currentState != AutomationState.IDLE &&
        currentState != AutomationState.STOPPED &&
        currentState != AutomationState.FINISHED

    fun isStopped(): Boolean = currentState == AutomationState.STOPPED

    fun getRetryCount(): Int = retryCount
    fun getSameActionCount(): Int = sameActionCount
    fun getLastAction(): String = lastAction
    fun getPreviousState(): AutomationState = previousState
}