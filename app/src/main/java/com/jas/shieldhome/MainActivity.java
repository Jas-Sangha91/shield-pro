package com.jas.shieldhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MainActivity extends Activity {

    // Dock apps used the very first time only. After that, use the settings cog on the TV.
    private static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.google.android.youtube.tv",
            "ar.tvplayer.tv"
    };

    private static final String VPN_APP = "com.surfshark.vpnclient.android";

    // Groups
    private static final String WATCH = "watch", PLAY = "play", TOOLS = "tools";
    private static final String[] GROUP_ORDER = {WATCH, PLAY, TOOLS};

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
            "org.videolan.vlc",
            "com.android.vending",
            "com.android.gallery3d",
            "com.google.android.tv",
            "com.nvidia.inputviewer",
            "com.plexapp.android",
            "com.android.tv.settings");

    private static final int COLUMNS = 5;
    private static final int DOCK_W = 176, DOCK_GAP = 7;
    private static final int GRID_W = 152, GRID_H = 86, GRID_GAP = 8;
    private static final int FOLDER_COLUMNS = 4;
    private static final int FOLDER_W = 128, FOLDER_H = 72;
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;
    private static final long VPN_EVERY_MS = 15 * 60 * 1000L;
    private static final long IDLE_AFTER_MS = 20 * 1000L;
    private static final int NEUTRAL_GLOW = 0xFF3A4A6B;
    private static final int VPN_ON = 0xFF7BD88F;
    private static final int VPN_OFF = 0xFFFFB74D;
    private static final int SOFT_TEXT = 0xB0FFFFFF;
    private static final int ICON_IDLE = 0xFFC9CDD6;
    private static final int DARK = 0xFF141A26;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String DEG = "\u00B0";
    private static final String DOT = "\u00B7";

    private PackageManager pm;
    private SharedPreferences prefs;
    private ConnectivityManager cm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ScrollView scroll;
    private LinearLayout content;
    private TextView titleView, hintView, weatherView, placeView;
    private TextView tabHome, tabDiscover;
    private FrameLayout vpnBtn, cogBtn;
    private GradientDrawable vpnDotBg;
    private GradientDrawable baseBg, glow;
    private int glowColor = 0x00000000;
    private ValueAnimator glowAnim;

    private final Map<String, ResolveInfo> installed = new LinkedHashMap<>();
    private final List<String> dock = new ArrayList<>();
    private final List<String> grid = new ArrayList<>();
    private final List<String> hidden = new ArrayList<>();
    private final Map<String, View> tileByPkg = new HashMap<>();
    private final Map<String, Integer> colorByPkg = new HashMap<>();
    private final List<View> allTiles = new ArrayList<>();
    private final Map<View, Float> baseAlpha = new HashMap<>();
    private View firstTile;

    private String installedSignature = "";
    private boolean onDiscover = false;
    private boolean arranging = false;
    private String movingPkg = null;
    private boolean swallowBackUp = false;
    private boolean idle = false;
    private List<String> backupDock = new ArrayList<>();
    private List<String> backupGrid = new ArrayList<>();
    private volatile long lastWeatherFetch = 0;

    private ConnectivityManager.NetworkCallback netCallback;
    private Boolean lastVpnState = null;
    private String lastWhere = null;
    private long lastIpFetch = 0;

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - lastWeatherFetch >= WEATHER_EVERY_MS) fetchWeather();
            if (baseBg != null) baseBg.setColors(timeOfDayColors());
            handler.postDelayed(this, 5 * 60 * 1000L);
        }
    };

    private final Runnable vpnTick = new Runnable() {
        @Override
        public void run() {
            updateVpn(false);
            handler.postDelayed(this, VPN_EVERY_MS);
        }
    };

    private final Runnable idleRunnable = new Runnable() {
        @Override
        public void run() {
            goIdle();
        }
    };

    // ---------- Lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        baseBg.setColors(timeOfDayColors());
        refreshInstalled();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
        registerNet();
        handler.removeCallbacks(vpnTick);
        handler.post(vpnTick);
        resetIdle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(vpnTick);
        handler.removeCallbacks(idleRunnable);
        unregisterNet();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (movingPkg != null) finishMove(false);
        if (arranging || onDiscover) {
            arranging = false;
            onDiscover = false;
            styleTabs();
            render(null);
        }
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (arranging) {
            exitArrange();
        } else if (onDiscover) {
            showPage(false);
            if (tabHome != null) tabHome.requestFocus();
        }
        // Otherwise this is the home screen, so Back does nothing.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) resetIdle();
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
        baseBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, timeOfDayColors());
        root.setBackground(baseBg);

        // Soft glow that takes the colour of the selected app
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.15f, 0.2f);
        glow.setGradientRadius(dp(620));
        glow.setColors(new int[]{0x00000000, 0x00000000});
        View glowView = new View(this);
        glowView.setBackground(glow);
        root.addView(glowView, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        FrameLayout header = new FrameLayout(this);
        header.setPadding(dp(40), dp(22), dp(48), dp(4));
        header.setClipChildren(false);
        header.setClipToPadding(false);
        column.addView(header, new LinearLayout.LayoutParams(MATCH, dp(176)));

        // Top left: tabs, then weather
        LinearLayout topLeft = new LinearLayout(this);
        topLeft.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabHome = makeTab("Home", false);
        tabDiscover = makeTab("Discover", true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp.rightMargin = dp(8);
        tabs.addView(tabHome, tlp);
        tabs.addView(tabDiscover);
        topLeft.addView(tabs);

        LinearLayout weatherRow = new LinearLayout(this);
        weatherRow.setOrientation(LinearLayout.HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherRow.setPadding(dp(6), dp(12), 0, 0);
        weatherView = new TextView(this);
        style(weatherView, 18, false, 0xFFFFFFFF);
        placeView = new TextView(this);
        style(placeView, 16, false, SOFT_TEXT);
        placeView.setPadding(dp(10), 0, 0, 0);
        weatherRow.addView(weatherView);
        weatherRow.addView(placeView);
        topLeft.addView(weatherRow);

        header.addView(topLeft, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START));

        // Bottom left: selected item name (and arrange hint)
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(this);
        style(titleView, 40, true, 0xFFFFFFFF);
        titleView.setSingleLine(true);
        titleView.setText(" ");
        hintView = new TextView(this);
        style(hintView, 16, false, 0xFFFFD54F);
        hintView.setVisibility(View.GONE);
        left.addView(titleView);
        left.addView(hintView);
        header.addView(left, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START));

        // Top right: VPN button, settings cog, clock
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setClipChildren(false);

        vpnBtn = roundButton(R.drawable.ic_shield, this::vpnTitle, this::openVpnApp);
        vpnDotBg = new GradientDrawable();
        vpnDotBg.setShape(GradientDrawable.OVAL);
        vpnDotBg.setColor(VPN_OFF);
        vpnDotBg.setStroke(dp(2), DARK);
        View vpnDot = new View(this);
        vpnDot.setBackground(vpnDotBg);
        vpnBtn.addView(vpnDot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.BOTTOM | Gravity.END));

        cogBtn = roundButton(R.drawable.ic_cog, () -> "Settings", this::openSettingsMenu);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(30), dp(30));
        blp.topMargin = dp(8);
        blp.rightMargin = dp(10);
        right.addView(vpnBtn, blp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(30), dp(30));
        clp.topMargin = dp(8);
        clp.rightMargin = dp(18);
        right.addView(cogBtn, clp);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("HH:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        time.setTextColor(0xFFFFFFFF);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setGravity(Gravity.END);
        time.setIncludeFontPadding(false);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        style(date, 18, false, SOFT_TEXT);
        date.setGravity(Gravity.END);
        date.setPadding(0, dp(4), 0, 0);
        clock.addView(time);
        clock.addView(date);
        right.addView(clock);

        header.addView(right, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END));

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
        styleTabs();
        showWeatherPlaceholder();
    }

    private TextView makeTab(final String label, final boolean discover) {
        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setPadding(dp(16), dp(5), dp(16), dp(6));
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setOnFocusChangeListener((v, f) -> {
            styleTabs();
            if (f) {
                titleView.setText(label);
                setGlow(NEUTRAL_GLOW);
            }
        });
        tv.setOnClickListener(v -> showPage(discover));
        return tv;
    }

    private void styleTabs() {
        if (tabHome == null || tabDiscover == null) return;
        styleTab(tabHome, !onDiscover);
        styleTab(tabDiscover, onDiscover);
    }

    private void styleTab(TextView tv, boolean active) {
        boolean focused = tv.isFocused();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (focused) bg.setColor(0xFFFFFFFF);
        else if (active) bg.setColor(0x26FFFFFF);
        else bg.setColor(0x00000000);
        tv.setBackground(bg);
        tv.setTextColor(focused ? DARK : (active ? 0xFFFFFFFF : SOFT_TEXT));
        tv.setTypeface(Typeface.create("sans-serif", (active || focused) ? Typeface.BOLD : Typeface.NORMAL));
    }

    private void showPage(boolean discover) {
        if (onDiscover == discover) return;
        onDiscover = discover;
        arranging = false;
        movingPkg = null;
        styleTabs();
        render(null, false);
    }

    private FrameLayout roundButton(int iconRes, final Supplier<String> title, final Runnable onClick) {
        final FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x14FFFFFF);
        b.setBackground(bg);

        final Drawable icon = getDrawable(iconRes).mutate();
        icon.setTint(ICON_IDLE);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        b.addView(iv, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER));

        b.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x14FFFFFF);
            icon.setTint(f ? DARK : ICON_IDLE);
            v.animate().scaleX(f ? 1.15f : 1f).scaleY(f ? 1.15f : 1f).setDuration(150).start();
            if (f) {
                titleView.setText(title.get());
                setGlow(NEUTRAL_GLOW);
            }
        });
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private static int[] timeOfDayColors() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 10) return new int[]{0xFF1F2B3D, 0xFF080B12};   // morning
        if (h >= 10 && h < 17) return new int[]{0xFF1B2130, 0xFF07080C};  // day
        if (h >= 17 && h < 21) return new int[]{0xFF2A1F33, 0xFF0A070E};  // evening
        return new int[]{0xFF10141F, 0xFF030407};                          // night
    }

    private void setGlow(int color) {
        int target = (color & 0x00FFFFFF) | 0x55000000;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofArgb(glowColor, target);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            glow.setColors(new int[]{glowColor, glowColor & 0x00FFFFFF});
        });
        glowAnim.start();
    }

    private int colorFor(String pkg) {
        Integer cached = colorByPkg.get(pkg);
        if (cached != null) return cached;
        int result = NEUTRAL_GLOW;
        try {
            ResolveInfo ri = installed.get(pkg);
            Drawable d = ri.activityInfo.loadBanner(pm);
            if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
            if (d == null) d = ri.loadIcon(pm);
            if (d != null) result = dominantColor(d, NEUTRAL_GLOW);
        } catch (Exception ignored) {
        }
        colorByPkg.put(pkg, result);
        return result;
    }

    private static int dominantColor(Drawable src, int fallback) {
        Drawable d = src.getConstantState() != null ? src.getConstantState().newDrawable().mutate() : src;
        Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 32, 18);
        d.draw(c);
        float[] hsv = new float[3];
        float[] weight = new float[12];
        float[][] sum = new float[12][3];
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 32; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                int bucket = Math.min(11, (int) (hsv[0] / 30f));
                float w = hsv[1] * hsv[2];
                weight[bucket] += w;
                sum[bucket][0] += Color.red(p) * w;
                sum[bucket][1] += Color.green(p) * w;
                sum[bucket][2] += Color.blue(p) * w;
            }
        }
        b.recycle();
        int best = 0;
        for (int i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] < 3f) return fallback;
        float w = weight[best];
        return Color.rgb((int) (sum[best][0] / w), (int) (sum[best][1] / w), (int) (sum[best][2] / w));
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
        colorByPkg.clear();
        for (String p : names) installed.put(p, found.get(p));
        loadLayout();
        render(null);
    }

    private void render(String focusPkg) {
        render(focusPkg, true);
    }

    private void render(String focusPkg, boolean takeFocus) {
        content.removeAllViews();
        tileByPkg.clear();
        allTiles.clear();
        baseAlpha.clear();
        idle = false;
        firstTile = null;

        hintView.setText(movingPkg != null
                ? "Moving: use the arrows, then OK to drop it. Back cancels."
                : "Arrange mode: press OK on an app to move, group, dock or hide it. Press Back when finished.");
        hintView.setVisibility(arranging ? View.VISIBLE : View.GONE);

        if (onDiscover) {
            renderDiscover();
            return;
        }

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

        // Watch and Play groups
        addGroupSection("Watch", WATCH);
        addGroupSection("Play", PLAY);

        // Tools folder
        final List<String> toolPkgs = pkgsInGroup(TOOLS);
        if (!toolPkgs.isEmpty()) {
            addHeader("Tools");
            List<View> tools = new ArrayList<>();
            tools.add(register(folderTile(toolPkgs)));
            addRows(content, tools, COLUMNS, GRID_W, GRID_H, GRID_GAP);
        }

        // Hidden apps
        if (!hidden.isEmpty()) {
            addHeader("Hidden apps");
            List<View> hiddenTiles = new ArrayList<>();
            for (String p : hidden) {
                View t = appTile(p);
                t.setAlpha(0.5f);
                baseAlpha.put(t, 0.5f);
                hiddenTiles.add(t);
            }
            addRows(content, hiddenTiles, COLUMNS, GRID_W, GRID_H, GRID_GAP);
        }

        if (takeFocus) {
            View target = focusPkg != null ? tileByPkg.get(focusPkg) : null;
            if (target == null) target = firstTile;
            if (target != null) target.requestFocus();
        }
    }

    private void renderDiscover() {
        TextView a = new TextView(this);
        style(a, 26, true, 0xFFFFFFFF);
        a.setText("Discover is on its way");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(WRAP, WRAP);
        alp.topMargin = dp(40);
        alp.leftMargin = dp(12);
        content.addView(a, alp);

        TextView b = new TextView(this);
        style(b, 16, false, SOFT_TEXT);
        b.setText("Trending and top rated films and series will appear here once Discover is set up.");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(WRAP, WRAP);
        blp.topMargin = dp(10);
        blp.leftMargin = dp(12);
        content.addView(b, blp);
    }

    private void addGroupSection(String title, String group) {
        List<String> pkgs = pkgsInGroup(group);
        if (pkgs.isEmpty()) return;
        addHeader(title);
        List<View> tiles = new ArrayList<>();
        for (String p : pkgs) tiles.add(appTile(p));
        addRows(content, tiles, COLUMNS, GRID_W, GRID_H, GRID_GAP);
    }

    private List<String> pkgsInGroup(String group) {
        List<String> out = new ArrayList<>();
        for (String p : grid) if (groupOf(p).equals(group)) out.add(p);
        return out;
    }

    private void addHeader(String text) {
        TextView h = new TextView(this);
        style(h, 18, true, 0xA0FFFFFF);
        h.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(4);
        lp.leftMargin = dp(12);
        content.addView(h, lp);
    }

    private void addRows(LinearLayout parent, List<View> views, int cols, int w, int h, int gap) {
        LinearLayout row = null;
        for (int i = 0; i < views.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(12), 0, dp(12), 0);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                parent.addView(row, new LinearLayout.LayoutParams(WRAP, WRAP));
            }
            row.addView(views.get(i), size(w, h, gap));
        }
    }

    private View register(View t) {
        allTiles.add(t);
        baseAlpha.put(t, 1f);
        return t;
    }

    private View appTile(final String pkg) {
        View t = buildAppTile(pkg, () -> onAppClicked(pkg));
        tileByPkg.put(pkg, t);
        if (firstTile == null) firstTile = t;
        return register(t);
    }

    private View buildAppTile(String pkg, Runnable onClick) {
        ResolveInfo ri = installed.get(pkg);
        String label = ri.loadLabel(pm).toString();
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        return tile(label, banner, ri.loadIcon(pm), pkg.equals(movingPkg), pkg, onClick);
    }

    private View folderTile(final List<String> pkgs) {
        FrameLayout t = (FrameLayout) tile("Tools", null, null, false, null, () -> openTools(pkgs));
        t.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        LinearLayout row1 = new LinearLayout(this);
        LinearLayout row2 = new LinearLayout(this);
        for (int i = 0; i < Math.min(6, pkgs.size()); i++) {
            ResolveInfo ri = installed.get(pkgs.get(i));
            Drawable d = ri.activityInfo.loadBanner(pm);
            if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
            if (d == null) d = ri.loadIcon(pm);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(18));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            (i < 3 ? row1 : row2).addView(iv, lp);
        }
        col.addView(row1);
        col.addView(row2);

        TextView label = new TextView(this);
        style(label, 13, false, 0xFFFFFFFF);
        label.setText("Tools (" + pkgs.size() + ")");
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(4), 0, 0);
        col.addView(label);

        t.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        return t;
    }

    private void openTools(List<String> pkgs) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(12), dp(8), dp(12));
        box.setClipChildren(false);
        box.setClipToPadding(false);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        final AlertDialog[] holder = new AlertDialog[1];
        List<View> views = new ArrayList<>();
        for (final String p : pkgs) {
            views.add(buildAppTile(p, () -> {
                if (holder[0] != null) holder[0].dismiss();
                onAppClicked(p);
            }));
        }
        addRows(box, views, FOLDER_COLUMNS, FOLDER_W, FOLDER_H, GRID_GAP);

        holder[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(arranging ? "Tools: press OK on an app to change it" : "Tools")
                .setView(sv)
                .setNegativeButton("Close", null)
                .create();
        holder[0].show();
        if (holder[0].getWindow() != null) holder[0].getWindow().setLayout(dp(700), WRAP);
    }

    private View tile(final String label, Drawable banner, Drawable icon, boolean moving,
                      final String colorPkg, final Runnable onClick) {
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
                col.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
            }
            TextView tv = new TextView(this);
            style(tv, 13, false, 0xFFFFFFFF);
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
            if (hasFocus) {
                if (titleView != null) titleView.setText(label);
                setGlow(colorPkg != null ? colorFor(colorPkg) : NEUTRAL_GLOW);
            }
        });
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    // ---------- Idle dimming ----------

    private void resetIdle() {
        handler.removeCallbacks(idleRunnable);
        handler.postDelayed(idleRunnable, IDLE_AFTER_MS);
        if (idle) wake();
    }

    private void goIdle() {
        if (arranging || movingPkg != null || !hasWindowFocus()) return;
        idle = true;
        View f = getCurrentFocus();
        for (View t : allTiles) {
            if (t != f) t.animate().alpha(0.35f).setDuration(600).start();
        }
        if (f != null && allTiles.contains(f)) f.animate().scaleX(1.18f).scaleY(1.18f).setDuration(600).start();
    }

    private void wake() {
        idle = false;
        View f = getCurrentFocus();
        for (View t : allTiles) {
            Float a = baseAlpha.get(t);
            t.animate().alpha(a == null ? 1f : a).setDuration(200).start();
        }
        if (f != null && allTiles.contains(f)) f.animate().scaleX(1.12f).scaleY(1.12f).setDuration(200).start();
    }

    // ---------- Settings menu ----------

    private void openSettingsMenu() {
        String[] opts = {"Arrange apps", "Weather location", "Shield settings", "Cancel"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Settings")
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        if (onDiscover) {
                            onDiscover = false;
                            styleTabs();
                        }
                        arranging = true;
                        render(null);
                    } else if (which == 1) {
                        askWeatherPlace();
                    } else if (which == 2) {
                        startSafe(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .show();
    }

    // ---------- VPN status (location by ipwho.is) ----------

    private void openVpnApp() {
        Intent i = pm.getLeanbackLaunchIntentForPackage(VPN_APP);
        if (i == null) i = pm.getLaunchIntentForPackage(VPN_APP);
        if (i != null) startSafe(i);
        else Toast.makeText(this, "Surfshark isn't installed.", Toast.LENGTH_LONG).show();
    }

    private String vpnTitle() {
        if (lastVpnState == null) return "VPN";
        if (!lastVpnState) return "VPN off";
        return lastWhere != null ? "VPN connected " + DOT + " " + lastWhere : "VPN connected";
    }

    private void registerNet() {
        if (netCallback != null || cm == null) return;
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network n) {
                handler.postDelayed(() -> updateVpn(true), 2000);
            }

            @Override
            public void onLost(Network n) {
                handler.postDelayed(() -> updateVpn(true), 2000);
            }

            @Override
            public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                handler.postDelayed(() -> updateVpn(false), 2000);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Exception e) {
            netCallback = null;
        }
    }

    private void unregisterNet() {
        if (netCallback == null || cm == null) return;
        try {
            cm.unregisterNetworkCallback(netCallback);
        } catch (Exception ignored) {
        }
        netCallback = null;
    }

    private boolean isVpnOn() {
        if (cm == null) return false;
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void updateVpn(boolean force) {
        final boolean on = isVpnOn();
        boolean changed = lastVpnState == null || lastVpnState != on;
        lastVpnState = on;
        if (changed) {
            lastWhere = null;
            showVpn(on, null);
        }
        long now = System.currentTimeMillis();
        if (!(changed || force || now - lastIpFetch > VPN_EVERY_MS)) return;
        lastIpFetch = now;
        new Thread(() -> {
            String where = null;
            try {
                JSONObject o = new JSONObject(httpGet("https://ipwho.is/"));
                if (o.optBoolean("success", false)) {
                    String city = o.optString("city", "");
                    where = city.isEmpty() ? o.optString("country", "") : city;
                }
            } catch (Exception ignored) {
            }
            final String w = where;
            runOnUiThread(() -> showVpn(on, w));
        }).start();
    }

    private void showVpn(boolean on, String where) {
        if (where != null && !where.isEmpty()) lastWhere = where;
        if (vpnDotBg != null) vpnDotBg.setColor(on ? VPN_ON : VPN_OFF);
        if (vpnBtn != null && vpnBtn.isFocused()) titleView.setText(vpnTitle());
    }

    // ---------- Groups ----------

    private String groupOf(String pkg) {
        String saved = prefs.getString("group_" + pkg, null);
        return saved != null ? saved : defaultGroup(pkg);
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

    private static String groupName(String g) {
        if (PLAY.equals(g)) return "Play";
        if (TOOLS.equals(g)) return "Tools";
        return "Watch";
    }

    private void chooseGroup(final String pkg) {
        String current = groupOf(pkg);
        final List<String> options = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (String g : GROUP_ORDER) {
            if (!g.equals(current)) {
                options.add(g);
                names.add(groupName(g));
            }
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Move to group")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    prefs.edit().putString("group_" + pkg, options.get(which)).apply();
                    saveAndRender(pkg);
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    private void exitArrange() {
        arranging = false;
        movingPkg = null;
        render(null);
    }

    private void showAppMenu(final String pkg) {
        final boolean isDock = dock.contains(pkg);
        final boolean isHidden = hidden.contains(pkg);
        final boolean inToolsFolder = !isDock && !isHidden && TOOLS.equals(groupOf(pkg));
        final List<String> opts = new ArrayList<>();
        if (!isHidden && !inToolsFolder) opts.add("Move");
        if (!isHidden && !isDock) opts.add("Move to group");
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
                    } else if (o.equals("Move to group")) {
                        chooseGroup(pkg);
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
        if (dock.contains(movingPkg)) {
            int i = dock.indexOf(movingPkg);
            int j = i + delta;
            if (i < 0 || j < 0 || j >= dock.size()) return;
            dock.remove(i);
            dock.add(j, movingPkg);
            render(movingPkg);
            return;
        }
        String g = groupOf(movingPkg);
        List<String> sub = pkgsInGroup(g);
        int i = sub.indexOf(movingPkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= sub.size()) return;
        sub.remove(i);
        sub.add(j, movingPkg);

        List<String> rebuilt = new ArrayList<>();
        for (String grp : GROUP_ORDER) {
            if (grp.equals(g)) rebuilt.addAll(sub);
            else rebuilt.addAll(pkgsInGroup(grp));
        }
        grid.clear();
        grid.addAll(rebuilt);
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
        weatherView.setText("");
        if (prefs.contains("wx_lat")) {
            placeView.setText(prefs.getString("wx_name", ""));
        } else {
            placeView.setText("Set weather in Settings");
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
                        + "&timezone=auto&forecast_days=1";
                JSONObject o = new JSONObject(httpGet(url));
                JSONObject cur = o.getJSONObject("current");
                final long now = Math.round(cur.getDouble("temperature_2m"));
                final int code = cur.getInt("weather_code");
                runOnUiThread(() -> {
                    weatherView.setText(symbol(code) + "  " + now + DEG);
                    placeView.setText(prefs.getString("wx_name", "") + "  " + DOT + "  " + describe(code));
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
