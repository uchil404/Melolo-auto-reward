import json
from pathlib import Path
from datetime import datetime
BASE=Path.home()/".melolo-helper"
def daily_path(aid): return BASE/"accounts"/aid/"daily.json"
def load_daily(aid):
    p=daily_path(aid)
    today=datetime.now().strftime("%Y-%m-%d")
    if p.exists():
        d=json.loads(p.read_text())
        if d.get("date")==today: return d
    return {"date":today,"tasks":{},"completed_count":0,"failed_count":0}
def save_daily(aid, d): 
    p=daily_path(aid); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(d,indent=2))
