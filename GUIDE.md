# Sangha Home — Guide

A custom home screen for the Nvidia Shield TV Pro.
Last updated: 24 October 2026

---

## Quick reference

| Item | Value |
|---|---|
| Shield IP | `192.168.1.197:5555` |
| ADB folder on Mac | `~/Downloads/platform-tools` |
| Repository | https://github.com/Jas-Sangha91/shield-pro |
| Code folder | `app/src/main/java/com/jas/shieldhome/` |
| App package | `com.jas.shieldhome` |
| Latest APK | `https://github.com/Jas-Sangha91/shield-pro/releases/latest/download/sangha-home.apk` |
| Normal APK size | About 80 KB (a few bytes means the download failed) |
| Update command | `sangha-update` (in Terminal) |
| Signing key | `app/sangha.keystore` (password `sanghahome`, alias `sangha`) — **never delete** |
| Backup file on Shield | `/sdcard/Android/data/com.jas.shieldhome/files/sangha-home-backup.json` |

---

## 1. Updating Sangha Home

### 1A. Make a change and build

1. On GitHub, open the file, click the pencil, paste the new version and commit to `main`.
2. **Before committing a long file, scroll to the bottom** and check it ends properly (usually a single `}` on its own line). Long pastes sometimes get cut off.
3. Open the **Actions** tab and wait for the green tick.
4. When a change touches several files, the builds in between may fail (red cross) or be cancelled (grey "!"). That's expected. Only the build after the **last** file matters.

### 1B. Install from the Mac (preferred)

After the green tick, in Terminal:

```
sangha-update
```

You should see `connected to 192.168.1.197:5555`, then `Downloaded … bytes` (around 80,000), then `Success`. Your layout, folders, My list and settings are all kept through updates.

**If `sangha-update` ever goes missing** (for example on a new Mac), set it up again by pasting this into Terminal once:

```
cat >> ~/.zshrc <<'EOF'

# Sangha Home: download the latest build and install it on the Shield
sangha-update() {
  cd ~/Downloads/platform-tools || return
  ./adb connect 192.168.1.197:5555
  curl -L -s -o ~/Downloads/sangha-home.apk https://github.com/Jas-Sangha91/shield-pro/releases/latest/download/sangha-home.apk
  size=$(stat -f%z ~/Downloads/sangha-home.apk)
  if [ "$size" -lt 10000 ]; then
    echo "Download failed ($size bytes). Is the build finished?"
    return 1
  fi
  echo "Downloaded $size bytes"
  ./adb install -r ~/Downloads/sangha-home.apk
}
EOF
source ~/.zshrc
```

