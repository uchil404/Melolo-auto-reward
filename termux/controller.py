#!/usr/bin/env python3
"""
Melolo Reward Helper — Termux Controller

Manages the lifecycle of the Melolo Reward Helper automation.
Communicates with the Android APK via broadcast intents and SharedPreferences.
"""

import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional

import logger

# --- Paths ---
CONFIG_PATH = Path.home() / ".melolo-helper" / "config.json"
DEFAULT_CONFIG = Path(__file__).parent / "config.json"
HELPER_PACKAGE = "com.melolo.helper"
COMMAND_ACTION = "com.melolo.helper.COMMAND"
STATUS_ACTION = "com.melolo.helper.STATUS"

# --- Config ---

def load_config() -> dict:
    """Load configuration from ~/.melolo-helper/config.json or default."""
    config_path = CONFIG_PATH
    if not config_path.exists():
        config_path = DEFAULT_CONFIG

    if not config_path.exists():
        logger.error(f"Config not found at {config_path}")
        return {}

    try:
        with open(config_path, "r") as f:
            return json.load(f)
    except Exception as e:
        logger.error(f"Failed to load config: {e}")
        return {}


def save_config(config: dict) -> bool:
    """Save configuration to ~/.melolo-helper/config.json."""
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    try:
        with open(CONFIG_PATH, "w") as f:
            json.dump(config, f, indent=2)
        logger.info(f"Config saved to {CONFIG_PATH}")
        return True
    except Exception as e:
        logger.error(f"Failed to save config: {e}")
        return False


def push_config_to_apk(config: dict) -> bool:
    """Push configuration to the APK via SharedPreferences write."""
    # Since we can't directly write to another app's SharedPreferences,
    # we send the config via broadcast extras.
    # The APK's TermuxBridge will pick it up.

    if not config:
        logger.error("No config to push")
        return False

    automation = config.get("automation", {})
    selectors = config.get("selectors", {})
    safety = config.get("safety", {})

    extras = []
    extras.append(f"--es target_package \"{config.get('target_package', '')}\"")
    extras.append(f"--ez enabled {str(automation.get('enabled', False)).lower()}")
    extras.append(f"--ei max_retry {automation.get('max_retry', 3)}")
    extras.append(f"--el click_delay_ms {automation.get('click_delay_ms', 1200)}")
    extras.append(f"--el page_timeout_ms {automation.get('page_timeout_ms', 10000)}")
    extras.append(f"--el scan_interval_ms {automation.get('scan_interval_ms', 1500)}")
    extras.append(f"--ei max_runtime_minutes {automation.get('max_runtime_minutes', 30)}")
    extras.append(f"--es reward_keywords \"{','.join(selectors.get('reward_keywords', []))}\"")
    extras.append(f"--es claim_keywords \"{','.join(selectors.get('claim_keywords', []))}\"")
    extras.append(f"--es resource_id_patterns \"{','.join(selectors.get('resource_id_patterns', []))}\"")
    extras.append(f"--ez stop_on_captcha {str(safety.get('stop_on_captcha', True)).lower()}")
    extras.append(f"--ez stop_on_security_check {str(safety.get('stop_on_security_check', True)).lower()}")
    extras.append(f"--ei max_same_action {safety.get('max_same_action', 3)}")
    extras.append(f"--ei confidence_threshold {safety.get('confidence_threshold', 70)}")
    extras.append(f"--ez coordinate_fallback_enabled {str(safety.get('coordinate_fallback_enabled', False)).lower()}")

    cmd = f"am broadcast -a {COMMAND_ACTION} -n {HELPER_PACKAGE}/.TermuxBridge --es command CONFIG {' '.join(extras)}"
    return run_adb_broadcast(cmd)


# --- ADB / Broadcast ---

