#!/usr/bin/env python3
"""Status model baru: installation/accessibility/service/automation/command terpisah"""
import time, json, subprocess
from pathlib import Path
BASE=Path.home()/".melolo-helper"
PREFS=BASE/"heartbeat.json"
TTL=30

def check_installation():
    try: return subprocess.run(["pm","list","packages","com.melolo.helper"],capture_output=True,text=True,timeout=5).stdout.find("com.melolo.helper")!=-1
    except: return False
def check_accessibility():
    try: out=subprocess.run(["settings","get","secure","enabled_accessibility_services"],capture_output=True,text=True,timeout=5).stdout
    except: return False
    # normalisasi com.melolo.helper/.RewardAccessibilityService vs full
    return "com.melolo.helper" in out and "RewardAccessibilityService" in out
def check_service_liveness():
    try:
        j=json.loads(PREFS.read_text()) if PREFS.exists() else {}
        last=j.get("service_last_seen",0)
        return (time.time()-last) <= TTL, last
    except: return False, 0
