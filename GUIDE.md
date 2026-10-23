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
| App package | `com.jas.shieldhome` |
| APK download | `https://github.com/Jas-Sangha91/shield-pro/releases/download/vNUMBER/sangha-home.apk` |
| Signing key | `app/sangha.keystore` (password `sanghahome`, alias `sangha`) — **never delete** |

---

## 1. Updating Sangha Home

### 1A. Make a change and build

1. On GitHub, open the file, click the pencil, paste the new version and commit to `main`.
2. Open the **Actions** tab and wait for the green tick (a red cross means the build failed, so check the log).
3. Open **Releases** and note the new version number, for example `v34`.

### 1B. Install from the Mac (preferred method)

In Terminal, replacing `vNUMBER` with the version from step 3:

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
curl -L -o ~/Downloads/sangha-home.apk https://github.com/Jas-Sangha91/shield-pro/releases/download/vNUMBER/sangha-home.apk
./adb install -r ~/Downloads/sangha-home.apk
```

The last line should end with `Success`. Sangha Home restarts with the new version, and your dock, folders, groups, hidden apps, My list, recent searches, weather and clock settings are all kept.

**Shortcut:** this link always points at the newest release, so there's no version number to type. If it downloads correctly, use it instead:

```
curl -L -o ~/Downloads/sangha-home.apk https://github.com/Jas-Sangha91/shield-pro/releases/latest/download/sangha-home.apk
```

### 1C. Install on the Shield (backup method)

Open **Downloader** on the Shield, enter the APK download link, then choose Install and Open. This works even if the Mac isn't available.

### 1D. If an update fails

| Message | What it means and what to do |
|---|---|
| `failed to connect` or `Connection refused` | Network debugging is off, the Shield is asleep, or its IP address has changed. Wake the Shield and check Settings → Device Preferences → Developer options → Network debugging. To check the IP, go to Settings → Network & Internet. |
| `unauthorized` | Look at the TV and accept the "Allow debugging?" prompt. Tick **Always allow from this computer**. |
| `device offline` | Run `./adb disconnect`, then `./adb connect 192.168.1.197:5555` again. If that fails, run `./adb kill-server` and try again. |
| `INSTALL_PARSE_FAILED_NOT_APK` or a tiny downloaded file | The version number is wrong, the build hasn't finished, or the repository is private (see section 6). |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The signing key has changed. Check `app/sangha.keystore` still exists in the repository. As a last resort, `./adb uninstall com.jas.shieldhome` and install again, **but this wipes your layout**, and you must restore Google's home screen first (section 3B). |

---

## 2. Using Sangha Home

### Top bar

- **Left:** Search, the Home and Discover tabs, and the weather (Home only).
- **Right:** VPN city (only when connected), VPN button (green dot means on, amber means off; press OK to open Surfshark), settings cog, and the clock and date.
- **Big title:** shows the name of whatever is selected. It stays blank on the top row.

### Home page

- **Dock:** your main apps along the top (Netflix, Prime Video, Disney+, YouTube and TiviMate by default). Tiles shrink to fit if you add more.
- **Sections:** Watch, Play and Tools. Each can hold apps and folders.
- **Hidden apps:** shown dimmed at the bottom.

### Hold OK on an app

Open · Move · Move to group · Add to folder (or Move to another folder / Take out of folder) · Add to or Remove from dock · Hide or Unhide · Cancel

When moving, use the arrows, press OK to drop, or Back to cancel.

### Folders

- **Create one:** hold OK on an app → Add to folder → **New folder** and name it. The folder appears where that app was.
- **Add more apps:** hold OK on an app → Add to folder → pick the folder.
- **Hold OK on a folder:** Open · Move · Rename · Move to group · Remove folder (keeps the apps).
- A folder disappears on its own once it's empty.
- On first setup, the rarely used tools go into a **More tools** folder in the Tools section.

### Cog menu

1. **Shield settings:** opens the Shield's own settings.
2. **Clock time zone:** shows another city's time on Sangha Home only, with the city's name under the date. **Use Shield's time** switches back. The Shield's own time is never changed.
3. **Weather location:** search for a town or city, or remove the weather.

### Discover

- **Tonight's pick:** five rotating picks with Trailer, My list and Surprise me buttons.
- **Rows:** My list (when you've saved something), Trending this week, New and highly rated, Binge-worthy series, Tonight's mood (genre changes daily) and Coming soon. Refreshes every 3 hours.
- **OK on a poster** opens the info card with trailer, rating, age rating, cast and More like this.
- **Hold OK on a poster:** More info · Trailer · Add to or Remove from My list.

### Search

Searches your apps instantly, plus every film and series. Recent searches show when the box is empty. Back hides the keyboard, and Back again closes Search.

### Other touches

- The background glow takes the colour of the selected item.
- The background shifts with the time of day.
- After 20 seconds idle, everything except the selected item dims.

---

## 3. Default home screen

### 3A. What was done

Sangha Home was made the home screen, and Google's home screen, its recommendations feed and the app that hijacks the Home button were switched off:

```
./adb shell cmd package set-home-activity com.jas.shieldhome/com.jas.shieldhome.MainActivity
./adb shell pm disable-user --user 0 com.google.android.tvlauncher
./adb shell pm disable-user --user 0 com.google.android.tvrecommendations
./adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