def run_adb_broadcast(command: str) -> bool:
    """Execute an Android broadcast command."""
    try:
        result = subprocess.run(
            command,
            shell=True,
            capture_output=True,
            text=True,
            timeout=10
        )
        if result.returncode == 0:
            logger.debug(f"Broadcast OK: {result.stdout.strip()}")
            return True
        else:
            logger.error(f"Broadcast failed: {result.stderr.strip()}")
            return False
    except subprocess.TimeoutExpired:
        logger.error("Broadcast timed out")
        return False
    except Exception as e:
        logger.error(f"Broadcast error: {e}")
        return False


def send_command(command: str) -> bool:
    """Send a command to the APK via broadcast."""
    cmd = f"am broadcast -a {COMMAND_ACTION} -n {HELPER_PACKAGE}/.TermuxBridge --es command {command}"
    return run_adb_broadcast(cmd)


def send_start() -> bool:
    return send_command("START")


def send_stop() -> bool:
    return send_command("STOP")


def send_inspect() -> bool:
    return send_command("INSPECT")


def send_emergency_stop() -> bool:
    return send_command("EMERGENCY_STOP")


def send_test() -> bool:
    return send_command("TEST")


def send_status_request() -> bool:
    return send_command("STATUS")


# --- Status ---

def get_status() -> dict:
    """Get current status from the APK."""
    return {
        "apk_service": "UNKNOWN",
        "accessibility": "UNKNOWN",
        "automation": "UNKNOWN",
        "state": "IDLE",
        "claims": 0,
        "last_claim": "NONE",
        "last_error": "NONE",
    }


def display_status():
    """Display the current status in a formatted way."""
    status = get_status()
    config = load_config()

    print()
    print("Melolo Reward Helper")
    print("-" * 22)
    print(f"APK Service   : {status.get('apk_service', 'UNKNOWN')}")
    print(f"Accessibility : {status.get('accessibility', 'UNKNOWN')}")
    print(f"Automation    : {status.get('automation', 'UNKNOWN')}")
    print(f"State         : {status.get('state', 'IDLE')}")
    print(f"Claims Today  : {status.get('claims', 0)}")
    print(f"Last Claim    : {status.get('last_claim', 'NONE')}")
    print(f"Last Error    : {status.get('last_error', 'NONE')}")
    print(f"Target        : {config.get('target_package', 'NOT SET')}")
    print()


# --- Setup Wizard ---

def run_setup():
    """Run the first-time setup wizard."""
    print()
    print("=" * 50)
    print("  Melolo Reward Helper — Setup Wizard")
    print("=" * 50)
    print()

    config = load_config()
    if not config:
        config = {
            "target_package": "",
            "automation": {"enabled": False, "max_retry": 3, "click_delay_ms": 1200,
                           "page_timeout_ms": 10000, "scan_interval_ms": 1500, "max_runtime_minutes": 30},
            "selectors": {"reward_keywords": ["reward", "hadiah", "daily reward", "check in"],
                          "claim_keywords": ["claim", "klaim", "collect", "ambil"],
                          "resource_id_patterns": []},
            "safety": {"stop_on_captcha": True, "stop_on_security_check": True,
                       "max_same_action": 3, "confidence_threshold": 70,
                       "coordinate_fallback_enabled": False},
            "scheduler": {"enabled": False, "times": []},
            "notifications": {"enabled": True, "on_claim_success": True, "on_error": True,
                              "on_security_alert": True, "on_complete": True},
            "resume_after_restart": False
        }

    steps = [
        ("[1] Python", check_python),
        ("[2] Termux:API", check_termux_api),
        ("[3] APK Installed", check_apk_installed),
        ("[4] Accessibility", check_accessibility),
        ("[5] Target Package", lambda: prompt_target_package(config)),
        ("[6] Test Communication", test_communication),
        ("[7] Inspect UI", lambda: send_inspect()),
        ("[8] Save Configuration", lambda: save_config(config)),
    ]

    for label, step_func in steps:
        print(f"{label}...")
        result = step_func()
        if isinstance(result, bool) and not result and label.startswith("[") and int(label[1]) < 8:
            print(f"  ⚠ Warning: {label} check failed — continuing anyway")
        print()

    print("=" * 50)
    print("  Setup complete!")
    print("  Run 'melolo-helper status' to check status.")
    print("  Run 'melolo-helper inspect' to discover UI selectors.")
    print("  Run 'melolo-helper test' to test without claiming.")
    print("  Run 'melolo-helper start' to begin automation.")
    print("=" * 50)
    print()


