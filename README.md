# Cocoa Home – Apple TV style launcher for Fire TV Stick

Fire TV Stick HD (Fire OS) ke liye custom launcher + debloat scripts.

## Kya milta hai
- Dark gradient home (din ke time ke hisaab se rang badlta hai), bada clock + greeting
- Apps ki ek row: focus par zoom + white glow + bada app naam (tvOS jaisa)
- Netflix / Prime / YouTube / Hotstar etc. pehle, baaki A-Z
- Kisi bhi app par OK long-press = hide. Settings tile par long-press = hidden apps wapas
- Koi ads, koi sponsored rows, koi internet permission nahi

## Step 1: APK banao (GitHub Actions, free)
1. GitHub par naya repo banao aur is poore folder ko push karo (branch `main`).
2. Repo > Actions > "Build APK" chalne do (3-5 min).
3. Run kholo > Artifacts > `CocoaLauncher-debug-apk` download karo, zip se `app-debug.apk` nikaalo.

## Step 2: TV se connect karo
1. TV: Settings > My Fire TV > Developer Options > ADB debugging ON, Install unknown apps ON.
2. TV ka IP: Settings > My Fire TV > About > Network.
3. PC par `adb` chahiye (Android platform-tools). Phir:
   `adb connect <TV_IP>:5555`  (TV par "Allow" dabao)

## Step 3: Bloat hatao
```
./scripts/debloat.sh <TV_IP>
```
Ye sirf disable karta hai, kuch uninstall nahi. Wapas: `adb shell pm enable <package>`.

## Step 4: Launcher lagao
```
./scripts/install_launcher.sh <TV_IP> app-debug.apk
```
Isse Amazon ka home screen (jahan ads aate hain) band ho jata hai aur Home button apne launcher par aata hai.

Agar kuch bigde: `./scripts/restore_home.sh <TV_IP>`.

## Dhyan rakhne wali baatein
- Alexa voice button Amazon launcher se jud kar kaam karta hai; home disable karne ke baad kuch voice features ya recommendations kaam na karein to normal hai.
- Fire OS update ke baad Amazon launcher kabhi kabhi wapas enable ho jata hai. Bas `install_launcher.sh` dobara chalao.
- Amazon ke ads ke privacy toggles bhi band kar do: Settings > Preferences > Privacy Settings (Interest-based Ads, Collect App Usage OFF).
- Ye Fire OS wale sticks ke liye hai. Vega OS wale (4K Select) par kaam nahi karega.

## Windows par
Scripts bash ke hain. Git Bash ya WSL me chalao, ya README ke commands ek-ek karke PowerShell me paste karo.
