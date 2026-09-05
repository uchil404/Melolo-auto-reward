import time, json, uuid
from pathlib import Path
BASE=Path.home()/".melolo-helper"
HB=BASE/"heartbeat.json"
def heartbeat(alive=True):
    HB.parent.mkdir(parents=True,exist_ok=True)
    d={"service_instance_id":uuid.uuid4().hex[:8],"service_started_at":time.time(),"service_last_seen":time.time(),"service": "alive" if alive else "dead"}
    HB.write_text(json.dumps(d))
    return d
