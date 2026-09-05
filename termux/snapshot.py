#!/usr/bin/env python3
"""UI Snapshot recorder + diff + selector scoring (usulan #2, #3, #10).

Scoring (boleh di-override via config.json -> scoring.weights):
  resource-id/viewId +100 | content-desc +80 | clickable +40
  enabled +30 | visible +20 | keyword text +20 | relasi +20
  bounds +10 | coordinate fallback -50
Keputusan: >=85 klik | 70-84 verifikasi | <70 jangan klik.
"""
import json
from datetime import datetime
from pathlib import Path

SNAP_DIR = Path.home() / ".melolo-helper" / "snapshots"
DEFAULT_WEIGHTS = {
    "resource_id": 100, "view_id": 100, "content_desc": 80,
    "clickable": 40, "enabled": 30, "visible": 20,
    "keyword": 20, "relation": 20, "bounds": 10,
    "coord_fallback": -50,
}
TH = {"click": 85, "verify": 70}

def weights_from_config(config=None):
    w = dict(DEFAULT_WEIGHTS)
    if config:
        w.update((config.get("scoring") or {}).get("weights", {}))
    return w

def score_node(node, keywords=(), w=None):
    """node: dict(class,text,resource_id,content_desc,clickable,
    enabled,visible,bounds,has_relation,no_id_fallback)."""
    w = w or DEFAULT_WEIGHTS
    s = 0
    if node.get("resource_id"): s += w["resource_id"]
    if node.get("view_id"): s += w["view_id"]
    if node.get("content_desc"): s += w["content_desc"]
    if node.get("clickable"): s += w["clickable"]
    if node.get("enabled"): s += w["enabled"]
    if node.get("visible", True): s += w["visible"]
    txt = (node.get("text") or "").lower()
    if any(k.lower() in txt for k in keywords): s += w["keyword"]
    if node.get("has_relation"): s += w["relation"]
    if node.get("bounds"): s += w["bounds"]
    if node.get("no_id_fallback"): s += w["coord_fallback"]
    return s

def verdict(score):
    if score >= TH["click"]: return "CLICK"
    if score >= TH["verify"]: return "VERIFY"
    return "SKIP"

def save_snapshot(package, activity, nodes):
    SNAP_DIR.mkdir(parents=True, exist_ok=True)
    snap = {"package": package, "activity": activity,
            "timestamp": datetime.now().isoformat(), "nodes": nodes}
    p = SNAP_DIR / f"snapshot_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    p.write_text(json.dumps(snap, indent=2))
    return p

def diff_snapshots(old, new):
    """Bandingkan dua snapshot dict -> ringkasan selector berubah."""
    def key(n):
        return (n.get("resource_id") or "", n.get("text") or "",
                n.get("content_desc") or "")
    a = {key(n) for n in old.get("nodes", [])}
    b = {key(n) for n in new.get("nodes", [])}
    return {"removed": sorted(a - b), "added": sorted(b - a),
            "old_count": len(a), "new_count": len(b)}
