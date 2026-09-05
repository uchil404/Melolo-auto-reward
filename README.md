# Melolo Reward Helper

**Automated reward claim assistant for the Melolo Android application using Accessibility Service.**

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      TERMUX (Python 3)                          │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │controller│  │scheduler │  │ logger   │  │  config.json  │  │
│  │   .py    │  │   .py    │  │   .py    │  │               │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───────┬───────┘  │
│       │              │              │               │          │
│       └──────────────┼──────────────┼───────────────┘          │
│                      │              │                           │
│              ┌───────┴──────┐  ┌────┴────┐                      │
│              │  melolo-     │  │  ~/.    │                      │
│              │  helper CLI  │  │ melolo/ │                      │
│              └───────┬──────┘  │ logs/   │                      │
│                      │         └─────────┘                      │
└──────────────────────┼──────────────────────────────────────────┘
                       │ Intent / Broadcast / SharedPreferences
                       │ (no root required)
┌──────────────────────┼──────────────────────────────────────────┐
│                      ▼          ANDROID APK (Kotlin)            │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              RewardAccessibilityService                   │  │
│  │              (AccessibilityService)                       │  │
│  └──────────────────────────┬───────────────────────────────┘  │
│                             │                                   │
│  ┌──────────────────────────┼───────────────────────────────┐  │
│  │                          ▼                                │  │
│  │  ┌──────────────┐  ┌───────────┐  ┌──────────────────┐   │  │
│  │  │AutomationEngine│  │StateMachine│  │  SafetyManager   │   │  │
│  │  └───────┬──────┘  └─────┬─────┘  └────────┬─────────┘   │  │
│  │          │               │                  │             │  │
│  │  ┌───────┴───────────────┴──────────────────┴─────────┐   │  │
│  │  │                    NodeFinder                       │   │  │
│  │  └───┬──────────────────┬──────────────────┬──────────┘   │  │
│  │      │                  │                  │              │  │
│  │  ┌───┴──────┐  ┌────────┴──────┐  ┌───────┴──────────┐   │  │
│  │  │RewardDet.│  │ClickController│  │VerificationEngine│   │  │
│  │  └──────────┘  └───────────────┘  └──────────────────┘   │  │
│  │                                                            │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │  │
│  │  │ TermuxBridge │  │   Logger     │  │ MainActivity │    │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

## Design Principles

| Principle | Implementation |
|-----------|---------------|
| **No private API** | Uses only public `AccessibilityService` API |
| **No reverse engineering** | Reads UI nodes, not network traffic |
| **No root** | Standard Android permissions only |
| **No CAPTCHA bypass** | Detects and stops; requests user intervention |
| **No coordinate reliance** | Priority: resource-id → contentDescription → text → hierarchy → coordinate fallback (configurable) |
| **Confidence scoring** | Every candidate scored; threshold-gated clicking |
| **State machine** | Deterministic flow with error recovery paths |
| **Configurable** | Package name, selectors, thresholds all in `config.json` |

## State Machine

```
IDLE → CHECK_SERVICE → OPEN_MELOLO → WAIT_FOR_UI → FIND_REWARD
→ OPEN_REWARD → FIND_CLAIM → CLICK_CLAIM → WAIT_RESULT
→ VERIFY_SUCCESS → FIND_NEXT_REWARD → FINISHED

Failure: ERROR → RETRY → WAIT → RECHECK_UI → (retry exhausted) → STOPPED
```

## Project Structure

```
melolo-reward-helper/
├── android/                    # Android APK (Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/melolo/helper/
│   │   │   ├── MainActivity.kt
│   │   │   ├── RewardAccessibilityService.kt
│   │   │   ├── AutomationEngine.kt
│   │   │   ├── StateMachine.kt
│   │   │   ├── NodeFinder.kt
│   │   │   ├── RewardDetector.kt
│   │   │   ├── ClickController.kt
│   │   │   ├── VerificationEngine.kt
│   │   │   ├── SafetyManager.kt
│   │   │   ├── TermuxBridge.kt
│   │   │   └── Logger.kt
│   │   ├── res/xml/accessibility_service_config.xml
│   │   ├── res/values/strings.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── termux/                     # Termux controller (Python)
│   ├── melolo-helper           # CLI executable
│   ├── config.json             # Configuration
│   ├── logger.py               # Logging module
│   ├── controller.py           # Main controller
│   ├── scheduler.py            # Time-based scheduler
│   └── requirements.txt
├── docs/
│   ├── INSTALL.md
│   ├── TERMUX.md
│   ├── ACCESSIBILITY.md
│   └── TROUBLESHOOTING.md
└── README.md
```

## Quick Start

```bash
# 1. Install APK
cd android && ./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# 2. Enable Accessibility Service
# Settings → Accessibility → Melolo Reward Helper → ON

# 3. Setup Termux controller
cd termux
pkg install python termux-api
pip install -r requirements.txt
chmod +x melolo-helper
cp melolo-helper $PREFIX/bin/

# 4. Run setup wizard
melolo-helper setup

# 5. Inspect UI (discover selectors)
melolo-helper inspect

# 6. Test (no actual claims)
melolo-helper test

# 7. Start automation
melolo-helper start
```

## CLI Commands

| Command | Description |
|---------|-------------|
| `melolo-helper start` | Start automation |
| `melolo-helper stop` | Stop automation gracefully |
| `melolo-helper status` | Show current status |
| `melolo-helper inspect` | Inspect UI node hierarchy |
| `melolo-helper logs` | View recent logs |
| `melolo-helper config` | Show/edit configuration |
| `melolo-helper test` | Dry-run test mode |
| `melolo-helper setup` | First-run setup wizard |
| `melolo-helper schedule --time HH:MM` | Schedule daily run |
| `melolo-helper emergency-stop` | Immediate halt |

## Safety

- CAPTCHA/verification detection → immediate stop
- Max retry per action → 3 (configurable)
- Max same-action repeat → 3 (configurable)
- Max runtime → 30 minutes (configurable)
- No credential storage
- No network interception
- All actions logged to `~/.melolo-helper/logs/`

## License

This project is for educational purposes. Use responsibly and in accordance with the Melolo application's terms of service.