### 3B. Restore Google's home screen

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

## 4. Debloat (Batch 1)

These were disabled and can be switched back on at any time:

`com.nvidia.stats`, `com.nvidia.feedback`, `com.google.android.feedback`, `com.nvidia.shieldbeta`, `com.nvidia.shield.beta`, `com.nvidia.shield.registration`, `com.nvidia.shield.welcome`, `com.nvidia.developerwidget`, `com.android.printspooler`

**Undo all of them:**

```
for p in com.nvidia.stats com.nvidia.feedback com.google.android.feedback com.nvidia.shieldbeta com.nvidia.shield.beta com.nvidia.shield.registration com.nvidia.shield.welcome com.nvidia.developerwidget com.android.printspooler; do ./adb shell pm enable $p; done
```

**Never disable:** system updates (ota, KeyComboOta, NvCPLUpdater), Google services (gms, gsf, vending, webview), remote and Bluetooth, audio, Chromecast (mediashell), voice search (katniss), screensaver (backdrop), Nvidia Games (tegrazone3), Play Games.

---

## 5. Network debugging and security

- **Network debugging stays on**, because updates are installed from the Mac.
- Only approve the "Allow debugging?" prompt for your own Mac. Any other device on the Wi-Fi can't connect without that approval on the TV.
- To remove every approved computer: Settings → Device Preferences → Developer options → **Revoke debugging authorisations**. The Mac will ask again next time.
- **Tip:** reserve `192.168.1.197` for the Shield in your router settings (often called DHCP reservation or static lease), so the address never changes.

---

## 6. The repository

| File | Purpose |
|---|---|
| `settings.gradle` | Project setup |
| `build.gradle` | Android build tools version |
| `app/build.gradle` | App settings, signing key, TMDB token |
| `app/src/main/AndroidManifest.xml` | App name, banner, permissions, home screen category |
| `app/src/main/java/com/jas/shieldhome/MainActivity.java` | The whole app |
| `app/src/main/res/drawable/banner.xml` | App banner |
| `app/src/main/res/drawable/ic_cog.xml` | Settings icon |
| `app/src/main/res/drawable/ic_shield.xml` | VPN icon |
| `app/src/main/res/drawable/ic_search.xml` | Search icon |
| `.github/workflows/build.yml` | Builds, signs and publishes each release |
| `app/sangha.keystore` | Permanent signing key — **never delete** |
| `GUIDE.md` | This guide |

**GitHub secret:** `TMDB_TOKEN`, the TMDB API Read Access Token (starts with `eyJ`). Found under Settings → Secrets and variables → Actions.

**Going private:** a private repository hides the code and the TMDB token, but the `curl` download link stops working. Options at that point:

- Switch to public briefly for each update, then back to private.
- Or install the GitHub command-line tool once (`brew install gh`, then `gh auth login`) and download with:
  ```
  gh release download vNUMBER -R Jas-Sangha91/shield-pro -p sangha-home.apk -D ~/Downloads --clobber
  ```
  then run `./adb install -r ~/Downloads/sangha-home.apk` as usual.

---

## 7. Data sources

| Feature | Source |
|---|---|
| Weather, place search, clock time zones | Open-Meteo.com (free, no key) |
| VPN location | ipwho.is (free, no key) |
| Discover and film search | TMDB (free for personal use). This product uses the TMDB API but is not endorsed or certified by TMDB. |

---

## 8. Samsung TV setup (still to do)

- Turn off Viewing Information Services and Interest-Based Advertising.
- Disconnect the TV from the internet (forget the Wi-Fi network).
- Turn off Smart Hub auto-start.
- Turn on Anynet+ (HDMI-CEC) on the TV and CEC on the Shield.

---

## 9. Change log

**24 October 2026**

- Debloat Batch 1, and a permanent signing key so updates install over the top.
- Home page with dock, Watch, Play and Tools sections, and hidden apps.
- Folders in any section, plus a "More tools" folder created on first setup.
- Hold-OK menus for apps, folders and posters.
- Discover page (TMDB) with Tonight's pick, My list, daily mood row and info cards.
- Full-screen search for apps, films and series, with recent searches.
- VPN status with city, weather on Home only, and clock time zone option.
- Big title stays blank on the top row.
- Cog menu trimmed to Shield settings, Clock time zone and Weather location.
- Sangha Home made the default home screen.

---

## 10. Ideas and to-do

- Daily restart at 4am (decision pending: built-in via Network debugging, or a smart plug).
- Decide whether to make the repository private (see section 6).
- Screensaver using Discover backdrops.
- Rotating wallpapers.
- Voice search with the Nvidia remote mic.
- Keep the TMDB token out of the APK.
- Fire TV Stick version (Fire OS models only, not Vega OS "Select" models).
