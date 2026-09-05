import json
from pathlib import Path
BASE=Path.home()/".melolo-helper"
ACTIVE=BASE/"scheduler"/"active.json"
def save_active(run): ACTIVE.parent.mkdir(parents=True,exist_ok=True); ACTIVE.write_text(json.dumps(run,indent=2))
def load_active(): return json.loads(ACTIVE.read_text()) if ACTIVE.exists() else None
def clear_active(): ACTIVE.unlink(missing_ok=True)