**The long way** (what `sangha-update` does for you):

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
curl -L -o ~/Downloads/sangha-home.apk https://github.com/Jas-Sangha91/shield-pro/releases/latest/download/sangha-home.apk
./adb install -r ~/Downloads/sangha-home.apk
```

### 1C. Install on the Shield (backup method)

Open **Downloader** on the Shield, enter the latest APK link from the quick reference, then choose Install and Open.

### 1D. If something goes wrong

| What you see | What it means and what to do |
|---|---|
| `adb: no devices/emulators found` | The Mac isn't connected. Run `./adb connect 192.168.1.197:5555` (or just use `sangha-update`, which connects for you). |
| `failed to connect` or `Connection refused` | Network debugging is off, the Shield is asleep, or its IP has changed. Wake the Shield and check Settings → Device Preferences → Developer options → Network debugging. |
| `unauthorized` | Accept the "Allow debugging?" prompt on the TV and tick **Always allow from this computer**. |
| `device offline` | Run `./adb disconnect`, then connect again. If that fails, run `./adb kill-server` and try again. |
| `Download failed (9 bytes)` | The build hasn't finished or failed. Check the Actions tab. |
| Build error `reached end of file while parsing` | A paste got cut off. Open the file named in the error, check the bottom, and re-paste it or paste the missing end. |
| Build error `cannot find symbol` | Usually a file is missing or out of date. Check every file from the last change was committed. |
| Build fails after uploading an image | Files in `res/drawable` must be named in lower case with only letters, numbers and underscores (for example `tmdb_logo.png`). SVG files aren't allowed there. |
| Any other build error | Open the failed run → the red step → copy the lines containing `error:`. They name the file and line number. |
| Most apps disappear from Home and Apps | `AndroidManifest.xml` has lost its "see all apps" lines. See section 8. Don't back up until it's fixed. |
| An app tile shows a code name and plain icon | The app updated and Sangha Home hadn't refreshed. This now fixes itself within seconds. If not, run `./adb shell am force-stop com.jas.shieldhome` and press Home. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The signing key has changed. Check `app/sangha.keystore` still exists. As a last resort: back up and copy to the Mac (section 3), restore Google's home screen (section 5B), `./adb uninstall com.jas.shieldhome`, install again, then restore the backup. |

### 1E. Going back to an earlier version

GitHub keeps every version of every file. Open the file → **History** → pick an older commit → copy its contents → paste it back in and commit.

---

## 2. Using Sangha Home

### Top bar

- **Left:** Search, then the **Home · Discover · Apps** tabs.
- **Under the tabs (Home only):** the weather, then a small caption about the wallpaper.
- **Right:** VPN city (only when connected), VPN button (green dot = on, amber = off; OK opens Surfshark), settings cog, then the clock and date.
- **Big title:** the name of whatever is selected. It stays blank on the top row.
- **Back** from Discover or Apps returns to Home. The remote's Home button always brings you back to the Home page.

### Home page

- **Dock:** your main apps along the top.
- **Sections:** Watch, Play and Tools. Each can hold apps and folders.
- **New apps** appear on Home automatically in their usual section.

### Home wallpaper

- Sharp trending film and series artwork across the top of the screen, fading into dark before the Watch row. It changes every 30 seconds.
- Darker on the left and top, and behind the clock, so text stays readable on any picture.
- **Caption** under the weather: title · rating · year · type · "Play for info".
- **Press Play/Pause** on the remote to open that title's info card (trailer, Where to watch and so on).
- The colour glow from the selected app is softened while artwork is showing.
- **Home only.** Discover and Apps keep plain backgrounds.
- **Switch it on or off:** cog → Home wallpaper.

### Weather

- One line: icon (sun by day, moon by night), temperature, place, conditions, then today's high and low.
- **Highlight it and press OK** for the forecast card: now and "feels like", today's high, low, rain chance, sunrise and sunset, then the next 12 hours and the next 7 days.
- Change the location: cog → Weather location.

### Hold OK on an app (Home)

Open · Move · Move to group · Add to folder (or Move to another folder / Take out of folder) · Add to or Remove from dock · **Remove from Home** · Cancel

When moving, use the arrows, press OK to drop, or Back to cancel.

### Folders

- **Create one:** hold OK on an app → Add to folder → **New folder** and name it.
- **Add apps:** hold OK on an app → Add to folder → pick the folder.
- **Hold OK on a folder:** Open · Move · Rename · Move to group · Remove folder (keeps the apps).
- A folder disappears on its own once it's empty.

### Apps page

- Every installed app, A to Z. A **green tick** means it's on Home.
- The line under the big title shows where each app lives, for example "On Home · Watch · Kids folder".
- **Hold OK:** Open · Add to Home (choose a section or folder) · Add to or Remove from dock · Remove from Home.

### Discover

- **Tonight's pick:** five rotating picks with Trailer, My list and Surprise me.
- **Rows:** My list, Trending this week, New and highly rated, Binge-worthy series, Tonight's mood and Coming soon.
- **OK on a poster** opens the info card: rating, age rating, genres, length, cast, trailer, My list, **Where to watch** and More like this.
- **Hold OK on a poster:** More info · Trailer · Add to or Remove from My list.

### Where to watch

- The UK services that have the title. Apps you have come first, and pressing OK opens the app (not the exact title, so use the app's own search).
- Dimmed means the app isn't installed.
- Only **Rent**, **Buy**, **Free** or **Free with ads** are labelled. No label means it's included with the subscription.

### Search

Searches your apps instantly, plus every film and series. Recent searches show when the box is empty.

### Cog menu

1. **Shield settings**
2. **Clock time zone:** shows another city's time on Sangha Home and the screensaver. The Shield's own time is unchanged. The city name isn't shown under the date.
3. **Weather location**
4. **Home wallpaper: On/Off**
5. **Backup and restore** (section 3)
6. **About:** credits for TMDB (with logo), JustWatch, Open-Meteo and ipwho.is, plus when the current version was installed.

### Screensaver

- Full-screen artwork that slowly drifts and fades every 20 seconds, with the title bottom left and the clock top right. The text shifts slightly each time to avoid burn-in.
- **Turn it on:** Settings → Device Preferences → Screen saver → Screen saver → **Sangha Home artwork**. **Start now** previews it.
- **If it's not in the list:**
  ```
  ./adb shell settings put secure screensaver_components com.jas.shieldhome/.ArtworkDream
  ```
- **Back to the original:** pick **Backdrop** in the same menu, or:
  ```
  ./adb shell settings put secure screensaver_components com.google.android.backdrop/.Backdrop
  ```

---

## 3. Backup and restore

### On the Shield

Cog → **Backup and restore**:

- **Back up now** saves the dock, sections, folders, apps on Home, My list, recent searches, weather, clock and wallpaper settings.
- **Restore** puts them all back (it asks you to confirm).

The backup survives updates, but **not an uninstall**. Back up after any big layout change.

### Copy it to the Mac

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb pull /sdcard/Android/data/com.jas.shieldhome/files/sangha-home-backup.json ~/Downloads/
```

