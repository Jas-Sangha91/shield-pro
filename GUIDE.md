# Sangha Home: Build Log and Recovery Guide

Last updated: 23 October 2026

---

## 1. Overview

**What it is:** Sangha Home is a custom, ad-free home screen (launcher) for the Nvidia Shield TV Pro. It replaces Google's stock Android TV home screen and its sponsored content, and adds a Discover page for finding something to watch.

**Setup:**
- **Streaming box:** Nvidia Shield TV Pro (Android TV)
- **Shield IP address:** 192.168.1.197 (can change if the router reassigns it; check Settings > Network & Internet)
- **TV:** Samsung (Tizen), used only as a screen for the Shield
- **Remote:** Samsung TV remote, controlling the Shield over HDMI-CEC (Anynet+). Only arrows, OK and Back are reliable. Samsung's Home button goes to Samsung's own screen.
- **Computer:** MacBook Air, with Google's ADB tools in `~/Downloads/platform-tools`
- **Code:** GitHub repository `Jas-Sangha91/shield-pro` (public; renamed from `shield-home`)
- **App package name:** `com.jas.shieldhome` (shows on the Shield as "Sangha Home"). This internal name never changes, even though the repository was renamed.

**How it works:** the code lives on GitHub. Every time a file is changed, GitHub builds the app automatically and publishes it as a numbered release. The Shield downloads and installs it using the Downloader app.

