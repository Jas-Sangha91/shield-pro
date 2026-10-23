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
| Signing key | `app/sangha.keystore` (password `sanghahome`, alias `sangha`) — **never delete** |
| Backup file on Shield | `/sdcard/Android/data/com.jas.shieldhome/files/sangha-home-backup.json` |

---

## 1. Updating Sangha Home

### 1A. Make a change and build

1. On GitHub, open the file in the code folder, click the pencil, paste the new version and commit to `main`.
2. **Before committing a long file, scroll to the bottom** and check it ends properly (usually a single `}` on its own line). Long pastes sometimes get cut off.
3. Open the **Actions** tab and wait for the green tick.
4. When a change touches several files, the builds in between may fail (red cross) or be cancelled (grey "!"). That's expected. Only the build after the **last** file matters.

### 1B. Install from the Mac (preferred method)

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
curl -L -o ~/Downloads/sangha-home.apk https://github.com/Jas-Sangha91/shield-pro/releases/latest/download/sangha-home.apk
ls -lh ~/Downloads/sangha-home.apk
./adb install -r ~/Downloads/sangha-home.apk
```

- `connect` should say `connected to 192.168.1.197:5555`.
- `ls` should show a size of a few MB. If it says something tiny like `9B`, the download failed, so don't install it.
- `install` should end with `Success`.

Your dock, folders, sections, Apps page choices, My list, recent searches, weather and clock settings are all kept through updates.

### 1C. Install on the Shield (backup method)

Open **Downloader** on the Shield, enter the latest APK link from the quick reference, then choose Install and Open.

### 1D. If something goes wrong

| What you see | What it means and what to do |
|---|---|
| `adb: no devices/emulators found` | The Mac isn't connected. Run `./adb connect 192.168.1.197:5555` first. |
| `failed to connect` or `Connection refused` | Network debugging is off, the Shield is asleep, or its IP has changed. Wake the Shield and check Settings → Device Preferences → Developer options → Network debugging. |
| `unauthorized` | Accept the "Allow debugging?" prompt on the TV and tick **Always allow from this computer**. |
| `device offline` | Run `./adb disconnect`, then connect again. If that fails, run `./adb kill-server` and try again. |
| Download is only a few bytes | The build hasn't finished or failed. Check the Actions tab. |
| Build error `reached end of file while parsing` | A paste got cut off. Open the file named in the error and check the bottom. Re-paste it, or paste just the missing end. |
| Any other build error | Open the failed run → the red step → copy the lines containing `error:`. They name the file and line number. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The signing key has changed. Check `app/sangha.keystore` still exists. As a last resort, back up (section 3), restore Google's home screen (section 4B), `./adb uninstall com.jas.shieldhome`, install again, then restore the backup. |

### 1E. Going back to an earlier version

GitHub keeps every version of every file. Open the file → **History** → pick an older commit → copy its contents → paste it back in and commit.

---

## 2. Using Sangha Home

### Top bar

- **Left:** Search, then the **Home · Discover · Apps** tabs, then the weather (Home only).
- **Right:** VPN city (only when connected), VPN button (green dot = on, amber = off; OK opens Surfshark), settings cog, then the clock and date.
- **Big title:** shows the name of whatever is selected. It stays blank on the top row.
- **Back** from Discover or Apps returns to Home. The remote's Home button always brings you back to the Home page.

### Home page

- **Dock:** your main apps along the top. Tiles shrink to fit if you add more.
- **Sections:** Watch, Play and Tools. Each can hold apps and folders.
- **New apps** appear on Home automatically in their usual section.

### Hold OK on an app (Home)

Open · Move · Move to group · Add to folder (or Move to another folder / Take out of folder) · Add to or Remove from dock · **Remove from Home** · Cancel

When moving, use the arrows, press OK to drop, or Back to cancel. "Remove from Home" takes the app off Home but keeps it in the Apps page.

### Folders

- **Create one:** hold OK on an app → Add to folder → **New folder** and name it.
- **Add apps:** hold OK on an app → Add to folder → pick the folder.
- **Hold OK on a folder:** Open · Move · Rename · Move to group · Remove folder (keeps the apps).
- A folder disappears on its own once it's empty.

### Apps page

- Every installed app, A to Z. A **green tick** means it's on Home.
- The line under the big title shows where each app lives, for example "On Home · Watch · Kids folder" or "Not on Home".
- **Hold OK:** Open · Add to Home (choose Watch, Play, Tools or a folder) · Add to or Remove from dock · Remove from Home.

### Discover

- **Tonight's pick:** five rotating picks with Trailer, My list and Surprise me.
- **Rows:** My list, Trending this week, New and highly rated, Binge-worthy series, Tonight's mood (genre changes daily) and Coming soon. Refreshes every 3 hours.
- **OK on a poster** opens the info card: rating, age rating, genres, length, cast, trailer, My list, **Where to watch** and More like this.
- **Hold OK on a poster:** More info · Trailer · Add to or Remove from My list.

### Where to watch

- Shows the UK services that have the title: Included, Free, Free with ads, Rent or Buy (data from JustWatch).
- Services you have installed come first, and pressing OK opens the app. It opens the app itself, not the exact title, so use the app's own search from there.
- Services you don't have are dimmed and marked "Not installed".

### Search

Searches your apps instantly, plus every film and series. Recent searches show when the box is empty. Back hides the keyboard, and Back again closes Search.

### Cog menu

1. **Shield settings:** opens the Shield's own settings.
2. **Clock time zone:** shows another city's time on Sangha Home only, with the city's name under the date. **Use Shield's time** switches back. The Shield's own time is never changed.
3. **Weather location:** search for a town or city, or remove the weather.
4. **Backup and restore:** see section 3.

### Other touches

- The background glow takes the colour of the selected item.
- The background shifts with the time of day.
- After 20 seconds idle, everything except the selected item dims.

---

## 3. Backup and restore

### On the Shield

Cog → **Backup and restore**:

- **Back up now** saves your dock, sections, folders, which apps are on Home, My list, recent searches, weather and clock settings.
- **Restore** puts them all back (it asks you to confirm first).
- The menu shows when you last backed up.

The backup on the Shield survives updates, but **not an uninstall**.

### Copy the backup to the Mac

After backing up on the Shield:

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

Then use cog → Backup and restore → **Restore**.

---

## 4. Default home screen

### 4A. What was done

```
./adb shell cmd package set-home-activity com.jas.shieldhome/com.jas.shieldhome.MainActivity
./adb shell pm disable-user --user 0 com.google.android.tvlauncher
./adb shell pm disable-user --user 0 com.google.android.tvrecommendations
./adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

