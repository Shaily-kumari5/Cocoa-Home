#!/usr/bin/env bash
# Launcher install karta hai aur Amazon ke home screen (ads wala) ko band karta hai.
# Usage: ./scripts/install_launcher.sh <TV_IP> <path/to/app-debug.apk>
set -eu
IP="${1:?TV ka IP do}"
APK="${2:?APK ka path do}"
adb connect "$IP:5555" >/dev/null
ADB="adb -s $IP:5555"

echo "[1/3] Installing launcher..."
$ADB install -r "$APK"

echo "[2/3] Setting as default home..."
$ADB shell cmd package set-home-activity com.cocoa.launcher/.MainActivity || true

echo "[3/3] Disabling Amazon home screen..."
$ADB shell pm disable-user --user 0 com.amazon.tv.launcher

echo
echo "Ho gaya. Remote ka Home button dabao."
echo "Agar kuch gadbad ho: ./scripts/restore_home.sh $IP"
