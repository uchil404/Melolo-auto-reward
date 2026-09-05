#!/usr/bin/env python3
"""Google OAuth otomatis untuk Termux - tanpa paste token manual.

Alur: buka browser -> user login Google -> redirect ke localhost ->
tukar code jadi id_token -> langsung dipakai google_login().
Client ID diambil dari env MELOLO_GOOGLE_CLIENT_ID atau config
~/.melolo-helper/config.json (google.client_id). Jika belum ada,
tool meminta sekali lalu menyimpannya.
"""
import json, os, threading, urllib.parse, urllib.request, webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

import logger

CFG = Path.home() / ".melolo-helper" / "config.json"
PORT = 8765
REDIRECT = f"http://127.0.0.1:{PORT}/callback"
GOOGLE_AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_TOKEN = "https://oauth2.googleapis.com/token"

def _load_client_id():
    if os.environ.get("MELOLO_GOOGLE_CLIENT_ID"):
        return os.environ["MELOLO_GOOGLE_CLIENT_ID"]
    try:
        cfg = json.loads(CFG.read_text())
        cid = cfg.get("google", {}).get("client_id")
        if cid and "ISI" not in cid:
            return cid
    except Exception:
        pass
    return None

def _save_client_id(cid):
    CFG.parent.mkdir(parents=True, exist_ok=True)
    cfg = {}
    try:
        cfg = json.loads(CFG.read_text())
    except Exception:
        pass
    cfg.setdefault("google", {})["client_id"] = cid
    CFG.write_text(json.dumps(cfg, indent=2))

def _post(url, data):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body)
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode())

def get_id_token(client_id=None):
    """Jalankan OAuth flow, kembalikan id_token. Murni otomatis via browser."""
    client_id = client_id or _load_client_id()
    if not client_id:
        print()
        print("Client ID Google belum diset.")
        print("Ambil dari: Google Cloud Console -> Credentials -> OAuth client")
        print("(pakai client ID milik aplikasi Melolo / Firebase project-nya).")
        client_id = input("Paste Google Client ID sekali saja: ").strip()
        if not client_id:
            logger.error("Client ID kosong."); return None
        _save_client_id(client_id)
        print("Client ID tersimpan, tidak ditanya lagi.")

    code_box = {}
    class H(BaseHTTPRequestHandler):
        def do_GET(self):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if "code" in q:
                code_box["code"] = q["code"][0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(b"<h2>Login berhasil, kembali ke Termux.</h2>")
        def log_message(self, *a):
            pass

    srv = HTTPServer(("127.0.0.1", PORT), H)
    t = threading.Thread(target=srv.handle_request, daemon=True)
    t.start()

    params = {"client_id": client_id, "redirect_uri": REDIRECT,
              "response_type": "code", "scope": "openid email profile",
              "access_type": "offline", "prompt": "select_account"}
    url = GOOGLE_AUTH + "?" + urllib.parse.urlencode(params)
    print("Membuka browser untuk login Google ...")
    try:
        import subprocess
        subprocess.run(["termux-open-url", url], timeout=10)
    except Exception:
        webbrowser.open(url)
    print("Jika browser tidak terbuka, buka manual URL ini:")
    print(url)
    t.join(timeout=180)
    srv.server_close()
    if "code" not in code_box:
        logger.error("Timeout / tidak ada code dari Google."); return None
    tok = _post(GOOGLE_TOKEN, {"code": code_box["code"], "client_id": client_id,
                               "redirect_uri": REDIRECT, "grant_type": "authorization_code"})
    id_token = tok.get("id_token")
    if not id_token:
        logger.error(f"Gagal tukar code: {tok}"); return None
    logger.info("ID token Google didapat otomatis.")
    return id_token

if __name__ == "__main__":
    print(get_id_token() or "GAGAL")