def check_python() -> bool:
    version = sys.version_info
    ok = version.major >= 3 and version.minor >= 6
    print(f"  Python {version.major}.{version.minor}.{version.micro} {'✓' if ok else '✗'}")
    return ok


def check_termux_api() -> bool:
    try:
        result = subprocess.run(["termux-notification", "--help"], capture_output=True, timeout=5)
        ok = result.returncode == 0
        print(f"  Termux:API {'✓ available' if ok else '✗ not found'}")
        return ok
    except FileNotFoundError:
        print("  Termux:API ✗ not installed (run: pkg install termux-api)")
        return False
    except Exception:
        print("  Termux:API ✗ error")
        return False


def check_apk_installed() -> bool:
    try:
        result = subprocess.run(
            ["pm", "list", "packages", HELPER_PACKAGE],
            capture_output=True, text=True, timeout=5
        )
        ok = HELPER_PACKAGE in result.stdout
        print(f"  APK {'✓ installed' if ok else '✗ not installed'}")
        return ok
    except Exception:
        print("  APK check ✗ error")
        return False


def check_accessibility() -> bool:
    try:
        result = subprocess.run(
            ["settings", "get", "secure", "enabled_accessibility_services"],
            capture_output=True, text=True, timeout=5
        )
        ok = HELPER_PACKAGE in result.stdout
        print(f"  Accessibility {'✓ enabled' if ok else '✗ not enabled'}")
        if not ok:
            print("    → Settings → Accessibility → Melolo Reward Helper → ON")
        return ok
    except Exception:
        print("  Accessibility ✗ could not check")
        return False


def prompt_target_package(config: dict) -> bool:
    print(f"  Current target package: {config.get('target_package', 'NOT SET')}")
    print("  To discover the package name:")
    print("    1. Open the Melolo app")
    print("    2. Run: dumpsys window | grep mCurrentFocus")
    print("    3. Copy the package name (before the /)")
    new_pkg = input("  Enter package name (or press Enter to skip): ").strip()
    if new_pkg:
        config["target_package"] = new_pkg
        print(f"  ✓ Set to: {new_pkg}")
        return True
    return False


def test_communication() -> bool:
    ok = send_status_request()
    print(f"  Communication {'✓ OK' if ok else '✗ failed'}")
    return ok


# --- Emergency Stop ---

def emergency_stop():
    """Perform emergency stop."""
    logger.safety("EMERGENCY STOP initiated")
    print()
    print("⚠ EMERGENCY STOP ⚠")
    print()

    # Send stop to APK
    send_emergency_stop()

    # Cancel any pending scheduled jobs
    # (This would interact with cron/scheduler)

    logger.safety("Emergency stop complete")
    print("All automation halted. No further actions will be performed.")
    print()


# --- Main CLI Handler ---

