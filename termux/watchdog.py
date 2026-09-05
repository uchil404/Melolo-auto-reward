#!/usr/bin/env python3
"""Watchdog: cek APK/Accessibility/stuck/timeout/state (usulan #8).

Dipakai scheduler sebelum run & berkala saat automation berjalan.
Recovery berjenjang: re-scan -> retry -> reopen -> restart -> STOP.
"""
import time
import logger
import state_store as st

STUCK_LIMIT = {"FIND_REWARD": 60, "FIND_CLAIM": 60, "WAIT_RESULT": 45}
_entered = {}

def note_state(state):
    _entered[state] = _entered.get(state, time.time())

def check(state, accessibility_ok=True, apk_ok=True):
    """Kembalikan 'ok' | 'rescan' | 'reopen' | 'restart' | 'stop'."""
    now = time.time()
    if not apk_ok:
        return "restart"
    if not accessibility_ok:
        return "stop"
    since = _entered.get(state, now)
    if time.time() - since > STUCK_LIMIT.get(state, 120):
        s = st.load_state()
        rec = s.get("recoveries", 0)
        if rec == 0: return "rescan"
        if rec == 1: return "reopen"
        if rec == 2: return "restart"
        return "stop"
    return "ok"

def apply(action):
    s = st.load_state()
    if action == "ok":
        return True
    s["recoveries"] = s.get("recoveries", 0) + 1
    st.save_state(s)
    st.emit("EVENT", action=f"watchdog:{action}")
    logger.warn(f"Watchdog: {action} (recovery #{s['recoveries']})")
    return action not in ("stop",)