If a Shield system update ever brings Google's home screen back, run these again.

### 4B. Restore Google's home screen

Network debugging must be on. From the Mac:

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb shell pm enable com.google.android.tvlauncher
./adb shell pm enable com.google.android.tvrecommendations
./adb shell pm enable com.google.android.tungsten.setupwraith
```

Then press Home, choose Google's home screen, then **Always**.

**Always restore Google's home screen before uninstalling Sangha Home**, or the Shield won't have a home screen.

---

## 5. Debloat (Batch 1)

These were disabled and can be switched back on at any time:

`com.nvidia.stats`, `com.nvidia.feedback`, `com.google.android.feedback`, `com.nvidia.shieldbeta`, `com.nvidia.shield.beta`, `com.nvidia.shield.registration`, `com.nvidia.shield.welcome`, `com.nvidia.developerwidget`, `com.android.printspooler`

**Undo all of them:**

```
for p in com.nvidia.stats com.nvidia.feedback com.google.android.feedback com.nvidia.shieldbeta com.nvidia.shield.beta com.nvidia.shield.registration com.nvidia.shield.welcome com.nvidia.developerwidget com.android.printspooler; do ./adb shell pm enable $p; done
```

**Never disable:** system updates (ota, KeyComboOta, NvCPLUpdater), Google services (gms, gsf, vending, webview), remote and Bluetooth, audio, Chromecast (mediashell), voice search (katniss), screensaver (backdrop), Nvidia Games (tegrazone3), Play Games.

---

## 6. Network debugging and security

- **Network debugging stays on**, because updates and backups go through the Mac.
- Only approve the "Allow debugging?" prompt for your own Mac. No other device on the Wi-Fi can connect without that approval on the TV.
- To remove every approved computer: Settings → Device Preferences → Developer options → **Revoke debugging authorisations**. The Mac will ask again next time.
- **Tip:** reserve `192.168.1.197` for the Shield in your router (often called DHCP reservation or static lease), so the address never changes.

---

## 7. The repository

### Code files (in `app/src/main/java/com/jas/shieldhome/`)

| File | What it looks after |
|---|---|
| `MainActivity.java` | The screen, top bar, tabs, Home and Apps pages, app tiles, cog menu |
| `Layout.java` | What's on Home and where: dock, sections, folders, apps off Home |
| `Menus.java` | All the hold-OK menus for apps and folders |
| `DiscoverPage.java` | Discover, Tonight's pick, My list and the info card |
| `WhereToWatch.java` | The "Where to watch" row on the info card |
| `SearchScreen.java` | Search |
| `Tmdb.java` | Talking to TMDB (films and series data) |
| `Images.java` | Downloading and caching posters and artwork |
| `WeatherClock.java` | Weather and clock time zone |
| `VpnStatus.java` | The VPN button and city |
| `Backup.java` | Backup and restore |
| `Ui.java` | Shared colours, sizes and small helpers |

### Other files

| File | Purpose |
|---|---|
| `settings.gradle` | Project setup |
| `build.gradle` | Android build tools version |
| `app/build.gradle` | App settings, signing key, TMDB token |
| `app/src/main/AndroidManifest.xml` | App name, banner, permissions, home screen category |
| `app/src/main/res/drawable/banner.xml` | App banner |
| `app/src/main/res/drawable/ic_cog.xml` | Settings icon |
| `app/src/main/res/drawable/ic_shield.xml` | VPN icon |
| `app/src/main/res/drawable/ic_search.xml` | Search icon |
| `.github/workflows/build.yml` | Builds, signs and publishes each release |
| `app/sangha.keystore` | Permanent signing key — **never delete** |
| `GUIDE.md` | This guide |

**GitHub secret:** `TMDB_TOKEN`, the TMDB API Read Access Token (starts with `eyJ`). Found under Settings → Secrets and variables → Actions.

**Going private:** a private repository hides the code, keystore and TMDB token, but the `curl` download link stops working. Options at that point:

- Switch to public briefly for each update, then back to private.
- Or install the GitHub command-line tool once (`brew install gh`, then `gh auth login`) and download with:
  ```
  gh release download -R Jas-Sangha91/shield-pro -p sangha-home.apk -D ~/Downloads --clobber
  ```
  then run `./adb install -r ~/Downloads/sangha-home.apk` as usual.

---

## 8. Data sources

| Feature | Source |
|---|---|
| Weather, place search, clock time zones | Open-Meteo.com (free, no key) |
| VPN location | ipwho.is (free, no key) |
| Discover and film search | TMDB (free for personal use). This product uses the TMDB API but is not endorsed or certified by TMDB. |
| Where to watch | JustWatch, via TMDB |

---

## 9. Samsung TV setup (still to do)

- Turn off Viewing Information Services and Interest-Based Advertising.
- Disconnect the TV from the internet (forget the Wi-Fi network).
- Turn off Smart Hub auto-start.
- Turn on Anynet+ (HDMI-CEC) on the TV and CEC on the Shield.

---

## 10. Change log

**24 October 2026**

- Debloat Batch 1, and a permanent signing key so updates install over the top.
- Home page with dock and Watch, Play and Tools sections.
- Folders in any section, with a "More tools" folder created on first setup.
- Hold-OK menus for apps, folders and posters.
- Discover page with Tonight's pick, My list, daily mood row and info cards.
- Full-screen search for apps, films and series, with recent searches.
- VPN status with city, weather on Home only, and clock time zone option.
- Sangha Home made the default home screen.
- Apps page listing every app, with "Remove from Home" replacing Hide.
- Tabs reordered to Home · Discover · Apps.
- Code split from one large file into smaller files, one per area.
- "Where to watch" on the info card.
- Backup and restore in the cog menu.

---

## 11. Ideas and to-do

- Home wallpaper rotating through Discover artwork, with an on/off switch.
- Screensaver using Discover artwork.
- Daily restart at 4am (on/off switch in the cog menu).
- Voice search with the Nvidia remote mic (parked for now).
- Keep the TMDB token out of the app and repository.
- Decide whether to make the repository private (see section 7).
- Fire TV Stick version (Fire OS models only, not Vega OS "Select" models).