### Put it back after a reinstall

Open Sangha Home once first, then:

```
./adb shell mkdir -p /sdcard/Android/data/com.jas.shieldhome/files
./adb push ~/Downloads/sangha-home-backup.json /sdcard/Android/data/com.jas.shieldhome/files/
```

Then cog → Backup and restore → **Restore**.

### If apps go missing for a while

Sangha Home remembers where every app belongs even if it can't see it for a while (an update, a restart, a mistake). When the app comes back, it returns to its old place in the dock, section or folder.

---

## 4. Network debugging and security

- **Network debugging stays on**, because updates and backups go through the Mac.
- Only approve the "Allow debugging?" prompt for your own Mac.
- To remove every approved computer: Settings → Device Preferences → Developer options → **Revoke debugging authorisations**.
- **Tip:** reserve `192.168.1.197` for the Shield in your router (DHCP reservation), so the address never changes.

---

## 5. Default home screen

### 5A. What was done

```
./adb shell cmd package set-home-activity com.jas.shieldhome/com.jas.shieldhome.MainActivity
./adb shell pm disable-user --user 0 com.google.android.tvlauncher
./adb shell pm disable-user --user 0 com.google.android.tvrecommendations
./adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

If a Shield system update ever brings Google's home screen back, run these again.

### 5B. Restore Google's home screen

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb shell pm enable com.google.android.tvlauncher
./adb shell pm enable com.google.android.tvrecommendations
./adb shell pm enable com.google.android.tungsten.setupwraith
```

Then press Home, choose Google's home screen, then **Always**. **Always do this before uninstalling Sangha Home.**

---

## 6. Debloat (Batch 1)

Disabled (can be switched back on at any time):

`com.nvidia.stats`, `com.nvidia.feedback`, `com.google.android.feedback`, `com.nvidia.shieldbeta`, `com.nvidia.shield.beta`, `com.nvidia.shield.registration`, `com.nvidia.shield.welcome`, `com.nvidia.developerwidget`, `com.android.printspooler`

**Undo all of them:**

```
for p in com.nvidia.stats com.nvidia.feedback com.google.android.feedback com.nvidia.shieldbeta com.nvidia.shield.beta com.nvidia.shield.registration com.nvidia.shield.welcome com.nvidia.developerwidget com.android.printspooler; do ./adb shell pm enable $p; done
```

**Never disable:** system updates (ota, KeyComboOta, NvCPLUpdater), Google services (gms, gsf, vending, webview), remote and Bluetooth, audio, Chromecast (mediashell), voice search (katniss), screensaver (backdrop), Nvidia Games (tegrazone3), Play Games.

---

## 7. The repository

### Code files (in `app/src/main/java/com/jas/shieldhome/`)