**Status (23 Oct 2026):**
- Debloat Batch 1 done
- Sangha Home installed, but NOT yet the default home screen (Google's stock home screen is still enabled as a safety net)
- TMDB account created and `TMDB_TOKEN` secret saved on GitHub
- Latest code: Discover (spotlight, My list, Surprise me, curated rows, info card, trailers) and Search, plus Open Google home screen and How to restore in the cog menu
- Still to do: test Discover and Search on the TV, then make Sangha Home the default

---

## 2. Quick recovery: if something breaks

### A. A new update is broken -> install an older build
Every build is kept on GitHub. Go to the repository > **Releases** (right-hand side) to see the build numbers. In Downloader on the Shield, install an older one:

```
https://github.com/Jas-Sangha91/shield-pro/releases/download/vNUMBER/sangha-home.apk
```

Builds made **after** the signing-key fix install straight over the top. Builds from **before** the fix have a different key, so you'd need to uninstall Sangha Home first (this loses your layout).

### B. Undo a code change on GitHub
1. Open the file on GitHub and click **History** (top right of the file).
2. Click an earlier commit and view the file as it was then.
3. Copy that version, open the current file, click the pencil, replace everything, and commit.
4. GitHub builds a new release from it. Install that build number.

### C. Go back to Google's original home screen
- **Before the "make default" step (current state):** press Home on the Nvidia remote, or use Sangha Home's **cog > Open Google home screen**. To remove Sangha Home: Settings > Apps > See all apps > Sangha Home > Uninstall.
- **After the "make default" step:** connect from the Mac (section 3) and switch Google's home screen back on:

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb shell pm enable com.google.android.tvlauncher
./adb shell pm enable com.google.android.tvrecommendations
./adb shell pm enable com.google.android.tungsten.setupwraith
```

Then press Home, choose Google's home screen, and select **Always**.

**Important:** always switch Google's home screen back on **before** uninstalling Sangha Home. Otherwise the Shield has no home screen until you run the commands above.

These steps are also on the TV: **cog > How to restore**.

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
- **Screensaver:** com.google.android.backdrop (Ambient Mode)
- **Kept by choice:** com.nvidia.tegrazone3 (Nvidia Games / GeForce NOW), com.google.android.play.games, plus Hey Google hotword packages, TalkBack, iPlayer, Prime Video, gallery, TCL browser

### Disable later (only once Sangha Home is the default)
- com.google.android.tvlauncher: stock home screen
- com.google.android.tvrecommendations: the ad and recommendations feed
- com.google.android.tungsten.setupwraith: known to hijack the Home button

---

## 5. GitHub repository

**Repository:** https://github.com/Jas-Sangha91/shield-pro

| File | Purpose |
|---|---|
| `settings.gradle` | Project setup |
| `build.gradle` | Android build tools version |
| `app/build.gradle` | App settings, the permanent signing key, and passing the TMDB token into the app |
| `app/src/main/AndroidManifest.xml` | App name (Sangha Home), banner, permissions (internet, network state), registers it as a home screen |
| `app/src/main/java/com/jas/shieldhome/MainActivity.java` | The whole app: home screen, dock, groups, weather, VPN, settings, arranging, Discover, Search |
| `app/src/main/res/drawable/banner.xml` | App banner (TV screen with a house) |
| `app/src/main/res/drawable/ic_cog.xml` | Settings cog icon |
| `app/src/main/res/drawable/ic_shield.xml` | VPN shield icon |
| `app/src/main/res/drawable/ic_search.xml` | Search icon |
| `.github/workflows/build.yml` | Tells GitHub how to build and publish the app, and passes in the TMDB secret |
| `app/sangha.keystore` | Permanent signing key. Created automatically. **Never delete.** |
| `GUIDE.md` | This guide |

**Secrets** (Settings > Secrets and variables > Actions):
- `TMDB_TOKEN`: TMDB **API Read Access Token** (the long one starting `eyJ`), used by Discover and Search

**How a build works:**
1. Any commit starts the "Build APK" workflow (Actions tab).
2. About 1 minute later: green tick = success, red cross = failed.
3. A release called "Sangha Home build N" appears with `sangha-home.apk`.

**Order matters when adding icons:** if `MainActivity.java` uses a new icon, add the icon file first. Otherwise that build fails (harmless; the next one works).

**Editing a file on GitHub:** open the file > pencil icon > Cmd+A > delete > paste > Commit changes (twice).
**New file:** Add file > Create new file > type the full path (slashes make folders) > paste > commit.

**Public vs private:** the repository must be **public** for Downloader to fetch builds. When you're done building, you can switch it to private (Settings > Danger Zone > Change visibility). The installed app keeps working; only the download link stops. Flip it to public briefly whenever you want to install an update.

---

## 6. Signing key

- Android only lets an update install over an existing app if both are signed with the same key.
- Early builds used a random key each time, which caused "App not installed" and forced uninstalls.
- Fix: the build workflow created one permanent key, `app/sangha.keystore` (password `sanghahome`, alias `sangha`, set in `app/build.gradle`). All builds since use it.
- **Back it up:** open `app/sangha.keystore` on GitHub > Download raw file, and keep it somewhere safe. If it's ever lost, the next build gets a new key and you'd have to uninstall once (losing your layout and My list).

---

## 7. Installing and updating on the Shield

**One-time setup:** Settings > Device Preferences > Security & restrictions > Unknown sources > turn on **Downloader**.

**Each update:**
1. Wait for the green tick in Actions and note the build number (e.g. "Build APK #15").
2. In Downloader, enter:
```
https://github.com/Jas-Sangha91/shield-pro/releases/download/vNUMBER/sangha-home.apk
```
3. Install > Open.

**Tips:**
- Always use the specific build number (`v15`) rather than "latest", to avoid cached downloads.
- Play Protect warning: choose More details > Install anyway (it's your own app).
- "App not installed" = signing key mismatch. See section 14.

---

## 8. TMDB setup (Discover and Search)

1. Account at themoviedb.org (done).
2. Settings > API > Request an API Key > **Developer**, personal use (done). Application URL: the GitHub repository.
3. Copy the **API Read Access Token** (long, starts with `eyJ`), not the shorter API Key.
4. Saved on GitHub as the secret `TMDB_TOKEN` (done).
5. `build.yml` passes it into the build, and `app/build.gradle` puts it into the app privately.

**If Discover says "Discover needs a TMDB key":** the secret didn't reach the build. Check the secret name is exactly `TMDB_TOKEN`, and that `build.yml` and `app/build.gradle` contain the TMDB lines.

**Regenerating the token** (if you think it's leaked or it stops working): TMDB Settings > API > regenerate, then update the `TMDB_TOKEN` secret on GitHub and build again. The old token stops working immediately.

---

## 9. Version history

Build numbers in GitHub don't match these labels one to one (every commit makes a build). Check Releases for the exact numbers.

| Version | What changed |
|---|---|
| **1** | First home screen: dock of 5 apps, all-apps grid, clock and date, grow-and-ring focus effect |
| **2** | Weather (Open-Meteo), arrange mode (move, dock, hide), Customise tile, bigger dock, 6-across grid |
| **Rename** | Named "Sangha Home", custom banner icon, download renamed `sangha-home.apk` |
| **3** | Fixed header that doesn't scroll, permanent signing key, dock shrinks to fit, default dock matched to old favourites, weather credit moved into the weather dialog |
| **4** | Colour glow from the selected app, time-of-day background, 5-across bigger grid, larger text, idle dimming after 20 seconds, weather moved top-left, large light clock top-right |
| **5** | Watch / Play / Tools groups, Tools folder popup, "Move to group" option |
| **6** | Home and Discover tabs, VPN button with green/amber status dot that opens Surfshark, small settings cog menu (Arrange apps, Weather location, Shield settings), Settings and Customise tiles removed, default dock of 5 |
| **7** | Discover: "Tonight's pick" spotlight (5 rotating picks), Surprise me, My list, curated rows with no repeats (Trending, New and highly rated, Binge-worthy series, Tonight's mood, Coming soon), info card with age rating and More like this, trailers in YouTube |
| **8 (latest)** | Search button before the tabs (apps and every film and series, with recent searches), cog > Open Google home screen, cog > How to restore. Repository renamed to `shield-pro` |

---

## 10. Using Sangha Home (Samsung remote)

**General**
- **Arrows** move around; **OK** opens; **Back** closes popups, leaves arrange mode, or returns from Discover to Home.
- **Samsung's Home button** goes to Samsung's own screen, not the Shield's. Use Back to get home from an app.
- **Top bar:** press up from the apps to reach it. Left to right: Search, Home, Discover ... VPN button, cog, clock.
- **Idle dimming:** after 20 seconds without input, everything except the selected item dims. Any button brings it back.

**Home**
- **Dock:** your favourite apps (best with 5).
- **Watch / Play / Tools:** apps grouped automatically. **Tools** is a folder; press it to open the popup.
- **Hidden apps:** dimmed at the very bottom, never lost.

**Top bar buttons**
- **Search:** type to find apps (instantly) and films and series (after a short pause). Press Back once to hide the keyboard, then browse with the arrows; Back again to close. Recent searches show when the box is empty.
- **VPN button:** green dot = connected, amber = off. Moving onto it shows the location in the big title. OK opens Surfshark.
- **Cog menu:**
  - **Arrange apps:** press OK on any app for Move, Move to group, Add/Remove from dock, Hide/Unhide. In Move mode, use the arrows, then OK to drop or Back to cancel. Back when finished.
  - **Weather location:** type a town or city and pick from the list.
  - **Shield settings:** the normal Android settings.
  - **Open Google home screen:** jumps to the original home screen (shows restore steps if it's switched off).
  - **How to restore:** the restore steps and a link to this guide.

**Discover**
- **Tonight's pick:** five trending titles rotating every 8 seconds; stops rotating while you're on its buttons.
  - **Trailer:** opens in YouTube; press Back to return.
  - **+ My list:** saves it to the My list row (press again to remove).
  - **Surprise me:** picks a random highly rated film or series.
- **Rows:** My list (once you've saved something), Trending this week, New and highly rated, Binge-worthy series, Tonight's mood (genre changes daily: Mon comedy, Tue thriller, Wed sci-fi, Thu crime, Fri action, Sat family, Sun drama), Coming soon. 12 titles per row, no repeats.
- **Info card:** OK on any poster. Shows rating, year, age rating, genres, length, description, cast, Trailer, My list, and **More like this** (press down).
- Lists refresh every few hours.

---

## 11. Design decisions

- **Clean, ad-free, apps first:** no content rows on Home. Recommendations live separately in Discover.
- **Dock of 5:** with 6 or more, dock tiles shrink below grid size, which defeats the point of a dock.
- **Fixed header:** clock, weather and status never scroll or overlap the apps.
- **Clock top-right, large and light:** the strongest convention for where people look for the time. Light weight so it doesn't compete with the bold title.
- **Weather top-left; VPN as a button with a status dot:** avoids crowding one corner; no duplicated information.
- **Small round buttons (Search, VPN, cog):** consistent style, quiet until selected, then white.
- **Bold title above the dock:** tells you what's selected, Apple TV style.
- **Colour glow and time-of-day background:** adds life without clutter; darker at night.
- **Groups (Watch / Play / Tools):** a short, meaningful screen instead of one long alphabetical list.
- **No hold-to-edit:** holding OK isn't reliable over HDMI-CEC, so everything works with arrows, OK and Back.
- **Two tabs only (Home, Discover):** more tabs would add steps for no gain.
- **Discover built around decision fatigue:** research shows people spend 10+ minutes choosing and 1 in 5 give up, so Discover uses one clear spotlight pick, Surprise me, My list, fewer rows (12 each), no repeats, and no all-time classics.
- **Discover shows all films and series**, not filtered by country or apps.
- **Trailers open in YouTube** rather than autoplaying, to keep the page fast and calm.

---

## 12. Samsung TV setup checklist

- [ ] Turn off **Viewing Information Services** (ACR tracking)
- [ ] Turn off **Interest-Based Advertising**
- [ ] Disconnect the TV from the internet (forget Wi-Fi, unplug Ethernet). Reconnect occasionally for firmware updates.
- [ ] Turn off Smart Hub auto-start, so the TV goes straight to the Shield input
- [ ] Turn on **Anynet+ (HDMI-CEC)** on the TV, and HDMI-CEC on the Shield

---

## 13. Next steps

1. **Install the latest build** and test Discover and Search on the TV.
2. **Tidy up on the TV:** trim the dock to 5, hide unused utilities, set the weather to your city.
3. **Uninstall Plex** (not used).
4. **Make Sangha Home the default** home screen: disable tvlauncher, tvrecommendations and setupwraith via ADB, with the restore commands in section 2C ready.
5. **Turn off Network debugging.**
6. **Switch the repository to private** once you're done building (flip to public briefly for each update).
7. **Optional later:**
   - A Sangha Home screensaver (Discover backdrops or your own photos, with clock and weather)
   - Rotating photo wallpapers on Home
   - Genre rows in Discover
   - Voice search (needs the Nvidia remote's microphone)
   - Keep the TMDB token out of the app entirely (sent once via ADB instead)
   - Fire TV Stick 4K: possible only on Fire OS models (not "Select" or 2026 models on Vega OS); runs as an app but Amazon blocks replacing the home screen

---

## 14. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| Red cross in Actions | Code or file error, or an icon added after the code that uses it | Open the run > failed step > copy the error and ask for help. Check file names and paths are exact. |
| "App not installed" | Signing key mismatch | Check `app/sangha.keystore` exists. Otherwise uninstall Sangha Home (loses layout and My list) and reinstall. |
| Installed but nothing changed | Old file downloaded | Use the exact build number (`vNUMBER`); delete old files in Downloader's Files section. |
| Home button goes to Google's screen | Not yet set as default | Expected until step 13.4. |
| "Discover needs a TMDB key" | Secret not reaching the build | See section 8. |
| "Couldn't load Discover" | No internet, or TMDB token revoked | Check internet; try again in a minute; regenerate the token if needed. |
| Trailer says "No trailer available" | TMDB has no YouTube trailer for that title | Nothing to fix. |
| Weather blank | No location set, or no internet | Cog > Weather location. |
| VPN shows off while connected | Surfshark split tunnelling bypasses Sangha Home | Make sure Sangha Home isn't excluded in Surfshark's Bypasser. |
| Can't connect with ADB | Network debugging off, IP changed, prompt not accepted | See section 3. |
| An app is in the wrong group | Auto-sorting guessed wrong | Cog > Arrange apps > the app > Move to group. |
| Shield doesn't sleep | Another app playing in the background (e.g. Spotify, paused video) | Close it. Sangha Home doesn't keep the Shield awake. Sleep settings: Settings > Device Preferences > Power & energy. |

---

## 15. Data sources, credits and privacy

- **Weather:** Open-Meteo (free, no key). Credit shown in the Weather location dialog.
- **VPN location:** ipwho.is (free, no key). It sees your public IP address (Surfshark's when the VPN is on).
- **Discover and Search:** TMDB. Free for non-commercial use; credit shown at the bottom of Discover ("This product uses the TMDB API but is not endorsed or certified by TMDB").
- **TMDB token security:** stored as an encrypted GitHub secret and never visible in your files. It is built into the app file, which is publicly downloadable while the repository is public, so it could in theory be extracted. It's read-only (public film data only; no access to your account or personal data). Making the repository private closes this; regenerating the token fixes it if leaked.
- **Public repository** also includes the signing key file. Low risk for a personal app; another reason to go private when finished.
- **Screensaver and sleep:** controlled by the Shield, not Sangha Home. Sangha Home pauses its background tasks when the screen sleeps.
