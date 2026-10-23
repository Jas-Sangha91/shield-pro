package com.jas.shieldhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    // Dock apps used the very first time only. After that, use Customise on the TV.
    private static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.google.android.youtube.tv",
            "ar.tvplayer.tv",
            "org.xbmc.kodi"
    };

    private static final int COLUMNS = 6;
    private static final int DOCK_W = 160, DOCK_GAP = 7;
    private static final int GRID_W = 128, GRID_H = 72, GRID_GAP = 8;
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String DEG = "\u00B0";

    private PackageManager pm;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ScrollView scroll;
    private LinearLayout content;
    private TextView titleView, hintView, weatherView, placeView;

    private final Map<String, ResolveInfo> installed = new LinkedHashMap<>();
    private final List<String> dock = new ArrayList<>();
    private final List<String> grid = new ArrayList<>();
    private final List<String> hidden = new ArrayList<>();
    private final Map<String, View> tileByPkg = new HashMap<>();
    private View firstTile;

    private String installedSignature = "";
    private boolean arranging = false;
    private String movingPkg = null;
    private boolean swallowBackUp = false;
    private List<String> backupDock = new ArrayList<>();
    private List<String> backupGrid = new ArrayList<>();
    private volatile long lastWeatherFetch = 0;

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - lastWeatherFetch >= WEATHER_EVERY_MS) fetchWeather();
            handler.postDelayed(this, 5 * 60 * 1000L);
        }
    };

    // ---------- Lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInstalled();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (arranging || movingPkg != null) {
            if (movingPkg != null) finishMove(false);
            arranging = false;
            render(null);
        }
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (arranging) exitArrange();
        // Otherwise this is the home screen, so Back does nothing.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (swallowBackUp && e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            swallowBackUp = false;
            return true;
        }
        if (movingPkg != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean inDock = dock.contains(movingPkg);
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (!inDock) moveBy(-COLUMNS); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (!inDock) moveBy(COLUMNS); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER: finishMove(true); return true;
                case KeyEvent.KEYCODE_BACK: swallowBackUp = true; finishMove(false); return true;
                default: break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------- Screen ----------

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2130, 0xFF07080C}));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Fixed header: selected app name on the left, clock and weather on the right
        FrameLayout header = new FrameLayout(this);
        header.setPadding(dp(40), dp(28), dp(48), dp(4));
        column.addView(header, new LinearLayout.LayoutParams(MATCH, dp(176)));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(this);
        style(titleView, 40, true, 0xFFFFFFFF);
        titleView.setSingleLine(true);
        titleView.setText(" ");
        hintView = new TextView(this);
        style(hintView, 14, false, 0xFFFFD54F);
        hintView.setVisibility(View.GONE);
        left.addView(titleView);
        left.addView(hintView);
        header.addView(left, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.END);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("HH:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 34, true, 0xFFFFFFFF);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        style(date, 14, false, 0xA0FFFFFF);

        weatherView = new TextView(this);
        style(weatherView, 18, false, 0xFFFFFFFF);
        weatherView.setPadding(0, dp(10), 0, 0);

        placeView = new TextView(this);
        style(placeView, 12, false, 0x99FFFFFF);

        TextView[] lines = {time, date, weatherView, placeView};
        for (TextView tv : lines) {
            tv.setGravity(Gravity.END);
            info.addView(tv);
        }
        header.addView(info, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END));

        // Scrolling area underneath the header
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(28));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(6), dp(32), dp(60));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        column.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
        showWeatherPlaceholder();
    }

    private void refreshInstalled() {
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(tv, 0)) {
            found.putIfAbsent(ri.activityInfo.packageName, ri);
        }
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phone, 0)) {
            found.putIfAbsent(ri.activityInfo.packageName, ri);
        }
        found.remove(getPackageName());

        List<String> names = new ArrayList<>(found.keySet());
        final Map<String, String> labels = new HashMap<>();
        for (String p : names) labels.put(p, found.get(p).loadLabel(pm).toString());
        Collections.sort(names, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));

        String sig = TextUtils.join(";", names);
        if (sig.equals(installedSignature)) return;
        installedSignature = sig;

        installed.clear();
        for (String p : names) installed.put(p, found.get(p));
        loadLayout();
        render(null);
    }

    private void render(String focusPkg) {
        content.removeAllViews();
        tileByPkg.clear();
        firstTile = null;

        hintView.setText(movingPkg != null
                ? "Moving: use the arrows, then OK to drop it. Back cancels."
                : "Arrange mode: press OK on an app to move, dock or hide it. Press Back when finished.");
        hintView.setVisibility(arranging ? View.VISIBLE : View.GONE);

        // Dock (tiles shrink a little if there are lots of apps, so it always fits)
        if (!dock.isEmpty()) {
            LinearLayout band = new LinearLayout(this);
            band.setOrientation(LinearLayout.HORIZONTAL);
            band.setGravity(Gravity.CENTER_VERTICAL);
            band.setPadding(dp(12), dp(10), dp(12), dp(10));
            band.setClipChildren(false);
            band.setClipToPadding(false);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x1FFFFFFF);
            bg.setCornerRadius(dp(24));
            band.setBackground(bg);

            int avail = getResources().getConfiguration().screenWidthDp - 64 - 24;
            int w = Math.min(DOCK_W, avail / dock.size() - 2 * DOCK_GAP);
            if (w < 110) w = 110;
            int h = w * 9 / 16;
            for (String p : dock) band.addView(appTile(p), size(w, h, DOCK_GAP));

            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.addView(band, new FrameLayout.LayoutParams(WRAP, WRAP));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(WRAP, WRAP);
            dlp.topMargin = dp(4);
            content.addView(hs, dlp);
        }

        // All apps
        addHeader("All apps");
        List<View> gridTiles = new ArrayList<>();
        for (String p : grid) gridTiles.add(appTile(p));
        addRows(gridTiles);

        // Settings and Customise
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, dp(12)));
        List<View> tools = new ArrayList<>();
        tools.add(tile("Settings", null, null, false,
                () -> startSafe(new Intent(Settings.ACTION_SETTINGS))));
        tools.add(tile(arranging ? "Done arranging" : "Customise", null, null, false,
                this::openCustomise));
        addRows(tools);

        // Hidden apps
        if (!hidden.isEmpty()) {
            addHeader("Hidden apps");
            List<View> hiddenTiles = new ArrayList<>();
            for (String p : hidden) {
                View t = appTile(p);
                t.setAlpha(0.5f);
                hiddenTiles.add(t);
            }
            addRows(hiddenTiles);
        }

        View target = focusPkg != null ? tileByPkg.get(focusPkg) : null;
        if (target == null) target = firstTile;
        if (target != null) target.requestFocus();
    }

    private void addHeader(String text) {
        TextView h = new TextView(this);
        style(h, 16, true, 0x99FFFFFF);
        h.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(28);
        lp.bottomMargin = dp(4);
        lp.leftMargin = dp(12);
        content.addView(h, lp);
    }

    private void addRows(List<View> views) {
        LinearLayout row = null;
        for (int i = 0; i < views.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(12), 0, dp(12), 0);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                content.addView(row, new LinearLayout.LayoutParams(WRAP, WRAP));
            }
            row.addView(views.get(i), size(GRID_W, GRID_H, GRID_GAP));
        }
    }

    private View appTile(final String pkg) {
        ResolveInfo ri = installed.get(pkg);
        String label = ri.loadLabel(pm).toString();
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        View t = tile(label, banner, ri.loadIcon(pm), pkg.equals(movingPkg), () -> onAppClicked(pkg));
        tileByPkg.put(pkg, t);
        if (firstTile == null) firstTile = t;
        return t;
    }

    private View tile(final String label, Drawable banner, Drawable icon, boolean moving, final Runnable onClick) {
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xFF2B3140);
        t.setBackground(bg);
        t.setClipToOutline(true);

        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            t.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            if (icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                col.addView(iv, new LinearLayout.LayoutParams(dp(32), dp(32)));
            }
            TextView tv = new TextView(this);
            style(tv, 12, false, 0xFFFFFFFF);
            tv.setText(label);
            tv.setSingleLine(true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(6), dp(4), dp(6), 0);
            col.addView(tv, new LinearLayout.LayoutParams(MATCH, WRAP));
            t.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        }

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(moving ? 4 : 3), moving ? 0xFFFFD54F : 0xFFFFFFFF);
        ring.setColor(0x00000000);

        t.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.12f : 1.0f;
            v.animate().scaleX(s).scaleY(s)
                    .translationZ(hasFocus ? dp(10) : 0)
                    .setDuration(150).start();
            ((FrameLayout) v).setForeground(hasFocus ? ring : null);
            if (hasFocus && titleView != null) titleView.setText(label);
        });
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    // ---------- Arranging ----------

    private void loadLayout() {
        dock.clear();
        grid.clear();
        hidden.clear();
        String savedDock = prefs.getString("dock", null);
        List<String> d = savedDock == null ? Arrays.asList(DEFAULT_DOCK) : split(savedDock);
        for (String p : d) if (installed.containsKey(p) && !dock.contains(p)) dock.add(p);
        for (String p : split(prefs.getString("hidden", ""))) {
            if (installed.containsKey(p) && !dock.contains(p) && !hidden.contains(p)) hidden.add(p);
        }
        for (String p : split(prefs.getString("order", ""))) {
            if (installed.containsKey(p) && !dock.contains(p) && !hidden.contains(p) && !grid.contains(p)) grid.add(p);
        }
        for (String p : installed.keySet()) {
            if (!dock.contains(p) && !hidden.contains(p) && !grid.contains(p)) grid.add(p);
        }
    }

    private void saveLayout() {
        prefs.edit()
                .putString("dock", TextUtils.join(",", dock))
                .putString("order", TextUtils.join(",", grid))
                .putString("hidden", TextUtils.join(",", hidden))
                .apply();
    }

    private void saveAndRender(String focusPkg) {
        saveLayout();
        render(focusPkg);
    }

    private void onAppClicked(String pkg) {
        if (arranging) showAppMenu(pkg);
        else launch(pkg);
    }

    private void openCustomise() {
        if (arranging) {
            exitArrange();
            return;
        }
        String[] opts = {"Arrange apps", "Weather location", "Cancel"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Customise")
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        arranging = true;
                        render(null);
                    } else if (which == 1) {
                        askWeatherPlace();
                    }
                })
                .show();
    }

    private void exitArrange() {
        arranging = false;
        movingPkg = null;
        render(null);
    }

    private void showAppMenu(final String pkg) {
        final boolean isDock = dock.contains(pkg);
        final boolean isHidden = hidden.contains(pkg);
        final List<String> opts = new ArrayList<>();
        if (!isHidden) opts.add("Move");
        opts.add(isDock ? "Remove from dock" : "Add to dock");
        opts.add(isHidden ? "Unhide" : "Hide");
        opts.add("Cancel");
        String label = installed.get(pkg).loadLabel(pm).toString();

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(label)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String o = opts.get(which);
                    if (o.equals("Move")) {
                        startMove(pkg);
                    } else if (o.equals("Add to dock")) {
                        grid.remove(pkg);
                        hidden.remove(pkg);
                        dock.add(pkg);
                        saveAndRender(pkg);
                    } else if (o.equals("Remove from dock")) {
                        dock.remove(pkg);
                        grid.add(0, pkg);
                        saveAndRender(pkg);
                    } else if (o.equals("Hide")) {
                        dock.remove(pkg);
                        grid.remove(pkg);
                        hidden.add(pkg);
                        saveAndRender(pkg);
                    } else if (o.equals("Unhide")) {
                        hidden.remove(pkg);
                        grid.add(pkg);
                        saveAndRender(pkg);
                    }
                })
                .show();
    }

    private void startMove(String pkg) {
        movingPkg = pkg;
        backupDock = new ArrayList<>(dock);
        backupGrid = new ArrayList<>(grid);
        render(pkg);
    }

    private void finishMove(boolean keep) {
        if (!keep) {
            dock.clear();
            dock.addAll(backupDock);
            grid.clear();
            grid.addAll(backupGrid);
        }
        String p = movingPkg;
        movingPkg = null;
        if (keep) saveLayout();
        render(p);
    }

    private void moveBy(int delta) {
        List<String> list = dock.contains(movingPkg) ? dock : grid;
        int i = list.indexOf(movingPkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= list.size()) return;
        list.remove(i);
        list.add(j, movingPkg);
        render(movingPkg);
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split(",")) {
            if (!p.trim().isEmpty()) out.add(p.trim());
        }
        return out;
    }

    // ---------- Weather (data by Open-Meteo.com) ----------

    private void showWeatherPlaceholder() {
        if (prefs.contains("wx_lat")) {
            placeView.setText(prefs.getString("wx_name", ""));
        } else {
            weatherView.setText("");
            placeView.setText("Set weather in Customise");
        }
    }

    private void askWeatherPlace() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setHint("Town or city");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Weather location")
                .setMessage("Weather data by Open-Meteo.com")
                .setView(box)
                .setPositiveButton("Search", (d, w) -> searchPlace(input.getText().toString().trim()))
                .setNeutralButton("Remove weather", (d, w) -> {
                    prefs.edit().remove("wx_lat").remove("wx_lon").remove("wx_name").apply();
                    lastWeatherFetch = 0;
                    showWeatherPlaceholder();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void searchPlace(final String q) {
        if (q.isEmpty()) return;
        new Thread(() -> {
            try {
                String url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name="
                        + URLEncoder.encode(q, "UTF-8");
                JSONObject o = new JSONObject(httpGet(url));
                JSONArray res = o.optJSONArray("results");
                final List<String> names = new ArrayList<>();
                final List<String> shortNames = new ArrayList<>();
                final List<double[]> coords = new ArrayList<>();
                if (res != null) {
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject r = res.getJSONObject(i);
                        String name = r.optString("name", "");
                        String admin = r.optString("admin1", "");
                        String country = r.optString("country", "");
                        StringBuilder sb = new StringBuilder(name);
                        if (!admin.isEmpty()) sb.append(", ").append(admin);
                        if (!country.isEmpty()) sb.append(", ").append(country);
                        names.add(sb.toString());
                        shortNames.add(name);
                        coords.add(new double[]{r.getDouble("latitude"), r.getDouble("longitude")});
                    }
                }
                runOnUiThread(() -> {
                    if (names.isEmpty()) {
                        Toast.makeText(this, "No places found. Try another spelling.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Which one?")
                            .setItems(names.toArray(new String[0]), (d, w) -> {
                                prefs.edit()
                                        .putFloat("wx_lat", (float) coords.get(w)[0])
                                        .putFloat("wx_lon", (float) coords.get(w)[1])
                                        .putString("wx_name", shortNames.get(w))
                                        .apply();
                                showWeatherPlaceholder();
                                fetchWeather();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Couldn't search right now. Check the internet connection.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void fetchWeather() {
        if (!prefs.contains("wx_lat")) {
            showWeatherPlaceholder();
            return;
        }
        final float lat = prefs.getFloat("wx_lat", 0f);
        final float lon = prefs.getFloat("wx_lon", 0f);
        lastWeatherFetch = System.currentTimeMillis();
        new Thread(() -> {
            try {
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=1";
                JSONObject o = new JSONObject(httpGet(url));
                JSONObject cur = o.getJSONObject("current");
                JSONObject day = o.getJSONObject("daily");
                final long now = Math.round(cur.getDouble("temperature_2m"));
                final int code = cur.getInt("weather_code");
                final long hi = Math.round(day.getJSONArray("temperature_2m_max").getDouble(0));
                final long lo = Math.round(day.getJSONArray("temperature_2m_min").getDouble(0));
                runOnUiThread(() -> {
                    weatherView.setText(symbol(code) + "  " + now + DEG + "    H " + hi + DEG + "  L " + lo + DEG);
                    placeView.setText(prefs.getString("wx_name", "") + "  \u00B7  " + describe(code));
                });
            } catch (Exception ex) {
                lastWeatherFetch = 0; // try again on the next check
            }
        }).start();
    }

    private static String httpGet(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String symbol(int c) {
        if (c == 0) return "\u2600\uFE0F";
        if (c == 1) return "\uD83C\uDF24\uFE0F";
        if (c == 2) return "\u26C5";
        if (c == 3) return "\u2601\uFE0F";
        if (c == 45 || c == 48) return "\uD83C\uDF2B\uFE0F";
        if (c >= 51 && c <= 57) return "\uD83C\uDF26\uFE0F";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "\uD83C\uDF27\uFE0F";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "\uD83C\uDF28\uFE0F";
        if (c >= 95) return "\u26C8\uFE0F";
        return "\uD83C\uDF21\uFE0F";
    }

    private static String describe(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorms";
        return "";
    }

    // ---------- Helpers ----------

    private void launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) startSafe(i);
    }

    private void startSafe(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private LinearLayout.LayoutParams size(int w, int h, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(h));
        lp.setMargins(dp(gap), dp(gap), dp(gap), dp(gap));
        return lp;
    }

    private void style(TextView tv, int sp, boolean bold, int color) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
