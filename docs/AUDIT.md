# AUDIT — Melolo-auto-reward (commit 0bd28a3 + lokal 8e99a96)

## Artefak dibaca
android/app/src/main/java/com/melolo/helper/*
termux/*, config.json, README.md, docs/*

## Arsitektur sekarang
Termux (controller.py, state_store, scheduler, termux_api) --am broadcast--> TermuxBridge (BroadcastReceiver) --> RewardAccessibilityService --> AutomationEngine --> StateMachine/NodeFinder/RewardDetector/MeloloAdapter/ClickController/VerificationEngine/SafetyManager

## Dependency graph
RewardAccessibilityService -> AutomationEngine -> StateMachine, ClickController, VerificationEngine, SafetyManager, NodeFinder, MeloloAdapter
TermuxBridge <-> state_store (via SharedPreferences + broadcast ACK baru)

## State flow (aktual vs komentar)
Komentar: FIND_REWARD->OPEN_REWARD->FIND_CLAIM->CLICK_CLAIM
Aktual sebelum fix: OPEN_REWARD klik langsung ke WAIT_FOR_UI (bypass FIND_CLAIM) - P0. Sudah diperbaiki di 8e99a96: OPEN_REWARD -> FIND_CLAIM.

## IPC Android<->Termux
Termux `am broadcast -a com.melolo.helper.COMMAND --es command --es request_id --es payload(json)` -> TermuxBridge (EXPORTED) -> ACK `com.melolo.helper.STATUS {request_id,account_id,state,result}`. Sebelum fix: receiver NOT_EXPORTED + payload CONFIG diabaikan + am return 0 dianggap sukses - P0.

## Account/session flow
Single-account oriented (config.target_package). Belum ada multi-account registry/queue/ui.lock.

## Task/ad/verification flow
Task: generic keyword scoring. Ad: lifecycle belum. Verification: UI changed => success (false) - P0, rewardCounters kosong - P0.

## P0 bug list
1. Node lifecycle recycle misuse 2. OPEN_REWARD bypass 3. Verification boolean SUCCESS 4. BEFORE snapshot timing 5. FINISHED->STOPPED 6. maxRetry hardcode 7. maxSameAction hardcode 8. double RETRY transition 9. TermuxBridge NOT_EXPORTED 10. NodeFinder non-clickable candidate 11. resource-id scoring 50 < threshold 70 12. duplicate scheduler

## File ubah / baru
Ubah: StateMachine.kt, AutomationEngine.kt, RewardAccessibilityService.kt, TermuxBridge.kt, ClickController.kt, NodeFinder.kt, VerificationEngine.kt, SafetyManager.kt, MeloloAdapter.kt, Termux controller.py, state_store.py
Baru: melolo/MeloloResourceMap.kt, MeloloTaskDetector.kt, DailyTaskManager.kt, AdCloseDetector.kt, AdCloseController.kt, AccountManager.kt, termux/multi_account/*, docs/*

## Test perlu
20 test W: CHECK_IN/TOMORROW, UNKNOWN tidak count, security STOP, close vs install, account scheduler, idempotensi.
