#!/usr/bin/env python3
"""Termux:API wrapper - notifikasi, vibrate, toast, battery, wakelock, job-scheduler."""
import json, subprocess, shutil

def _run(cmd, timeout=10):
    if not shutil.which(cmd[0]):
        return None
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return r.stdout.strip() if r.returncode == 0 else None
    except Exception:
        return None

def notify(title, content, nid="melolo-reward"):
    if _run(["termux-notification", "--id", nid, "--title", title, "--content", content]) is None:
        print(f"[NOTIFY] {title}: {content}")

def vibrate(ms=500):
    _run(["termux-vibrate", "-d", str(ms)])

def toast(msg):
    if _run(["termux-toast", msg]) is None:
        print(f"[TOAST] {msg}")

def battery():
    out = _run(["termux-battery-status"])
    try:
        return json.loads(out) if out else {}
    except Exception:
        return {}

def battery_ok(min_pct=20):
    b = battery()
    pct = b.get("percentage", 100)
    status = b.get("status", "UNKNOWN")
    if status == "CHARGING":
        return True
    return pct >= min_pct

def wakelock_lock():
    _run(["termux-wake-lock"])

def wakelock_unlock():
    _run(["termux-wake-unlock"])

def schedule_job(script_path, period_ms=1800000):
    """Daftarkan job periodik via termux-job-scheduler."""
    return _run(["termux-job-scheduler", "--job-id", "1701",
                 "--period-ms", str(period_ms),
                 "--persisted", "true",
                 "--script", script_path])