def handle_command(args: list):
    """Route CLI commands to the appropriate handler."""
    if not args:
        display_status()
        return

    command = args[0].lower()

    if command == "start":
        logger.info("Starting automation...")
        config = load_config()
        push_config_to_apk(config)
        send_start()
        logger.info("Start command sent")
        time.sleep(1)
        display_status()

    elif command == "stop":
        logger.info("Stopping automation...")
        send_stop()
        logger.info("Stop command sent")

    elif command == "status":
        display_status()

    elif command == "inspect":
        logger.info("Starting inspect mode...")
        send_inspect()
        print("Inspect mode active. Check the APK or logcat for hierarchy dump.")
        print("Run: adb logcat -s MeloloHelper:D")

    elif command == "logs":
        lines = 50
        if len(args) > 1:
            try:
                lines = int(args[1])
            except ValueError:
                pass
        print(logger.read_logs(lines))

    elif command == "config":
        config = load_config()
        if len(args) > 1 and args[1] == "edit":
            print(json.dumps(config, indent=2))
            print("\nEdit ~/.melolo-helper/config.json to change settings.")
        else:
            print(json.dumps(config, indent=2))

    elif command == "test":
        logger.info("Starting test mode (no actual claims)...")
        config = load_config()
        push_config_to_apk(config)
        send_test()
        print("Test mode active. Check the APK or logcat for scan results.")
        print("Run: adb logcat -s MeloloHelper:D")

    elif command == "setup":
        run_setup()

    elif command == "schedule":
        handle_schedule(args[1:])

    elif command == "emergency-stop":
        emergency_stop()

    elif command == "login":
        import auto_reward as ar
        user = passwd = None
        for i, a in enumerate(args):
            if a == "--user" and i+1 < len(args): user = args[i+1]
            if a == "--pass" and i+1 < len(args): passwd = args[i+1]
        if not user: user = input("Email/HP: ").strip()
        if not passwd:
            import getpass; passwd = getpass.getpass("Password: ")
        ar.login(user, passwd)

    elif command == "auto":
        import auto_reward as ar
        import argparse
        user = passwd = None; cycles = 10
        for i, a in enumerate(args):
            if a == "--user" and i+1 < len(args): user = args[i+1]
            if a == "--pass" and i+1 < len(args): passwd = args[i+1]
            if a == "--cycles" and i+1 < len(args):
                try: cycles = int(args[i+1])
                except ValueError: pass
        ar.auto_run(user, passwd, watch_cycles=cycles)

    else:
        print(f"Unknown command: {command}")
        print()
        print("Available commands:")
        print("  start           Start automation")
        print("  stop            Stop automation")
        print("  status          Show current status")
        print("  inspect         Inspect UI hierarchy")
        print("  logs [N]        View recent logs")
        print("  config [edit]   Show/edit configuration")
        print("  test            Test mode (no claims)")
        print("  setup           First-run setup wizard")
        print("  schedule --time HH:MM   Schedule daily run")
        print("  emergency-stop  Immediate halt")
        print()


def handle_schedule(args: list):
    """Handle schedule command."""
    config = load_config()
    scheduler_config = config.get("scheduler", {})

    if not args:
        times = scheduler_config.get("times", [])
        if times:
            print(f"Scheduled times: {', '.join(times)}")
        else:
            print("No schedules configured.")
        print(f"Scheduler: {'enabled' if scheduler_config.get('enabled') else 'disabled'}")
        return

    if args[0] == "--time" and len(args) > 1:
        time_str = args[1]
        if ":" not in time_str:
            print(f"Invalid time format: {time_str}. Use HH:MM (e.g. 08:00)")
            return

        times = scheduler_config.get("times", [])
        if time_str not in times:
            times.append(time_str)
        scheduler_config["times"] = times
        scheduler_config["enabled"] = True
        config["scheduler"] = scheduler_config
        save_config(config)

        print(f"Added schedule: {time_str}")
        print(f"Note: Use Termux cron (termux-job-scheduler) or a cron job to trigger at {time_str}")

    elif args[0] == "--disable":
        scheduler_config["enabled"] = False
        config["scheduler"] = scheduler_config
        save_config(config)
        print("Scheduler disabled")

    elif args[0] == "--enable":
        scheduler_config["enabled"] = True
        config["scheduler"] = scheduler_config
        save_config(config)
        print("Scheduler enabled")

    elif args[0] == "--clear":
        scheduler_config["times"] = []
        scheduler_config["enabled"] = False
        config["scheduler"] = scheduler_config
        save_config(config)
        print("All schedules cleared")


def main():
    args = sys.argv[1:]
    handle_command(args)


if __name__ == "__main__":
    main()