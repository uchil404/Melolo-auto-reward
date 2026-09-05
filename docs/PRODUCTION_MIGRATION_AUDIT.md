# PRODUCTION_MIGRATION_AUDIT

A. CURRENT ARCHITECTURE
Termux controller.py <-> TermuxBridge BroadcastReceiver <-> RewardAccessibilityService -> AutomationEngine -> StateMachine/NodeFinder/RewardDetector/MeloloAdapter/ClickController/VerificationEngine/SafetyManager ; melolo/* (ResourceMap, TaskDetector, DailyTaskManager, AdClose)

B. CURRENT DATA FLOW
Event -> freshRoot -> snapshot -> MeloloTaskDetector (ResourceMap) -> SafetyGate -> Click -> before/after snapshot -> Verification (3-state)

C. CURRENT STATE MACHINE
AutomationState: IDLE, CHECK_SERVICE, OPEN_MELOLO, WAIT_FOR_UI, CHECK_IN..VERIFY_CHECK_IN, FIND_REWARD..VERIFY_SUCCESS, WATCH_REWARD..VERIFY_REWARD, NO_REWARD, ALREADY_COMPLETED etc. Transitions explicit table, terminal FINISHED/STOPPED.

D. CURRENT IPC FLOW
Termux am broadcast {command,request_id,account_id,run_id,payload} -> TermuxBridge EXPORTED -> ACK {request_id,account_id,state,result}

E. CURRENT ACCOUNT FLOW
Single-account (config.target_package=com.worldance.drama). Multi-account scaffolding: termux/accounts.py + multi_account.py (registry, UI_LOCK)

F. CURRENT TASK FLOW
FIND_REWARD->OPEN_REWARD->FIND_CLAIM->CLICK_CLAIM->WAIT_RESULT->VERIFY_SUCCESS->FIND_NEXT_REWARD ; CHECK_IN separate

G. CURRENT AD FLOW
AdLifecycleDetector + AdCloseDetector (close only in ad overlay, reject install/download/CTA)

H. CURRENT VERIFICATION FLOW
Verdict SUCCESS/FAILURE/UNKNOWN, BEFORE tepat sebelum click, rewardCounters observasi

I. CURRENT PERSISTENCE
~/.melolo-helper/{config.json, state.json(history), accounts/<id>/*, scheduler/ui.lock}, atomic write + fcntl lock + schema 3

J. CURRENT TEST COVERAGE
20 cases checklist documented, unit tests belum dijalankan di repo

K. CURRENT FAILURE MODES
recycle misuse fixed, duplicate scheduler fixed, resource-id scoring fixed, high/medium safety split
