import uuid, time, json
from pathlib import Path
STATES=["RECEIVED","ACCEPTED","STARTED","FAILED","COMPLETED","TIMEOUT"]
def new_request(cmd,payload=None):
    rid=uuid.uuid4().hex[:8]
    return {"request_id":rid,"command":cmd,"payload":payload or {},"ts":time.time()}
def wait_ack(rid, timeout=5):
    # poll heartbeat.json / status.json for ACK - stub, real wait via broadcast result file
    return {"request_id":rid,"status":"TIMEOUT"}
