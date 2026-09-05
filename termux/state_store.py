#!/usr/bin/env python3
"""Persistent state: state.json (harian), history.json (riwayat), event log.

~/.melolo-helper/
  state.json    -> status run hari ini (tahan restart)
  history.json  -> daftar run sebelumnya (max 30 hari)
  events.log    -> event protocol Termux<->APK (JSON lines)
"""
import json, time
from datetime import datetime
from pathlib import Path

BASE = Path.home() / ".melolo-helper"
STATE = BASE / "state.json"
HISTORY = BASE / "history.json"
EVENTS = BASE / "events.log"

def today():
    return datetime.now().strftime("%Y-%m-%d")

def load_state():
    try:
        s = json.loads(STATE.read_text())
        if s.get("date") != today():  # hari baru -> reset
            archive_state(s)
            return fresh_state()
        return s
    except Exception:
        return fresh_state()

def fresh_state():
    return {"date": today(), "state": "IDLE", "claims": 0,
            "runs": 0, "successful_runs": 0, "failed": 0,
            "recoveries": 0, "security_stops": 0,
            "started_at": None, "finished_at": None,
            "last_claim": None, "last_error": None,
            "claim_durations": []}

def save_state(s):
    BASE.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(s, indent=2))

def archive_state(s):
    try:
        h = json.loads(HISTORY.read_text()) if HISTORY.exists() else []
    except Exception:
        h = []
    h.append(s)
    HISTORY.write_text(json.dumps(h[-30:], indent=2))

def emit(event, **kw):
    """Event protocol: COMMAND/STATUS/EVENT/ERROR/CLAIM_SUCCESS/
    CLAIM_FAILED/SECURITY_STOP/UI_CHANGED."""
    BASE.mkdir(parents=True, exist_ok=True)
    rec = {"event": event, "timestamp": datetime.now().isoformat(),
           "state": load_state().get("state"), **kw}
    with open(EVENTS, "a") as f:
        f.write(json.dumps(rec) + "\n")
    # update state ringkas untuk event penting
    s = load_state()
    if event == "CLAIM_SUCCESS":
        s["claims"] += 1
        s["last_claim"] = rec["timestamp"]
        if kw.get("duration_s") is not None:
            s["claim_durations"].append(kw["duration_s"])
    elif event == "CLAIM_FAILED":
        s["failed"] += 1
        s["last_error"] = str(kw.get("error", ""))[:300]
    elif event == "SECURITY_STOP":
        s["security_stops"] += 1
        s["last_error"] = "SECURITY_STOP: " + str(kw.get("reason", ""))[:200]
        s["state"] = "STOPPED"
    save_state(s)
    return rec
