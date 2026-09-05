#!/usr/bin/env python3
"""
Melolo Auto Reward - API client + auto claim loop.
Login: email/HP + password -> simpan token di ~/.melolo-helper/session.json
Jika endpoint resmi berubah, cukup sesuaikan BASE_URL / path di bawah.
"""
import json, time, os
from pathlib import Path
import urllib.request, urllib.parse

import logger
import termux_api as tx

BASE_URL = os.environ.get("MELOLO_BASE_URL", "https://api.melolo.example.com")
SESSION_PATH = Path.home() / ".melolo-helper" / "session.json"

def _req(method, path, data=None, token=None):
    url = BASE_URL + path
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "MeloloRewardHelper/1.0 Termux")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode() or "{}")
    except Exception as e:
        # Coba baca error body
        try:
            import urllib.error as ue
            if isinstance(e, ue.HTTPError):
                return {"_error": e.read().decode(), "_code": e.code}
        except Exception:
            pass
        return {"_error": str(e)}

def save_session(s): SESSION_PATH.parent.mkdir(parents=True, exist_ok=True); SESSION_PATH.write_text(json.dumps(s, indent=2))
def load_session():
    if SESSION_PATH.exists():
        try: return json.loads(SESSION_PATH.read_text())
        except Exception: return {}
    return {}

def login(identifier, password):
    """identifier = email atau no HP."""
    logger.info(f"Login sebagai {identifier} ...")
    res = _req("POST", "/v1/auth/login", {"identifier": identifier, "password": password})
    if res.get("_error"):
        logger.error(f"Login gagal: {res}");
        tx.notify("Melolo Login Gagal", str(res.get('_error'))[:200]); return None
    token = res.get("token") or res.get("access_token") or res.get("data", {}).get("token")
    if not token:
        logger.error(f"Login: token tidak ditemukan: {res}"); return None
    save_session({"token": token, "identifier": identifier, "ts": time.time()})
    logger.claim("Login berhasil, token tersimpan."); tx.notify("Melolo", "Login berhasil"); return token

def get_reward_status(token):
    return _req("GET", "/v1/rewards/status", token=token)

def claim_daily(token):
    return _req("POST", "/v1/rewards/daily-checkin", {}, token)

def claim_task(token, task_id):
    return _req("POST", f"/v1/rewards/tasks/{task_id}/claim", {}, token)

def watch_reward(token, seconds=30):
    """Simulasi nonton short-drama agar reward watch-time cair."""
    r = _req("POST", "/v1/rewards/watch", {"seconds": seconds}, token)
    return r

def auto_run(identifier=None, password=None, watch_cycles=10, watch_seconds=30, delay=5):
    sess = load_session()
    token = sess.get("token")
    if not token:
        if not (identifier and password):
            logger.error("Belum login. Jalankan: melolo-helper login --user EMAIL --pass PASS"); return False
        token = login(identifier, password)
        if not token: return False
    if not tx.battery_ok():
        msg = "Baterai rendah, auto-reward ditunda."
        logger.warn(msg); tx.notify("Melolo Ditunda", msg); return False
    tx.wakelock_lock()
    try:
        tx.notify("Melolo Auto", "Mulai auto reward...")
        st = get_reward_status(token)
        logger.info(f"Status reward: {st}")
        d = claim_daily(token)
        if d.get("_error"): logger.warn(f"Daily: {d}")
        else: logger.claim(f"Daily claimed: {d}"); tx.notify("Melolo Daily", "Check-in berhasil!"); tx.vibrate(300)
        tasks = st.get("tasks") or st.get("data", {}).get("tasks", []) if isinstance(st, dict) else []
        for t in tasks if isinstance(tasks, list) else []:
            tid = t.get("id"); 
            if t.get("claimable") and tid:
                r = claim_task(token, tid)
                logger.claim(f"Task {tid}: {r}"); time.sleep(2)
        for i in range(watch_cycles):
            r = watch_reward(token, watch_seconds)
            logger.claim(f"Watch {i+1}/{watch_cycles}: {r}")
            tx.toast(f"Watch {i+1}/{watch_cycles}")
            time.sleep(delay)
        tx.notify("Melolo Selesai", f"{watch_cycles} watch + daily selesai")
        logger.claim("Auto reward selesai."); return True
    finally:
        tx.wakelock_unlock()
