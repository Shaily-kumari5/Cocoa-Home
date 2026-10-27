#!/usr/bin/env bash
# Amazon ka original home screen wapas laata hai.
# Usage: ./scripts/restore_home.sh <TV_IP>
set -eu
IP="${1:?TV ka IP do}"
adb connect "$IP:5555" >/dev/null
adb -s "$IP:5555" shell pm enable com.amazon.tv.launcher
echo "Amazon launcher wapas enable ho gaya. Home button dabao."
