# REVERSE_ENGINEERING_REPOSITORY_AUDIT (STEP 1)

## Repository Baseline
- branch: main, latest 0bd28a3 remote / 8e99a96..d76c18a local
- structure: android/ (Gradle, Kotlin), termux/ (Python), docs/
- Android: AccessibilityService, Kotlin, Gradle
- Termux: controller.py, state_store.py, scheduler.py, snapshot.py
- Tests: checklist documented, unit tests pending

## Current Architecture
Termux -> IPC (am broadcast request_id) -> AccessibilityService -> AutomationEngine -> StateMachine -> Detector (MeloloAdapter/ResourceMap) -> ClickController/ActionExecutor -> Verification -> SafetyGate

## Current Problems
Architecture: flat helper, no domain separation
Lifecycle: freshRoot fixed, no stale node
Task detection: generic keywords -> ResourceMap priority done
Action: duplicate retry fixed, actionable resolve done
Verification: 3-state SUCCESS/FAILURE/UNKNOWN done
Safety: HIGH/MEDIUM split done
IPC: EXPORTED receiver + ACK done
Persistence: atomic + lock + schema 3 done
Scheduler: single UI_LOCK done
Testing: 20 cases pending execution

## Reverse Engineering Gap
RE 5.4.4 IDs (check_in_*, claim_ad, cash_reward, needCaptcha) mapped to MeloloResourceMap; generic keyword fallback removed. Remaining: full DailyTaskManager integration, Ad engine full, multi-account persistence.
