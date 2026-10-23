package com.jas.shieldhome;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Backup and restore of everything Sangha Home remembers: dock, sections, folders,
 * apps on Home, My list, recent searches, weather and clock settings.
 * The file lives in Android/data/com.jas.shieldhome/files/ on the Shield.
 */
final class Backup {

    static final String FILE_NAME = "sangha-home-backup.json";

    private final MainActivity a;

    Backup(MainActivity a) {
        this.a = a;
    }

    private File file() {
        File dir = a.getExternalFilesDir(null);
        if (dir == null) dir = a.getFilesDir();
        return new File(dir, FILE_NAME);
    }

    private String when(File f) {
        return new SimpleDateFormat("EEEE d MMMM, HH:mm", Locale.UK).format(new Date(f.lastModified()));
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void toast(String text) {
        Toast.makeText(a, text, Toast.LENGTH_LONG).show();
    }

    void openMenu() {
        File f = file();
        String last = f.exists() ? "Last backup: " + when(f) : "No backup yet.";
        String msg = last
                + "\n\nSaves your dock, sections, folders, which apps are on Home, My list, "
                + "recent searches, weather and clock settings."
                + "\n\nThe backup is kept on the Shield. To keep a copy on your Mac as well, see the guide.";
        dialog().setTitle("Backup and restore")
                .setMessage(msg)
                .setPositiveButton("Back up now", (d, w) -> backUp())
                .setNeutralButton("Restore", (d, w) -> confirmRestore())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void backUp() {
        try {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> e : a.prefs.getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof String) {
                    item.put("t", "s");
                    item.put("v", v);
                } else if (v instanceof Boolean) {
                    item.put("t", "b");
                    item.put("v", v);
                } else if (v instanceof Integer) {
                    item.put("t", "i");
                    item.put("v", v);
                } else if (v instanceof Long) {
                    item.put("t", "l");
                    item.put("v", v);
                } else if (v instanceof Float) {
                    item.put("t", "f");
                    item.put("v", ((Float) v).doubleValue());
                } else if (v instanceof Set) {
                    JSONArray arr = new JSONArray();
                    for (Object o : (Set<?>) v) arr.put(String.valueOf(o));
                    item.put("t", "set");
                    item.put("v", arr);
                } else {
                    continue;
                }
                values.put(e.getKey(), item);
            }

            JSONObject root = new JSONObject();
            root.put("app", "Sangha Home");
            root.put("version", 1);
            root.put("saved", System.currentTimeMillis());
            root.put("prefs", values);

            try (OutputStream out = new FileOutputStream(file())) {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            toast("Backed up. Your layout and settings are saved on the Shield.");
        } catch (Exception ex) {
            toast("Couldn't back up right now.");
        }
    }

    private void confirmRestore() {
        File f = file();
        if (!f.exists()) {
            toast("No backup found. Choose Back up now first.");
            return;
        }
        dialog().setTitle("Restore backup?")
                .setMessage("This replaces your current layout and settings with the backup from " + when(f) + ".")
                .setPositiveButton("Restore", (d, w) -> restore())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restore() {
        try {
            byte[] data;
            try (InputStream in = new FileInputStream(file());
                 ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
                data = buf.toByteArray();
            }

            JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
            JSONObject values = root.getJSONObject("prefs");
            SharedPreferences.Editor ed = a.prefs.edit().clear();
            Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject item = values.getJSONObject(k);
                String t = item.optString("t", "");
                switch (t) {
                    case "s":
                        ed.putString(k, item.getString("v"));
                        break;
                    case "b":
                        ed.putBoolean(k, item.getBoolean("v"));
                        break;
                    case "i":
                        ed.putInt(k, item.getInt("v"));
                        break;
                    case "l":
                        ed.putLong(k, item.getLong("v"));
                        break;
                    case "f":
                        ed.putFloat(k, (float) item.getDouble("v"));
                        break;
                    case "set": {
                        JSONArray arr = item.getJSONArray("v");
                        Set<String> s = new HashSet<>();
                        for (int i = 0; i < arr.length(); i++) s.add(arr.getString(i));
                        ed.putStringSet(k, s);
                        break;
                    }
                    default:
                        break;
                }
            }
            ed.commit();
            a.reloadAfterRestore();
            toast("Restored.");
        } catch (Exception ex) {
            toast("Couldn't restore. The backup file may be damaged.");
        }
    }
}
