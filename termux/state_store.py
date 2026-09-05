#!/usr/bin/env python3
"""Persistent state: state.json (harian), history.json (riwayat), event log.

~/.melolo-helper/
  state.json    -> status run hari ini (tahan restart)
  history.json  -> daftar run sebelumnya (max 30 hari)
  events.log    -> event protocol Termux<->APK (JSON lines)
"""
import json, time, os, uuid, fcntl
from datetime import datetime
from pathlib import Path

BASE = Path.home() / ".melolo-helper"
STATE = BASE / "state.json"
HISTORY = BASE / "history.json"
EVENTS = BASE / "events.log"
LOCK = BASE / "state.lock"
SCHEMA = 2

def today():
    return datetime.now().strftime("%Y-%m-%d")

def _locked(fn):
    """File lock agar dua proses tidak menulis state bersamaan (P0/P4)."""
    BASE.mkdir(parents=True, exist_ok=True)
    with open(LOCK, "w") as lf:
        try:
            fcntl.flock(lf, fcntl.LOCK_EX)
            return fn()
        finally:
            try: fcntl.flock(lf, fcntl.LOCK_UN)
            except Exception: pass

def load_state():
    def _load():
        try:
            raw = STATE.read_text()
            s = json.loads(raw)  # corrupt -> ValueError
            if not isinstance(s, dict):
                raise ValueError("not a dict")
            if s.get("schema", 1) < SCHEMA:
                s = migrate_state(s)
            if s.get("date") != today():
                archive_state(s)
                return fresh_state()
            return s
        except FileNotFoundError:
            return fresh_state()
        except (ValueError, OSError) as e:
            # recovery: backup corrupt + fresh (P0)
            try:
                STATE.rename(STATE.with_suffix(".corrupt"))
            except Exception:
                pass
            s = fresh_state()
            s["last_error"] = f"state corrupt, direset: {e}"
            save_state_nolock(s)
            return s
    try:
        return _locked(_load)
    except Exception:
        return fresh_state()  # fcntl tak tersedia -> best effort

def fresh_state():
    return {"schema": SCHEMA, "date": today(), "state": "IDLE",
            "run_id": None, "claims": 0,
            "runs": 0, "successful_runs": 0, "failed": 0,
            "recoveries": 0, "security_stops": 0,
            "started_at": None, "finished_at": None,
            "last_claim": None, "last_error": None,
            "claim_durations": []}

def migrate_state(s):
    s.setdefault("schema", SCHEMA)
    s.setdefault("run_id", None)
    s.setdefault("claim_durations", [])
    s["schema"] = SCHEMA
    return s

def save_state_nolock(s):
    BASE.mkdir(parents=True, exist_ok=True)
    s["checkin_date"] = s.get("checkin_date")
    tmp = STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(s, indent=2))
    os.replace(tmp, STATE)  # atomic write (P0)

def save_state(s):
    def _save():
        save_state_nolock(s)
    try:
        _locked(_save)
    except Exception:
        save_state_nolock(s)

def new_run():
    """Mulai run baru dengan run_id (P5)."""
    s = load_state()
    s["run_id"] = uuid.uuid4().hex[:8]
    s["runs"] += 1
    s["state"] = "CHECK_SERVICE"
    s["started_at"] = datetime.now().isoformat()
    s["finished_at"] = None
    save_state(s)
    emit("RUN_STARTED", run_id=s["run_id"])
    return s["run_id"]

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
