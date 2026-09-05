#!/usr/bin/env bash
# Installer Melolo Auto Reward (Termux)
set -e
pkg update -y && pkg install -y python termux-api termux-tools
pip install --upgrade pip
echo "[1/3] Cek Termux:API..."
termux-battery-status >/dev/null 2>&1 && echo "Termux:API OK" || echo "WARNING: install aplikasi Termux:API dari F-Droid/Play Store!"
mkdir -p ~/.melolo-helper/logs
DIR="$(cd "$(dirname "$0")/.." && pwd)"
chmod +x "$DIR/termux/melolo-helper"
ln -sf "$DIR/termux/melolo-helper" "$PREFIX/bin/melolo-helper"
echo "[2/3] Symlink: $PREFIX/bin/melolo-helper"
echo "[3/3] Selesai. Jalankan: melolo-helper setup && melolo-helper login --user EMAIL --pass PASS && melolo-helper auto"
