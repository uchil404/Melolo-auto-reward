#!/usr/bin/env python3
"""
Logging module for Melolo Reward Helper (Termux side).

Writes structured logs to ~/.melolo-helper/logs/ with rotation.
Each log line has the format: YYYY-MM-DD HH:MM:SS | LEVEL | message
"""

import os
import sys
import time
import json
from datetime import datetime
from pathlib import Path

LOG_DIR = Path.home() / ".melolo-helper" / "logs"
LOG_FILE = LOG_DIR / "melolo-helper.log"
MAX_LOG_SIZE = 5 * 1024 * 1024  # 5 MB
MAX_LOG_FILES = 10
LOG_FORMAT = "{timestamp} | {level} | {message}"

LEVELS = {
    "INFO": "INFO",
    "WARN": "WARN",
    "ERROR": "ERROR",
    "DEBUG": "DEBUG",
    "STATE": "STATE",
    "CLAIM": "CLAIM",
    "SAFETY": "SAFETY",
    "START": "START",
    "STOP": "STOP",
}


def ensure_log_dir():
    """Create log directory if it doesn't exist."""
    LOG_DIR.mkdir(parents=True, exist_ok=True)


def rotate_logs():
    """Rotate log files if the main log exceeds MAX_LOG_SIZE."""
    if not LOG_FILE.exists():
        return
    if LOG_FILE.stat().st_size < MAX_LOG_SIZE:
        return

    for i in range(MAX_LOG_FILES - 1, 0, -1):
        old = LOG_DIR / f"melolo-helper.log.{i}"
        new = LOG_DIR / f"melolo-helper.log.{i + 1}"
        if old.exists():
            if new.exists():
                new.unlink()
            old.rename(new)

    backup = LOG_DIR / "melolo-helper.log.1"
    if backup.exists():
        backup.unlink()
    LOG_FILE.rename(backup)


def log(level: str, message: str):
    """Write a log entry to the log file and stdout."""
    ensure_log_dir()
    rotate_logs()

    level = LEVELS.get(level.upper(), level.upper())
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = LOG_FORMAT.format(timestamp=timestamp, level=level, message=message)

    try:
        with open(LOG_FILE, "a") as f:
            f.write(line + "\n")
    except Exception as e:
        print(f"Logger error: {e}", file=sys.stderr)

    # Also print to stdout for CLI visibility
    if level in ("ERROR", "SAFETY", "WARN"):
        print(line, file=sys.stderr)
    else:
        print(line)


def info(message: str):
    log("INFO", message)


def warn(message: str):
    log("WARN", message)


def error(message: str):
    log("ERROR", message)


def debug(message: str):
    log("DEBUG", message)


def state(message: str):
    log("STATE", message)


def claim(message: str):
    log("CLAIM", message)


def safety(message: str):
    log("SAFETY", message)


def read_logs(lines: int = 50, level_filter: str = None) -> str:
    """Read the last N lines from the log file."""
    if not LOG_FILE.exists():
        return "No logs found."

    try:
        with open(LOG_FILE, "r") as f:
            all_lines = f.readlines()

        if level_filter:
            all_lines = [l for l in all_lines if f"| {level_filter} |" in l]

        recent = all_lines[-lines:]
        return "".join(recent)
    except Exception as e:
        return f"Error reading logs: {e}"


def get_log_status() -> dict:
    """Get log file statistics."""
    if not LOG_FILE.exists():
        return {"exists": False, "size_bytes": 0, "line_count": 0}

    try:
        size = LOG_FILE.stat().st_size
        with open(LOG_FILE, "r") as f:
            line_count = sum(1 for _ in f)
        return {
            "exists": True,
            "size_bytes": size,
            "size_kb": round(size / 1024, 1),
            "line_count": line_count,
            "path": str(LOG_FILE),
        }
    except Exception as e:
        return {"exists": True, "error": str(e)}