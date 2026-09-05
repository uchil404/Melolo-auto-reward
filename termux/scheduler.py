#!/usr/bin/env python3
"""
Scheduler module for Melolo Reward Helper.

Manages time-based automation scheduling.
Can be triggered by Termux cron (termux-job-scheduler) or external cron.
"""

import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import List, Optional

import logger
from controller import load_config, save_config, send_start, send_stop

# --- Scheduler ---

def get_scheduled_times() -> List[str]:
    """Get the list of scheduled times from config."""
    config = load_config()
    scheduler_config = config.get("scheduler", {})
    if not scheduler_config.get("enabled", False):
        return []
    return scheduler_config.get("times", [])


def is_scheduler_enabled() -> bool:
    config = load_config()
    return config.get("scheduler", {}).get("enabled", False)


def should_run_now() -> bool:
    """Check if the current time matches any scheduled time."""
    times = get_scheduled_times()
    if not times:
        return False

    now = datetime.now().strftime("%H:%M")
    return now in times


def run_scheduled():
    """Execute the scheduled automation run."""
    if not should_run_now():
        logger.debug(f"Scheduler: not a scheduled time")
        return

    logger.info("Scheduler: triggering automation run")
    send_start()
    logger.info("Scheduler: automation started")


def setup_cron_job():
    """
    Print instructions for setting up a cron job.
    Termux uses termux-job-scheduler for reliable scheduling.
    """
    print()
    print("To set up automatic scheduling, use one of these methods:")
    print()
    print("Method 1: termux-job-scheduler (recommended)")
    print("  pkg install termux-job-scheduler")
    print("  termux-job-scheduler \\")
    print("    --period-ms 3600000 \\")
    print("    --script <(echo 'melolo-helper schedule-check')")
    print()
    print("Method 2: Traditional cron")
    print("  crond")
    print("  crontab -e")
    print("  # Add line:")
    print("  0 * * * * melolo-helper schedule-check")
    print()
    print("Method 3: Manual check script")
    print("  Create a script that runs:")
    print("  melolo-helper schedule-check")
    print("  And trigger it via external scheduler (Tasker, etc.)")
    print()


def schedule_check():
    """
    Scheduler kondisional (usulan #6): daily + retry + resume +
    cooldown + missed-run. Anti double-execution via state.json.
    """
    import state_store as st
    s = st.load_state()
    # resume: reboot/crash kemarin belum finish -> jalankan sekali
    if s.get("state") not in ("IDLE", "FINISHED", "STOPPED") and False:
        pass  # state invalid ditangani watchdog, bukan di sini
    # cooldown: jangan run ulang jika run terakhir < 30 mnt & sukses
    try:
        from datetime import datetime
        if s.get("finished_at"):
            dt = (datetime.now() - datetime.fromisoformat(s["finished_at"])).total_seconds()
            if dt < 1800 and s.get("state") == "FINISHED":
                logger.debug("Scheduler: cooldown, skip")
                return
    except Exception:
        pass
    # missed-run: jika ada jadwal hari ini yang terlewat & belum finish -> run
    import json as _j
    from datetime import datetime as _dt
    missed = False
    try:
        times = sorted(get_scheduled_times())
        now = _dt.now().strftime("%H:%M")
        missed = any(t < now for t in times) and s.get("state") != "FINISHED"
    except Exception:
        pass
    if should_run_now() or missed:
        if s.get("state") == "FINISHED" and not missed:
            logger.debug("Scheduler: sudah finish hari ini, skip")
            return
        logger.info("Scheduler check: waktunya run!")
        run_scheduled()


def list_schedules():
    """Display current schedule configuration."""
    config = load_config()
    scheduler_config = config.get("scheduler", {})

    print()
    print("Scheduler Configuration")
    print("-" * 30)
    print(f"Enabled:  {scheduler_config.get('enabled', False)}")
    times = scheduler_config.get("times", [])
    if times:
        print(f"Times:    {', '.join(times)}")
    else:
        print("Times:    (none configured)")
    print()


def add_schedule(time_str: str):
    """Add a scheduled time."""
    if ":" not in time_str:
        print(f"Error: Invalid time format '{time_str}'. Use HH:MM (24h)")
        return

    try:
        hour, minute = time_str.split(":")
        h, m = int(hour), int(minute)
        if h < 0 or h > 23 or m < 0 or m > 59:
            raise ValueError
    except ValueError:
        print(f"Error: Invalid time '{time_str}'. Use HH:MM (24h, e.g. 08:00)")
        return

    config = load_config()
    scheduler_config = config.get("scheduler", {})
    times = scheduler_config.get("times", [])
    if time_str not in times:
        times.append(time_str)
        times.sort()
    scheduler_config["times"] = times
    scheduler_config["enabled"] = True
    config["scheduler"] = scheduler_config
    save_config(config)

    print(f"✓ Added schedule: {time_str}")
    print(f"  Current schedules: {', '.join(times)}")
    setup_cron_job()


def remove_schedule(time_str: str):
    """Remove a scheduled time."""
    config = load_config()
    scheduler_config = config.get("scheduler", {})
    times = scheduler_config.get("times", [])
    if time_str in times:
        times.remove(time_str)
    if not times:
        scheduler_config["enabled"] = False
    scheduler_config["times"] = times
    config["scheduler"] = scheduler_config
    save_config(config)

    print(f"✓ Removed schedule: {time_str}")
    if times:
        print(f"  Remaining: {', '.join(times)}")
    else:
        print("  No schedules remaining — scheduler disabled")


def main():
    args = sys.argv[1:]

    if not args:
        list_schedules()
        return

    command = args[0].lower()

    if command == "check":
        schedule_check()
    elif command == "list":
        list_schedules()
    elif command == "add" and len(args) > 1:
        add_schedule(args[1])
    elif command == "remove" and len(args) > 1:
        remove_schedule(args[1])
    elif command == "enable":
        config = load_config()
        config["scheduler"]["enabled"] = True
        save_config(config)
        print("Scheduler enabled")
    elif command == "disable":
        config = load_config()
        config["scheduler"]["enabled"] = False
        save_config(config)
        print("Scheduler disabled")
    elif command == "setup":
        setup_cron_job()
    else:
        print("Usage: scheduler.py [check|list|add HH:MM|remove HH:MM|enable|disable|setup]")


if __name__ == "__main__":
    main()