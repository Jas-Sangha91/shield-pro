# Sangha Home — Guide

A custom home screen for the Nvidia Shield TV Pro. Last updated: 25 October 2026

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
| Error log | `./adb logcat -s SanghaHome` (see section 1D) |

---

## 1. Updating Sangha Home

### 1A. Make a change and build

1. On GitHub, open the file, click the pencil, paste the new version and commit to `main`.
2. **Before committing a long file, scroll to the bottom** and check it ends properly (usually a single `}` on its own line). Long pastes sometimes get cut off.
3. Open the **Actions** tab and wait for the green tick.
4. When a change touches several files, the builds in between may fail (red cross) or be cancelled (grey "!"). That's expected. Only the build after the **last** file matters.
5. For a **new file**, click **Add file → Create new file** in the code folder and type the full name, for example `app/src/main/java/com/jas/shieldhome/BrowsePage.java`.

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
| **"Couldn't load Discover"** | Look at the **Details** line under the message, which says what went wrong. No Details line usually means a real connection problem (internet or VPN). Discover waits 30 seconds before trying again. If Search finds films, TMDB is working and it's a code problem: send the Details line. |
| **A Discover row is missing** | That row failed on its own and the rest loaded. Check the error log (below). |
| **Browse says "Couldn't find that subgenre on TMDB right now"** | TMDB's keyword search didn't answer. Try again later. Once a subgenre works, it's remembered. |
| Most apps disappear from Home and Apps | `AndroidManifest.xml` has lost its "see all apps" lines. See section 8. Don't back up until it's fixed. |
| An app tile shows a code name and plain icon | The app updated and Sangha Home hadn't refreshed. This now fixes itself within seconds. If not, run `./adb shell am force-stop com.jas.shieldhome` and press Home. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The signing key has changed. Check `app/sangha.keystore` still exists. As a last resort: back up and copy to the Mac (section 3), restore Google's home screen (section 5B), `./adb uninstall com.jas.shieldhome`, install again, then restore the backup. |

**Reading the error log.** Sangha Home writes problems to the Shield's system log. To watch it live, open Discover (or whatever isn't working) while this runs, then press Ctrl+C to stop:

```
cd ~/Downloads/platform-tools
./adb connect 192.168.1.197:5555
./adb logcat -s SanghaHome
```

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

- Sharp film and series artwork across the top of the screen, fading into dark before the Watch row. It changes every 30 seconds.
- **The mix:** trending titles plus popular Hindi and Punjabi titles, following your languages (see Languages below).
- Darker on the left and top, and behind the clock, so text stays readable on any picture.
- **Caption** under the weather: title · rating · year · type · language (international titles only) · "Play for info".
- **Press Play/Pause** on the remote to open that title's info card (trailer, Where to watch and so on).
- The colour glow from the selected app is softened while artwork is showing.
- **Switch it on or off:** cog → Home wallpaper. This only affects Home. The Discover background is always on.

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

From top to bottom:

1. **Tonight's pick:** five rotating picks with Trailer, My list and Surprise me.
2. **Browse by genre:** Horror, Comedy, Action, Thriller, Sci-fi, Drama, Crime, Mystery, Romance, Fantasy, Animation, Family, Documentary, War, Western and **All filters**. Each opens Browse (below).
3. **My list**
4. **Trending this week**
5. **Hindi and Punjabi:** popular Hindi and Punjabi films, two Hindi to one Punjabi.
6. **New and highly rated**, **Binge-worthy series**, **Tonight's mood** (a different genre each day) and **Coming soon**.

Also:

- **The background follows the highlighted poster.** Pause on a poster and, after a moment, the artwork behind the page fades to that title. Fast scrolling doesn't flicker.
- **OK on a poster** opens the info card: rating, age rating, genres, length, language (international titles), cast, trailer, My list, **Where to watch** and More like this.
- **Hold OK on a poster:** More info · Trailer · Add to or Remove from My list.
- **Language labels:** international titles show a small label, for example "Korean", in the poster's bottom corner.
- Everything follows your languages (see Languages below). Discover refreshes every 3 hours.
- If one row can't load, the rest still appear.

### Browse

A full-screen page of every film and series on TMDB, from any country and any service, whether or not you can stream it.

- **Opening it:** a genre button on Discover, **All filters**, a genre shortcut in Search, or a person in Search.
- **It always opens on Popular**, with any decade and length. **Films or Series** is remembered from last time. All filters opens on **All films**.
- **Filter buttons** along the top (press one to choose from a list):
  - **Films / Series**
  - **Genre**
  - **Subgenre** (Horror and Comedy only):
    - Horror: Slasher, Supernatural, Found footage, Zombie, Ghost, Haunted house, Vampire, Creature feature, Body horror, Psychological
    - Comedy: Romantic comedy, Dark comedy, Spoof, Stand-up
  - **Sort:** Popular, Top rated, Newest, Hidden gems (well rated but less well known)
  - **Decade:** Any, 2020s, 2010s, 2000s, 1990s, 1980s, Older
  - **Length** (films only): Any, Under 100 min, About 2 hours, Long (over 2h 20m)
- **Posters** fill a grid seven across, and more load as you move down. OK opens the info card, and Back closes Browse.
- The highlighted title and its details show at the top, and the background follows the highlighted poster.
- The first time you use each subgenre there's a short pause while it looks up TMDB's tag. After that it's remembered.
- **Good to know:** subgenres rely on TMDB's community tags, so popular and older films are covered best. Horror **series** are the thinnest, because TMDB has no horror genre for series.

### Languages

Sangha Home shows **English, Hindi and Punjabi** titles, plus **big international hits** (anything with at least 1,000 ratings on TMDB). These are the most likely to have an English or Hindi dub.