| File | What it looks after |
|---|---|
| `MainActivity.java` | The screen, top bar, tabs, Home and Apps pages, tiles, cog menu, Play/Pause shortcut |
| `Layout.java` | What's on Home and where, including remembering missing apps |
| `Menus.java` | All the hold-OK menus |
| `DiscoverPage.java` | Discover, Tonight's pick, My list and the info card |
| `WhereToWatch.java` | The "Where to watch" row |
| `SearchScreen.java` | Search |
| `Wallpaper.java` | The Home wallpaper and its caption |
| `ArtworkDream.java` | The screensaver |
| `WeatherClock.java` | Weather, forecast card and clock time zone |
| `VpnStatus.java` | The VPN button and city |
| `Backup.java` | Backup and restore |
| `About.java` | Cog → About |
| `Tmdb.java` | Talking to TMDB |
| `Images.java` | Downloading and caching artwork |
| `Ui.java` | Shared colours, sizes and helpers |

### Other files

| File | Purpose |
|---|---|
| `app/src/main/AndroidManifest.xml` | App name, permissions, home screen and screensaver (see section 8) |
| `app/src/main/res/drawable/banner.xml` | App banner |
| `app/src/main/res/drawable/ic_cog.xml`, `ic_search.xml`, `ic_shield.xml` | Top bar icons |
| `app/src/main/res/drawable/tmdb_logo.png` | TMDB logo for About (official file from TMDB's logos page) |
| `app/build.gradle` | App settings, signing key, TMDB token |
| `.github/workflows/build.yml` | Builds, signs and publishes each release |
| `app/sangha.keystore` | Permanent signing key — **never delete** |
| `GUIDE.md` | This guide |

**GitHub secret:** `TMDB_TOKEN` (Settings → Secrets and variables → Actions).

**Going private:** this hides the code, keystore and token, but the download link stops working. Either switch to public briefly for each update, or install GitHub's command-line tool (`brew install gh`, then `gh auth login`) and download with:
```
gh release download -R Jas-Sangha91/shield-pro -p sangha-home.apk -D ~/Downloads --clobber
```

---

## 8. AndroidManifest.xml — must keep

Since Android 11, an app can only see other apps if its manifest says so. If these lines are ever lost, most apps vanish from Home and Apps (only the Shield's built-in ones remain). Keep:

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

The manifest also holds the `INTERNET` and `ACCESS_NETWORK_STATE` permissions, the `MainActivity` home screen entries (HOME and LEANBACK_LAUNCHER), and the `ArtworkDream` screensaver service.

---

## 9. Data sources and credits

| Feature | Source |
|---|---|
| Discover, search, wallpaper and screensaver artwork | TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. |
| Where to watch | JustWatch, via TMDB |
| Weather, forecast, place search, clock time zones | Open-Meteo.com |
| VPN location | ipwho.is |

Credits appear in cog → About and at the bottom of Discover.

---

## 10. Samsung TV setup (still to do)

- Turn off Viewing Information Services and Interest-Based Advertising.
- Disconnect the TV from the internet.
- Turn off Smart Hub auto-start.
- Turn on Anynet+ (HDMI-CEC) on the TV and CEC on the Shield.

---

## 11. Change log

**24 October 2026**

- Debloat Batch 1 and a permanent signing key.
- Home with dock, Watch, Play and Tools sections, and folders.
- Hold-OK menus, Discover, Search, VPN status, weather and clock time zone.
- Sangha Home made the default home screen.
- Apps page with "Remove from Home".
- Code split into smaller files.
- Where to watch, with simpler labels and the JustWatch credit moved to Discover and About.
- Backup and restore, with copies to the Mac.
- `sangha-update` one-command updates.
- App tiles refresh automatically when an app updates.
- Manifest fixed so all apps are visible (section 8).
- Layout remembers missing apps and restores their places.
- Home wallpaper: sharp artwork fading into dark, caption under the weather, Play for info, readable clock on bright pictures.
- Screensaver with drifting artwork, title and clock.
- Cog → About with TMDB logo and credits.
- Weather: day and night icons, today's high and low, and a forecast card on OK.
- City name removed from under the clock (Home and screensaver).

---

## 12. Ideas and to-do

- Discover background that changes to match the highlighted poster (Netflix style).
- Screensaver extras: left/right to skip, textless artwork and title logos.
- Daily restart at 4am (on/off switch in the cog menu).
- Voice search with the Nvidia remote mic (parked).
- Keep the TMDB token out of the app and repository.
- Decide whether to make the repository private (section 7).
- Fire TV Stick version (Fire OS models only).
