#!/usr/bin/env bash
# Fire TV Stick bloat/ad apps ko DISABLE karta hai (uninstall nahi, isliye reversible).
# Usage: ./scripts/debloat.sh <TV_IP>      e.g. ./scripts/debloat.sh 192.168.1.50
set -u
IP="${1:?TV ka IP do, e.g. ./debloat.sh 192.168.1.50}"
adb connect "$IP:5555" >/dev/null
ADB="adb -s $IP:5555 shell"

# Sirf wahi packages jo community me safe-to-disable maane jaate hain.
# Jo package aapke Fire OS version me nahi hai wo skip ho jayega.
PKGS=(
  com.amazon.ftv.screensaver        # screensaver (ads/promos)
  com.amazon.shoptv.client          # Shop TV
  com.amazon.imdb.tv.android.app    # Freevee / IMDb TV
  com.amazon.ags.app                # Amazon Games
  com.amazon.recess                 # recommendations helper
  com.amazon.hedwig                 # notifications/promos
  com.amazon.ssmsys                 # promo/sales service
  com.amazon.tv.csapp               # customer service app
  com.amazon.ods.kindleconnect      # Kindle connect
  com.amazon.firebat                # promo/OOBE extras
  com.amazon.kor.demo               # retail demo mode
)

for p in "${PKGS[@]}"; do
  if $ADB pm list packages | tr -d '\r' | grep -q "package:$p\$"; then
    out=$($ADB pm disable-user --user 0 "$p" 2>&1 | tr -d '\r')
    echo "[disable] $p -> $out"
  else
    echo "[skip]    $p (not installed)"
  fi
done

echo
echo "Done. Wapas enable karne ke liye: adb shell pm enable <package>"