- This applies to Discover, Browse, Tonight's pick, Surprise me, More like this, the Home wallpaper and the screensaver.
- **Search isn't filtered**, so typing a title always finds it.
- International titles get a **language label** (poster, info card, wallpaper caption and screensaver), so you know to check the audio.
- TMDB doesn't record which dubs a service offers, so a labelled title may or may not have English audio. Check in the app.
- The languages and the 1,000-ratings threshold are set in `Languages.java` (`MINE` and `HIT_VOTES`).

### Where to watch

- The UK services that have the title. Apps you have come first, and pressing OK opens the app (not the exact title, so use the app's own search).
- Dimmed means the app isn't installed.
- Only **Rent**, **Buy**, **Free** or **Free with ads** are labelled. No label means it's included with the subscription.

### Search

Searches your apps instantly, plus every film, series and person in every language. Recent searches (the last six) show when the box is empty.

- **Genre shortcuts:** type a genre or subgenre and a **Browse** row appears at the top straight away. For example "horror" gives **Horror films**, "slasher" gives **Horror films · Slasher**, and "romcom" gives **Comedy films · Romantic comedy**. Press it to open Browse with those filters, and Back returns to your search.
  - Add "series" or "films" to choose the type ("horror series"). Otherwise it uses your last Browse choice.
  - Everyday words work too: scary, funny, sci-fi, whodunnit, anime, kids, zombie, found footage, haunted, vampire, monster, parody, stand-up and more.
  - It suggests as you type, so "horr" already offers Horror.
- **People:** typing an actor's or director's name shows a **People** row with their photo, name and job. If the name is the best match, it comes before films and series.
  - Choosing someone opens their work: an actor's films and series (without talk shows or "as themselves" appearances), or what a director directed.
  - Buttons: **Films and series / Films / Series**, and **Best known / Newest**.
  - It follows your languages, unless all their work is in other languages, in which case it shows everything.
  - People without a photo on TMDB are left out of the row. If someone's missing, search one of their films and look at the cast on the info card.

### Cog menu

1. **Shield settings**
2. **Clock time zone:** shows another city's time on Sangha Home and the screensaver. The Shield's own time is unchanged. The city name isn't shown under the date.
3. **Weather location**
4. **Home wallpaper: On/Off**
5. **Backup and restore** (section 3)
6. **About:** credits for TMDB (with logo), JustWatch, Open-Meteo and ipwho.is, plus when the current version was installed.

### Screensaver

- Full-screen artwork that slowly drifts and fades every 20 seconds, with the clock top right. The text shifts slightly each time to avoid burn-in.
- **Title logos:** the film's official logo is shown bottom left when TMDB has one (English first, then Hindi or Punjabi), otherwise the title as text. Rating, year, type and language (international titles) are shown underneath.
- **The mix:** trending, top-rated films and series, and popular Hindi and Punjabi titles, following your languages.
- **Remote:** **left and right** skip to the previous or next picture. **Volume** works as normal. Any other button ends the screensaver.
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

The backup survives updates, but **not an uninstall**. Back up after any big layout change. Browse's last Films/Series choice and the remembered subgenre tags aren't in the backup, but they come back on their own as you use Browse.

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
| `DiscoverPage.java` | Discover, Tonight's pick, Browse by genre row, My list, the rows and the info card |
| `BrowsePage.java` | Browse: genres, subgenres, filters, the poster grid, and a person's films and series |
| `Languages.java` | Which languages are shown, the international hit rule, and language labels |
| `WhereToWatch.java` | The "Where to watch" row |
| `SearchScreen.java` | Search, genre shortcuts and people |
| `Wallpaper.java` | The Home wallpaper and caption, and the Discover background that follows the poster |
| `ArtworkDream.java` | The screensaver, with title logos and left/right skipping |
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
| Discover, Browse, search, people, title logos, wallpaper and screensaver artwork | TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. |
| Subgenres | TMDB keywords (community tags) |
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

**25 October 2026**

- Discover background now follows the highlighted poster, Netflix style.
- Screensaver: left and right skip pictures, official title logos instead of plain text, volume keeps working.
- New **Browse** page: every film and series worldwide, with genre, subgenre (Horror and Comedy), sort, decade and length filters. Always opens on Popular, and remembers Films or Series.
- **Browse by genre** row on Discover.
- **Languages:** English, Hindi and Punjabi plus big international hits, across Discover, Browse, the wallpaper and the screensaver, with language labels on international titles.
- New **Hindi and Punjabi** row on Discover, and Hindi and Punjabi titles added to the wallpaper and screensaver.
- Search: **genre shortcuts** that open Browse, and **people** (actors and directors) with their films and series.
- Discover fix: no longer asks TMDB for pages that don't exist, loads each row separately so one failure doesn't stop the page, and shows a **Details** line when it can't load. Errors are written to the log (`./adb logcat -s SanghaHome`).

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

- **Help me pick:** three quick questions (mood or genre, how long you have, new or classic) and three picks with reasons. This targets "I don't know what to watch".
- Trim the Browse by genre row to the most-used genres plus "More genres".
- Cog setting for languages (instead of editing `Languages.java`).
- Review the international hit threshold (1,000 ratings), since recent hits take a while to reach it.
- Where to watch: choose the country, or show other countries when a title isn't on a UK service.
- Screensaver: textless artwork (pictures without words already on them).
- Daily restart at 4am (on/off switch in the cog menu).
- Voice search with the Nvidia remote mic (parked).
- Keep the TMDB token out of the app and repository.
- Decide whether to make the repository private (section 7).
- Fire TV Stick version (Fire OS models only).
