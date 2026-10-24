package com.jas.shieldhome;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jas.shieldhome.Ui.str;

/**
 * What's on Home and where: the dock, the sections, folders, and apps that are off Home.
 *
 * Two versions are kept:
 *  - the lists below (dock, grid, hidden, folders) hold only apps that are installed right now,
 *    and are what the screen shows;
 *  - the saved version also remembers where any missing apps belong, so if an app briefly
 *    disappears (an update, a Shield restart, a mistake), it goes straight back to its old
 *    place when it returns instead of being forgotten.
 */
final class Layout {

    static final String WATCH = "watch", PLAY = "play", TOOLS = "tools";
    static final String[] GROUP_ORDER = {WATCH, PLAY, TOOLS};
    static final String FOLDER_PREFIX = "f:";

    // Dock apps used the very first time only.
    private static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.google.android.youtube.tv",
            "ar.tvplayer.tv"
    };

    private static final List<String> PLAY_PKGS = Arrays.asList(
            "com.nvidia.tegrazone3",
            "com.nvidia.geforcenow",
            "psplay.grill.com",
            "com.swordfish.lemuroid",
            "com.google.android.play.games");

    private static final List<String> TOOLS_PKGS = Arrays.asList(
            "com.surfshark.vpnclient.android",
            "com.esaba.downloader",
            "com.cxinventor.file.explorer",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.tcl.browser",
            "com.android.vending",
            "com.android.gallery3d",
            "com.google.android.tv",
            "com.nvidia.inputviewer",
            "com.android.tv.settings");

    // Rarely used tools that go into a "More tools" folder the first time folders are set up
    private static final List<String> DEFAULT_FOLDER_PKGS = Arrays.asList(
            "com.android.vending",
            "com.cxinventor.file.explorer",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.android.gallery3d",
            "com.google.android.tv",
            "com.nvidia.inputviewer",
            "com.android.tv.settings");

    static final class Folder {
        String id;
        String name;
        String group;
        final List<String> apps = new ArrayList<>();
    }

    // What the screen shows: installed apps only
    final List<String> dock = new ArrayList<>();
    final List<String> grid = new ArrayList<>();   // app packages and folder keys, in order
    final List<String> hidden = new ArrayList<>(); // apps not on Home (still listed in Apps)
    final Map<String, Folder> folders = new LinkedHashMap<>();

    // The saved version, including where missing apps belong
    private List<String> savedDock = new ArrayList<>();
    private List<String> savedOrder = new ArrayList<>();
    private List<String> savedHidden = new ArrayList<>();
    private Map<String, Folder> savedFolders = new LinkedHashMap<>();
    // Folders whose apps are all missing right now: not shown, but kept
    private final Set<String> waitingFolders = new HashSet<>();

    private final SharedPreferences prefs;
    private final Map<String, ResolveInfo> installed;

    Layout(SharedPreferences prefs, Map<String, ResolveInfo> installed) {
        this.prefs = prefs;
        this.installed = installed;
    }

    // ---------- Loading and saving ----------

    void load() {
        dock.clear();
        grid.clear();
        hidden.clear();
        folders.clear();
        waitingFolders.clear();

        String dockPref = prefs.getString("dock", null);
        savedDock = dockPref == null ? new ArrayList<>(Arrays.asList(DEFAULT_DOCK)) : split(dockPref);
        savedOrder = split(prefs.getString("order", ""));
        savedHidden = split(prefs.getString("hidden", ""));
        savedFolders = readFolders();

        for (String p : savedDock) if (installed.containsKey(p) && !dock.contains(p)) dock.add(p);
        for (String p : savedHidden) {
            if (installed.containsKey(p) && !dock.contains(p) && !hidden.contains(p)) hidden.add(p);
        }

        // Folders show only installed apps that aren't docked, off Home or already in another folder
        Set<String> inFolder = new HashSet<>();
        for (Folder saved : savedFolders.values()) {
            Folder f = emptyCopy(saved);
            for (String p : saved.apps) {
                if (installed.containsKey(p) && !dock.contains(p) && !hidden.contains(p) && !inFolder.contains(p)) {
                    f.apps.add(p);
                    inFolder.add(p);
                }
            }
            if (!f.apps.isEmpty()) {
                folders.put(f.id, f);
            } else if (hasMissing(saved)) {
                waitingFolders.add(saved.id);
            }
        }

        // First time with folders: put the rarely used tools into a "More tools" folder
        if (!prefs.getBoolean("folders_init", false)) {
            Folder f = new Folder();
            f.id = "f" + System.currentTimeMillis();
            f.name = "More tools";
            f.group = TOOLS;
            for (String p : DEFAULT_FOLDER_PKGS) {
                if (installed.containsKey(p) && !dock.contains(p) && !hidden.contains(p) && !inFolder.contains(p)) {
                    f.apps.add(p);
                    inFolder.add(p);
                }
            }
            if (!f.apps.isEmpty()) folders.put(f.id, f);
            prefs.edit().putBoolean("folders_init", true).apply();
        }

        for (String k : savedOrder) {
            if (grid.contains(k)) continue;
            if (isFolderKey(k)) {
                if (folderForKey(k) != null) grid.add(k);
            } else if (installed.containsKey(k) && !dock.contains(k) && !hidden.contains(k) && !inFolder.contains(k)) {
                grid.add(k);
            }
        }
        for (Folder f : folders.values()) {
            String k = keyOf(f);
            if (!grid.contains(k)) grid.add(k);
        }
        // Anything not placed yet (including newly installed apps) goes onto Home in its usual section
        for (String p : installed.keySet()) {
            if (!dock.contains(p) && !hidden.contains(p) && !inFolder.contains(p) && !grid.contains(p)) grid.add(p);
        }
        save();
    }

    void save() {
        dropEmptyFolders();
        savedDock = withMissing(dock, savedDock);
        savedOrder = withMissing(grid, savedOrder);
        savedHidden = withMissing(hidden, savedHidden);
        prefs.edit()
                .putString("dock", TextUtils.join(",", savedDock))
                .putString("order", TextUtils.join(",", savedOrder))
                .putString("hidden", TextUtils.join(",", savedHidden))
                .apply();
        saveFolders();
    }

    /**
     * Takes what's on screen and slots any missing apps (or waiting folders) back into
     * the places they had in the saved version.
     */
    private List<String> withMissing(List<String> shown, List<String> saved) {
        List<String> out = new ArrayList<>(shown);
        String prev = null;
        for (String k : saved) {
            if (out.contains(k)) {
                prev = k;
                continue;
            }
            if (!isMissing(k)) continue; // moved or removed on purpose, so don't bring it back
            int at = prev == null ? 0 : out.indexOf(prev) + 1;
            out.add(at, k);
            prev = k;
        }
        return out;
    }

    private boolean isMissing(String k) {
        if (isFolderKey(k)) return waitingFolders.contains(k.substring(FOLDER_PREFIX.length()));
        return !installed.containsKey(k);
    }

    private boolean hasMissing(Folder f) {
        for (String p : f.apps) if (!installed.containsKey(p)) return true;
        return false;
    }

    private static Folder emptyCopy(Folder from) {
        Folder f = new Folder();
        f.id = from.id;
        f.name = from.name;
        f.group = from.group;
        return f;
    }

    private Map<String, Folder> readFolders() {
        Map<String, Folder> out = new LinkedHashMap<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("folders", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Folder f = new Folder();
                f.id = str(o, "id");
                f.name = str(o, "name");
                f.group = str(o, "group");
                if (!Arrays.asList(GROUP_ORDER).contains(f.group)) f.group = TOOLS;
                if (f.name.isEmpty()) f.name = "Folder";
                JSONArray apps = o.optJSONArray("apps");
                if (apps != null) for (int k = 0; k < apps.length(); k++) f.apps.add(apps.getString(k));
                if (!f.id.isEmpty()) out.put(f.id, f);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveFolders() {
        Map<String, Folder> next = new LinkedHashMap<>();

        // Folders on screen, with any missing apps slotted back in
        for (Folder f : folders.values()) {
            Folder saved = savedFolders.get(f.id);
            Folder copy = emptyCopy(f);
            copy.apps.addAll(saved == null ? f.apps : withMissing(f.apps, saved.apps));
            next.put(copy.id, copy);
        }
        // Folders waiting for their apps to come back (only their missing apps are kept)
        for (String id : waitingFolders) {
            if (next.containsKey(id)) continue;
            Folder saved = savedFolders.get(id);
            if (saved == null) continue;
            Folder copy = emptyCopy(saved);
            for (String p : saved.apps) if (!installed.containsKey(p)) copy.apps.add(p);
            if (!copy.apps.isEmpty()) next.put(copy.id, copy);
        }

        JSONArray a = new JSONArray();
        try {
            for (Folder f : next.values()) {
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("name", f.name);
                o.put("group", f.group);
                JSONArray apps = new JSONArray();
                for (String p : f.apps) apps.put(p);
                o.put("apps", apps);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString("folders", a.toString()).apply();
        savedFolders = next;
    }

    private void dropEmptyFolders() {
        List<String> empty = new ArrayList<>();
        for (Folder f : folders.values()) if (f.apps.isEmpty()) empty.add(f.id);
        for (String id : empty) {
            folders.remove(id);
            grid.remove(FOLDER_PREFIX + id);
            // Keep it if it still has apps that are only missing for now
            Folder saved = savedFolders.get(id);
            if (saved != null && hasMissing(saved)) waitingFolders.add(id);
        }
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) {
            if (!p.trim().isEmpty()) out.add(p.trim());
        }
        return out;
    }

    // ---------- Questions ----------

    static boolean isFolderKey(String k) {
        return k != null && k.startsWith(FOLDER_PREFIX);
    }

    static String keyOf(Folder f) {
        return FOLDER_PREFIX + f.id;
    }

    Folder folderForKey(String k) {
        return isFolderKey(k) ? folders.get(k.substring(FOLDER_PREFIX.length())) : null;
    }

    Folder folderOf(String pkg) {
        for (Folder f : folders.values()) if (f.apps.contains(pkg)) return f;
        return null;
    }

    boolean isOnHome(String pkg) {
        return !hidden.contains(pkg);
    }

    String groupOf(String key) {
        Folder f = folderForKey(key);
        if (f != null) return f.group;
        if (isFolderKey(key)) return TOOLS;
        String saved = prefs.getString("group_" + key, null);
        return saved != null ? saved : defaultGroup(key);
    }

    private String defaultGroup(String pkg) {
        if (PLAY_PKGS.contains(pkg)) return PLAY;
        if (TOOLS_PKGS.contains(pkg)) return TOOLS;
        ResolveInfo ri = installed.get(pkg);
        int cat = ri != null ? ri.activityInfo.applicationInfo.category : ApplicationInfo.CATEGORY_UNDEFINED;
        if (cat == ApplicationInfo.CATEGORY_GAME) return PLAY;
        if (cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_AUDIO
                || cat == ApplicationInfo.CATEGORY_NEWS || cat == ApplicationInfo.CATEGORY_UNDEFINED) {
            return WATCH;
        }
        return TOOLS;
    }

    static String groupName(String g) {
        if (PLAY.equals(g)) return "Play";
        if (TOOLS.equals(g)) return "Tools";
        return "Watch";
    }

    List<String> keysInGroup(String group) {
        List<String> out = new ArrayList<>();
        for (String k : grid) if (groupOf(k).equals(group)) out.add(k);
        return out;
    }

    // ---------- Changes (the caller saves and redraws afterwards) ----------

    void setGroupPref(String pkg, String g) {
        prefs.edit().putString("group_" + pkg, g).apply();
    }

    void setGroup(String key, String g) {
        Folder f = folderForKey(key);
        if (f != null) {
            f.group = g;
            for (String p : f.apps) setGroupPref(p, g);
        } else {
            setGroupPref(key, g);
        }
    }

    /** Makes a new folder where the app was, puts the app in it, and returns the folder's key. */
    String createFolderWith(String pkg, String name) {
        Folder cur = folderOf(pkg);
        Folder f = new Folder();
        f.id = "f" + System.currentTimeMillis();
        f.name = name;
        f.group = cur != null ? cur.group : groupOf(pkg);
        int idx;
        if (cur != null) {
            cur.apps.remove(pkg);
            int ci = grid.indexOf(keyOf(cur));
            idx = ci < 0 ? grid.size() : ci + 1;
        } else {
            idx = grid.indexOf(pkg);
            grid.remove(pkg);
        }
        if (idx < 0 || idx > grid.size()) idx = grid.size();
        String key = keyOf(f);
        grid.add(idx, key);
        f.apps.add(pkg);
        folders.put(f.id, f);
        setGroupPref(pkg, f.group);
        return key;
    }

    void putInFolder(String pkg, Folder f) {
        Folder cur = folderOf(pkg);
        if (cur != null) cur.apps.remove(pkg);
        grid.remove(pkg);
        if (!f.apps.contains(pkg)) f.apps.add(pkg);
        setGroupPref(pkg, f.group);
    }

    void takeOutOfFolder(String pkg) {
        Folder f = folderOf(pkg);
        if (f == null) return;
        f.apps.remove(pkg);
        setGroupPref(pkg, f.group);
        int idx = grid.indexOf(keyOf(f));
        grid.add(idx < 0 ? grid.size() : idx + 1, pkg);
    }

    void removeFromFolder(String pkg) {
        Folder f = folderOf(pkg);
        if (f != null) f.apps.remove(pkg);
    }

    /** Removes the folder but keeps its apps in the section. Returns the first app, for focus. */
    String removeFolder(Folder f) {
        String key = keyOf(f);
        int idx = grid.indexOf(key);
        if (idx < 0) idx = grid.size();
        grid.remove(key);
        String first = null;
        for (String p : f.apps) {
            setGroupPref(p, f.group);
            grid.add(Math.min(idx, grid.size()), p);
            idx++;
            if (first == null) first = p;
        }
        folders.remove(f.id);
        return first;
    }

    void addToDock(String pkg) {
        removeFromFolder(pkg);
        grid.remove(pkg);
        hidden.remove(pkg);
        if (!dock.contains(pkg)) dock.add(pkg);
    }

    void removeFromDock(String pkg) {
        dock.remove(pkg);
        if (!grid.contains(pkg)) grid.add(0, pkg);
    }

    void removeFromHome(String pkg) {
        removeFromFolder(pkg);
        dock.remove(pkg);
        grid.remove(pkg);
        if (!hidden.contains(pkg)) hidden.add(pkg);
    }

    void addToHome(String pkg, String group) {
        hidden.remove(pkg);
        setGroupPref(pkg, group);
        if (!grid.contains(pkg)) grid.add(pkg);
    }

    void addToHomeFolder(String pkg, Folder f) {
        hidden.remove(pkg);
        if (!f.apps.contains(pkg)) f.apps.add(pkg);
        setGroupPref(pkg, f.group);
    }

    /** Moves an app or folder within the dock or its section. Returns true if it moved. */
    boolean moveBy(String key, int delta) {
        if (dock.contains(key)) {
            int i = dock.indexOf(key);
            int j = i + delta;
            if (j < 0 || j >= dock.size()) return false;
            dock.remove(i);
            dock.add(j, key);
            return true;
        }
        String g = groupOf(key);
        List<String> sub = keysInGroup(g);
        int i = sub.indexOf(key);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= sub.size()) return false;
        sub.remove(i);
        sub.add(j, key);

        List<String> rebuilt = new ArrayList<>();
        for (String grp : GROUP_ORDER) {
            if (grp.equals(g)) rebuilt.addAll(sub);
            else rebuilt.addAll(keysInGroup(grp));
        }
        grid.clear();
        grid.addAll(rebuilt);
        return true;
    }
}
