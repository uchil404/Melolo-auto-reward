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

    @Synchronized
    fun transitionTo(newState: AutomationState): Boolean {
        if (currentState == AutomationState.STOPPED && newState != AutomationState.IDLE) {
            Logger.warn("StateMachine: refusing transition to $newState while STOPPED")
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