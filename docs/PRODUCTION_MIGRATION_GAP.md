# PRODUCTION_MIGRATION_GAP

| Area | Current | Target | Risk | Migration |
|------|---------|--------|------|-----------|
| Architecture | app flat helper/ | app/domain/application/infrastructure | Medium | Move Melolo domain to melolo/ package, keep facade |
| StateMachine | hardcode 3 before | config-driven maxRetry/maxSameAction | Low | configure() done |
| IPC | broadcast fire-and-forget | request_id ACK correlation | Medium | TermuxBridge EXPORTED + payload JSON |
| Account | single | multi-account registry+scheduler+UI_LOCK | High | accounts.py + multi_account.py incremental |
| Verification | boolean | SUCCESS/FAILURE/UNKNOWN | High | 3-state done |
| Ad | keyword fallback | ResourceMap + overlay validation | Medium | AdCloseDetector done |
| Safety | single list | HIGH vs MEDIUM | Low | split done |
| Domain model | implicit | explicit Account/Task/Run | Low | add models.py |

No credential/token/API forging - preserve safety boundary.
