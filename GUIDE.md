# Sangha Home: Build Log and Recovery Guide

Last updated: 23 October 2026

---

## 1. Overview

**What it is:** Sangha Home is a custom, ad-free home screen (launcher) for the Nvidia Shield TV Pro. It replaces Google's stock Android TV home screen and its sponsored content.

**Setup:**
- **Streaming box:** Nvidia Shield TV Pro (Android TV)
- **Shield IP address:** 192.168.1.197 (can change if the router reassigns it; check Settings > Network & Internet)
- **TV:** Samsung (Tizen), used only as a screen for the Shield
- **Remote:** Samsung TV remote, controlling the Shield over HDMI-CEC (Anynet+). Only arrows, OK and Back are reliable.
- **Computer:** MacBook Air, with Google's ADB tools in `~/Downloads/platform-tools`
- **Code:** GitHub repository `Jas-Sangha91/shield-home` (public)
- **App package name:** `com.jas.shieldhome` (shows on the Shield as "Sangha Home")

**How it works:** the code lives on GitHub. Every time a file is changed, GitHub builds the app automatically and publishes it as a numbered release. The Shield downloads and installs it using the Downloader app.

**Status (23 Oct 2026):**
- Debloat Batch 1 done
- Sangha Home installed, but NOT yet the default home screen (Google's stock home screen is still enabled as a safety net)
- Latest code: tabs, VPN button, settings cog, Watch/Play/Tools groups
- Discover (TMDB) not built yet

---

## 2. Quick recovery: if something breaks

### A. A new update is broken -> install an older build
Every build is kept on GitHub. Go to the repository > **Releases** (right-hand side) to see the build numbers. In Downloader on the Shield, install an older one:

```
https://github.com/Jas-Sangha91/shield-home/releases/download/vNUMBER/sangha-home.apk
```

Builds made **after** the signing-key fix install straight over the top. Builds from **before** the fix have a different key, so you'd need to uninstall Sangha Home first (this loses your layout).

### B. Undo a code change on GitHub
1. Open the file on GitHub and click **History** (top right of the file).
2. Click an earlier commit and view the file as it was then.
3. Copy that version, open the current file, click the pencil, replace everything, and commit.
4. GitHub builds a new release from it. Install that build number.

### C. Sangha Home won't open or keeps crashing
- **Before the "make default" step:** just press Home. Google's stock home screen is still there.
- **After the "make default" step:** connect from the Mac (section 3) and bring the stock home screen back:

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb shell pm enable com.google.android.tvlauncher
./adb shell pm enable com.google.android.tvrecommendations
./adb shell pm enable com.google.android.tungsten.setupwraith
```

### D. Undo the debloat (Batch 1)
```
for p in com.nvidia.stats com.nvidia.feedback com.google.android.feedback com.nvidia.shieldbeta com.nvidia.shield.beta com.nvidia.shield.registration com.nvidia.shield.welcome com.nvidia.developerwidget com.android.printspooler; do ./adb shell pm enable $p; done
```

### E. Last resort
Factory reset the Shield: Settings > Device Preferences > Reset. Everything returns to how it came out of the box.

---

## 3. Connecting to the Shield with ADB (from the Mac)

### On the Shield (one time)
1. Settings > Device Preferences > About > press **Build** about 7 times to unlock Developer options.
2. Settings > Device Preferences > Developer options > switch on **Network debugging**. The IP address shows underneath.

### On the Mac
```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb devices
```
- A prompt appears on the TV: tick **Always allow from this computer** and press OK.
- `adb devices` should show the IP followed by `device`.

### Connection quirks
- `Connection refused`: Network debugging is off, the Shield was asleep, or the IP changed. Try again.
- `failed to authenticate`: accept the prompt on the TV, then run connect again.
- `zsh: no such file or directory: ./adb`: you're not in the platform-tools folder. Run the `cd` line first.
- macOS blocks adb ("developer cannot be verified"): run `xattr -d com.apple.quarantine adb` in the folder, or allow it in System Settings > Privacy & Security.

### Security
Turn **Network debugging off** when finished. Turn it back on only when you need it.

---

## 4. Debloat

### Batch 1: disabled on 23 Oct 2026
Telemetry, feedback, beta and setup apps. None affect daily use.

| Package | What it was |
|---|---|
| com.nvidia.stats | Nvidia usage stats |
| com.nvidia.feedback | Nvidia feedback |
| com.google.android.feedback | Google feedback |
| com.nvidia.shieldbeta | Beta program |
| com.nvidia.shield.beta | Beta program |
| com.nvidia.shield.registration | Product registration |
| com.nvidia.shield.welcome | Welcome tour |
| com.nvidia.developerwidget | Developer widget |
| com.android.printspooler | Printing |

Command used:
```
./adb shell pm list packages -d > ~/Desktop/shield-disabled-before.txt
for p in com.nvidia.stats com.nvidia.feedback com.google.android.feedback com.nvidia.shieldbeta com.nvidia.shield.beta com.nvidia.shield.registration com.nvidia.shield.welcome com.nvidia.developerwidget com.android.printspooler; do ./adb shell pm disable-user --user 0 $p; done
```
A snapshot of what was disabled beforehand is saved on the Mac desktop: `shield-disabled-before.txt`. The full package list is in `shield-packages.txt`.

### Never disable these
- **System updates:** com.nvidia.ota, com.nvidia.KeyComboOta, com.nvidia.NvCPLUpdater
- **Google services:** com.google.android.gms, com.google.android.gsf, com.android.vending, com.google.android.webview
- **Remote and Bluetooth:** com.nvidia.blakepairing, com.nvidia.bluetooth.ShieldBluetoothManager, com.nvidia.irtuner, com.nvidia.remotelocator
- **Audio:** com.nvidia.nvaudiosvc, com.dolby.android.audio.service, com.nvidia.avsync
- **Chromecast:** com.google.android.apps.mediashell
- **Voice search:** com.google.android.katniss
- **Phone remote:** com.google.android.tv.remote.service
- **Keyboards:** com.google.android.leanback.ime, com.google.android.inputmethod.latin
- **Kept by choice:** com.nvidia.tegrazone3 (Nvidia Games / GeForce NOW), com.google.android.play.games, plus Hey Google hotword packages, Ambient mode (backdrop), TalkBack, iPlayer, Prime Video, gallery, TCL browser

### Disable later (only once Sangha Home is the default)
- com.google.android.tvlauncher: stock home screen
- com.google.android.tvrecommendations: the ad and recommendations feed
- com.google.android.tungsten.setupwraith: known to hijack the Home button

---

## 5. GitHub repository

**Repository:** https://github.com/Jas-Sangha91/shield-home

| File | Purpose |
|---|---|
| `settings.gradle` | Project setup |
| `build.gradle` | Android build tools version |
| `app/build.gradle` | App settings, including the permanent signing key |
| `app/src/main/AndroidManifest.xml` | App name (Sangha Home), banner, permissions (internet, network state), registers it as a home screen |
| `app/src/main/java/com/jas/shieldhome/MainActivity.java` | The whole home screen: layout, dock, groups, weather, VPN, settings, arranging |
| `app/src/main/res/drawable/banner.xml` | App banner (TV screen with a house) |
| `app/src/main/res/drawable/ic_cog.xml` | Settings cog icon |
| `app/src/main/res/drawable/ic_shield.xml` | VPN shield icon |
| `.github/workflows/build.yml` | Tells GitHub how to build and publish the app |
| `app/sangha.keystore` | Permanent signing key. Created automatically. **Never delete.** |
| `GUIDE.md` | This guide |

**Secrets** (Settings > Secrets and variables > Actions):
- `TMDB_TOKEN`: TMDB read access token for Discover (pending)

**How a build works:**
1. Any commit starts the "Build APK" workflow (Actions tab).
2. About 1 minute later: green tick = success, red cross = failed.
3. A release called "Sangha Home build N" appears with `sangha-home.apk`.

**Editing a file on GitHub:** open the file > pencil icon > Cmd+A > delete > paste > Commit changes (twice).
**New file:** Add file > Create new file > type the full path (slashes make folders) > paste > commit.

**Public vs private:** the repository must be **public** for Downloader to fetch builds. When you're done building, you can switch it to private (Settings > Danger Zone > Change visibility). The installed app keeps working; only the download link stops.

---

## 6. Signing key

- Android only lets an update install over an existing app if both are signed with the same key.
- Early builds used a random key each time, which caused "App not installed" and forced uninstalls.
- Fix: the build workflow created one permanent key, `app/sangha.keystore` (password `sanghahome`, alias `sangha`, set in `app/build.gradle`). All builds since use it.
- **Back it up:** open `app/sangha.keystore` on GitHub > Download raw file, and keep it somewhere safe. If it's ever lost, the next build gets a new key and you'd have to uninstall once (losing your layout).

---

## 7. Installing and updating on the Shield

**One-time setup:** Settings > Device Preferences > Security & restrictions > Unknown sources > turn on **Downloader**.

**Each update:**
1. Wait for the green tick in Actions and note the build number (e.g. "Build APK #12").
2. In Downloader, enter:
```
https://github.com/Jas-Sangha91/shield-home/releases/download/vNUMBER/sangha-home.apk
```
3. Install > Open.

**Tips:**
- Always use the specific build number (`v12`) rather than "latest", to avoid cached downloads.
- Play Protect warning: choose More details > Install anyway (it's your own app).
- "App not installed" = signing key mismatch. See section 13.

---

## 8. Version history

Build numbers in GitHub don't match these labels one to one (every commit makes a build). Check Releases for the exact numbers.

| Version | What changed |
|---|---|
| **1** | First home screen: dock of 5 apps, all-apps grid, clock and date, grow-and-ring focus effect |
| **2** | Weather (Open-Meteo), arrange mode (move, dock, hide), Customise tile, bigger dock, 6-across grid |
| **Rename** | Named "Sangha Home", custom banner icon, download renamed `sangha-home.apk` |
| **3** | Fixed header that doesn't scroll, permanent signing key, dock shrinks to fit, default dock matched to old favourites, weather credit moved into the weather dialog |
| **4** | Colour glow from the selected app, time-of-day background, 5-across bigger grid, larger text, idle dimming after 20 seconds, VPN status with location, weather and VPN moved top-left, large light clock top-right |
| **5** | Watch / Play / Tools groups, Tools folder popup, "Move to group" option |
| **6 (latest)** | Home and Discover tabs (Discover placeholder), VPN button with green/amber status dot that opens Surfshark, smaller settings cog menu (Arrange apps, Weather location, Shield settings), Settings and Customise tiles removed, default dock of 5 |

---

## 9. Using Sangha Home (Samsung remote)

- **Arrows** move around; **OK** opens; **Back** leaves arrange mode or returns from Discover to Home.
- **Samsung's Home button** goes to Samsung's own screen, not the Shield's. Use Back to get home from an app.
- **Top bar:** press up from the dock to reach Home, Discover, the VPN button and the cog.
- **VPN button:** green dot = connected, amber = off. Moving onto it shows the location in the big title. OK opens Surfshark.
- **Cog menu:**
  - **Arrange apps:** press OK on any app for Move, Move to group, Add/Remove from dock, Hide/Unhide. In Move mode, use the arrows, then OK to drop or Back to cancel.
  - **Weather location:** type a town or city and pick from the list.
  - **Shield settings:** the normal Android settings.
- **Tools tile:** opens a popup with your utility apps.
- **Hidden apps:** appear dimmed at the very bottom, so they're never lost.
- **Idle dimming:** after 20 seconds without input, everything except the selected app dims. Any button brings it back.

---

## 10. Design decisions

- **Clean, ad-free, apps first:** no content rows on Home. Recommendations live separately in Discover.
- **Dock of 5:** with 6 or more, dock tiles shrink below grid size, which defeats the point of a dock.
- **Fixed header:** clock, weather and status never scroll or overlap the apps.
- **Clock top-right, large and light:** the strongest convention for where people look for the time. Light weight so it doesn't compete with the bold app name.
- **Weather top-left; VPN as a button with a status dot:** avoids crowding one corner; no duplicated information.
- **Bold app name above the dock:** tells you what's selected, Apple TV style.
- **Colour glow and time-of-day background:** adds life without clutter; darker at night.
- **Groups (Watch / Play / Tools):** a short, meaningful screen instead of one long alphabetical list.
- **Cog instead of tiles:** settings are occasional, so they don't belong among the apps.
- **No hold-to-edit:** holding OK isn't reliable over HDMI-CEC, so everything works with arrows, OK and Back.
- **Tabs kept to two (Home, Discover):** more tabs would add steps for no gain.
- **Discover shows all films and series**, not filtered by country or apps.

---

## 11. Samsung TV setup checklist

- [ ] Turn off **Viewing Information Services** (ACR tracking)
- [ ] Turn off **Interest-Based Advertising**
- [ ] Disconnect the TV from the internet (forget Wi-Fi, unplug Ethernet). Reconnect occasionally for firmware updates.
- [ ] Turn off Smart Hub auto-start, so the TV goes straight to the Shield input
- [ ] Turn on **Anynet+ (HDMI-CEC)** on the TV, and HDMI-CEC on the Shield

---

## 12. Next steps

1. **Install the latest build** and check groups, VPN button and cog work.
2. **Tidy up on the TV:** trim the dock to 5 (remove Kodi), hide unused utilities, set the weather to your city.
3. **Uninstall Plex** (not used).
4. **Make Sangha Home the default** home screen: disable tvlauncher, tvrecommendations and setupwraith via ADB, with the restore commands in section 2C ready.
5. **Turn off Network debugging.**
6. **Build Discover (TMDB):**
   - Create a free TMDB account > Settings > API > Request an API Key (Developer, personal use) > copy the **API Read Access Token** (starts with `eyJ`).
   - Save it as the GitHub secret `TMDB_TOKEN`.
   - Planned rows: Trending this week, Popular films, Popular series, Top rated films, Top rated series, Coming soon.
   - Info card: backdrop, title, year, rating, genres, runtime/seasons, description, cast, **Watch trailer** (opens YouTube).
   - TMDB credit line at the bottom of the Discover page.
7. **Optional later:** rotating photo wallpapers, genre rows in Discover.
8. **When finished:** switch the repository to private.

---

## 13. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| Red cross in Actions | Code or file error | Open the run > failed step > copy the error and ask for help. Check file names and paths are exact. |
| "App not installed" | Signing key mismatch | Check `app/sangha.keystore` exists. Otherwise uninstall Sangha Home (loses layout) and reinstall. |
| Installed but nothing changed | Old file downloaded | Use the exact build number (`vNUMBER`); delete old files in Downloader's Files section. |
| Home button goes to Google's screen | Not yet set as default | Expected until step 12.4. |
| Weather blank | No location set, or no internet | Cog > Weather location. |
| VPN shows off while connected | Surfshark split tunnelling bypasses Sangha Home | Make sure Sangha Home isn't excluded in Surfshark's Bypasser. |
| Can't connect with ADB | Network debugging off, IP changed, prompt not accepted | See section 3. |
| An app is in the wrong group | Auto-sorting guessed wrong | Cog > Arrange apps > the app > Move to group. |

---

## 14. Data sources, credits and privacy

- **Weather:** Open-Meteo (free, no key). Credit shown in the Weather location dialog.
- **VPN location:** ipwho.is (free, no key). It sees your public IP address (Surfshark's when the VPN is on).
- **Discover (planned):** TMDB. Free for non-commercial use; requires a credit. The token is stored as a GitHub secret, but it's embedded in the public app file, so it could in theory be extracted. It's read-only and can be regenerated in TMDB settings.
- **Public repository** includes the signing key file. Low risk for a personal app; another reason to go private when finished.
