#!/usr/bin/env python3
"""multi_account: AccountRegistry/AccountScheduler/DailyState - UI_LOCK single session"""
import json, uuid
from pathlib import Path
from datetime import datetime
BASE=Path.home()/".melolo-helper"
ACCS=BASE/"accounts"
LOCK=BASE/"scheduler"/"ui.lock"
def registry(): 
    if not ACCS.exists(): return []
    return sorted(p.name for p in ACCS.iterdir() if p.is_dir())
def acquire_lock(aid):
    LOCK.parent.mkdir(parents=True,exist_ok=True)
    if LOCK.exists(): return False
    LOCK.write_text(aid); return True
def release_lock(): LOCK.unlink(missing_ok=True)
def next_account():
    """Pilih account READY berikutnya, acquire lock, return id atau None"""
    for a in registry():
        try:
            state=(ACCS/a/"state.json").read_text()
            if '"READY"' in state and acquire_lock(a): return a
        except: pass
    return None
