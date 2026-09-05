#!/usr/bin/env python3
"""Multi-Account Orchestrator (observasi + legitimate flow, tanpa fake completion)
~/.melolo-helper/accounts/<id>/ {account.json, state.json, history.json}
"""
import json, uuid, time
from pathlib import Path
from datetime import datetime

BASE = Path.home() / ".melolo-helper"
ACCS = BASE / "accounts"
QUEUE = BASE / "scheduler" / "queue.json"

def list_accounts():
    if not ACCS.exists(): return []
    return [p.name for p in ACCS.iterdir() if p.is_dir()]

def add_account(label, profile="default"):
    ACCS.mkdir(parents=True, exist_ok=True)
    aid = f"acc-{len(list_accounts())+1:03d}"
    d = ACCS / aid; d.mkdir()
    acc = {"id":aid,"label":label,"enabled":True,"android_profile":profile,"package":"com.worldance.drama","automation":{"check_in":True,"reward_scan":True}}
    (d/"account.json").write_text(json.dumps(acc,indent=2))
    (d/"state.json").write_text(json.dumps({"state":"NEW","last_run":None},indent=2))
    return aid

def get_state(aid):
    try: return json.loads((ACCS/aid/"state.json").read_text())
    except: return {"state":"NEW"}

def set_state(aid, state):
    p = ACCS/aid/"state.json"; s=get_state(aid); s["state"]=state; s["last_run"]=datetime.now().isoformat()
    p.write_text(json.dumps(s,indent=2))

def queue_run(aids):
    QUEUE.parent.mkdir(parents=True,exist_ok=True)
    q=[{"account_id":a,"status":"QUEUED","request_id":uuid.uuid4().hex[:8]} for a in aids]
    QUEUE.write_text(json.dumps(q,indent=2)); return q

if __name__ == "__main__":
    import sys
    if len(sys.argv)<2: print("usage: accounts [list|add LABEL|queue]"); raise SystemExit
    c=sys.argv[1]
    if c=="list":
        for a in list_accounts():
            s=get_state(a); print(f"{a:8} {s.get('state'):12} {s.get('last_run')}")
    elif c=="add": print(add_account(sys.argv[2] if len(sys.argv)>2 else "Account"))
    elif c=="queue": print(json.dumps(queue_run(list_accounts()),indent=2))
