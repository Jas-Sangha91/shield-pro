package com.jas.shieldhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final String YOUTUBE_APP = "com.google.android.youtube.tv";
    private static final String GOOGLE_HOME = "com.google.android.tvlauncher";
    private static final String GUIDE_URL = "github.com/Jas-Sangha91/shield-pro/blob/main/GUIDE.md";

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

    // Discover (data from TMDB)
    private static final String TMDB_API = "https://api.themoviedb.org/3";
    private static final String TMDB_IMG = "https://image.tmdb.org/t/p/";
    private static final long DISCOVER_REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long SPOT_EVERY_MS = 8000L;
    private static final int ROW_MAX = 12;
    private static final int SPOT_COUNT = 5;
    private static final int POSTER_W = 100, POSTER_H = 150;
    private static final int SPOT_BG = 0xFF0E1118;

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
    private static final String STAR = "\u2605";
    private static final String SEP = "   \u00B7   ";

    private static class Item {
        int id;
        String type;
        String title;
        String poster;
        String backdrop;
        String overview;
        String date;
        double rating;
        String year;

        String key() {
            return type + ":" + id;
        }
    }

    private static class Row {
        String title;
        final List<Item> items = new ArrayList<>();
    }

    private PackageManager pm;
    private SharedPreferences prefs;
    private ConnectivityManager cm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ScrollView scroll;
    private LinearLayout content;
    private TextView titleView, metaView, hintView, weatherView, placeView;
    private TextView tabHome, tabDiscover;
    private FrameLayout searchBtn, vpnBtn, cogBtn;
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

    // Discover state
    private List<Row> discoverRows = null;
    private List<Item> discoverSpot = new ArrayList<>();
    private long discoverAt = 0;
    private long discoverTriedAt = 0;
    private boolean discoverLoading = false;
    private boolean discoverError = false;
    private final Map<String, String> trailerCache = new HashMap<>();

    // Spotlight views
    private ImageView spotBackdrop;
    private TextView spotTitle, spotMeta, spotDesc, spotTrailer, spotList, spotSurprise;
    private final List<View> spotDots = new ArrayList<>();
    private int spotIndex = 0;

    // My list row
    private LinearLayout myListSection;
    private final List<View> myListTiles = new ArrayList<>();

    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final ExecutorService imagePool = Executors.newFixedThreadPool(4);

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

    private final Runnable spotTick = new Runnable() {
        @Override
        public void run() {
            if (onDiscover && spotTitle != null && !spotlightFocused() && hasWindowFocus()) {
                showSpot(spotIndex + 1, true);
            }
            handler.postDelayed(this, SPOT_EVERY_MS);
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
        handler.removeCallbacks(spotTick);
        if (onDiscover && spotTitle != null) handler.postDelayed(spotTick, SPOT_EVERY_MS);
        resetIdle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(vpnTick);
        handler.removeCallbacks(idleRunnable);
        handler.removeCallbacks(spotTick);
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

        // Soft glow that takes the colour of the selected item
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

        // Top left: search, tabs, then weather
        LinearLayout topLeft = new LinearLayout(this);
        topLeft.setOrientation(LinearLayout.VERTICAL);
        topLeft.setClipChildren(false);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setClipChildren(false);
        searchBtn = roundButton(R.drawable.ic_search, () -> "Search", this::openSearch);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(30), dp(30));
        slp.rightMargin = dp(12);
        tabs.addView(searchBtn, slp);
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

        // Bottom left: selected item name, details line (Discover) and arrange hint
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(this);
        style(titleView, 40, true, 0xFFFFFFFF);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxWidth(dp(560));
        titleView.setText(" ");
        metaView = new TextView(this);
        style(metaView, 16, false, SOFT_TEXT);
        metaView.setVisibility(View.GONE);
        hintView = new TextView(this);
        style(hintView, 16, false, 0xFFFFD54F);
        hintView.setVisibility(View.GONE);
        left.addView(titleView);
        left.addView(metaView);
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
                metaView.setText("");
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
        if (scroll != null) scroll.scrollTo(0, 0);
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
                metaView.setText("");
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
        render(null, !onDiscover);
    }

    private void render(String focusPkg) {
        render(focusPkg, true);
    }

    private void render(String focusPkg, boolean takeFocus) {
        handler.removeCallbacks(spotTick);
        spotTitle = null;
        spotBackdrop = null;
        spotDots.clear();
        myListSection = null;
        myListTiles.clear();

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
        metaView.setText("");
        metaView.setVisibility(onDiscover ? View.VISIBLE : View.GONE);

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
        addHeaderTo(content, text);
    }

    private void addHeaderTo(LinearLayout parent, String text) {
        TextView h = new TextView(this);
        style(h, 18, true, 0xA0FFFFFF);
        h.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(24);
        lp.bottomMargin = dp(4);
        lp.leftMargin = dp(12);
        parent.addView(h, lp);
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

    // ---------- Search ----------

    private void openSearch() {
        final Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, timeOfDayColors()));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(32), dp(48), dp(20));
        col.setClipChildren(false);
        col.setClipToPadding(false);
        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView heading = new TextView(this);
        style(heading, 16, true, SOFT_TEXT);
        heading.setText("Search");
        heading.setPadding(dp(8), 0, 0, dp(10));
        col.addView(heading);

        final EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0x80FFFFFF);
        field.setHint("Search your apps, films and series");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        final GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(dp(28));
        fieldBg.setColor(0x1FFFFFFF);
        field.setBackground(fieldBg);
        field.setPadding(dp(22), dp(14), dp(22), dp(14));
        Drawable icon = getDrawable(R.drawable.ic_search).mutate();
        icon.setTint(ICON_IDLE);
        icon.setBounds(0, 0, dp(22), dp(22));
        field.setCompoundDrawables(icon, null, null, null);
        field.setCompoundDrawablePadding(dp(14));
        field.setOnFocusChangeListener((v, f) -> fieldBg.setStroke(f ? dp(2) : 0, 0xFFFFFFFF));
        col.addView(field, new LinearLayout.LayoutParams(MATCH, WRAP));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(dp(24));
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        results.setPadding(0, dp(4), 0, dp(40));
        sv.addView(results, new FrameLayout.LayoutParams(MATCH, WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        final boolean hasTmdb = BuildConfig.TMDB_TOKEN != null && !BuildConfig.TMDB_TOKEN.isEmpty();
        final Runnable[] pending = new Runnable[1];
        final int[] generation = {0};

        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String q = s.toString().trim();
                if (pending[0] != null) handler.removeCallbacks(pending[0]);
                final int gen = ++generation[0];
                boolean searchFilms = hasTmdb && q.length() >= 2;
                renderSearch(results, field, d, q, null, searchFilms);
                if (!searchFilms) return;
                pending[0] = () -> new Thread(() -> {
                    final List<Item> found = new ArrayList<>();
                    try {
                        JSONArray res = new JSONObject(tmdbGet("/search/multi?language=en-US&include_adult=false&page=1&query="
                                + URLEncoder.encode(q, "UTF-8"))).getJSONArray("results");
                        for (int i = 0; i < res.length() && found.size() < 20; i++) {
                            Item it = parseItem(res.getJSONObject(i), null);
                            if (it != null) found.add(it);
                        }
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(() -> {
                        if (gen == generation[0] && d.isShowing()) renderSearch(results, field, d, q, found, true);
                    });
                }).start();
                handler.postDelayed(pending[0], 450);
            }
        });

        field.setOnEditorActionListener((v, actionId, ev) -> {
            String q = field.getText().toString().trim();
            if (!q.isEmpty()) saveRecentSearch(q);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
            results.requestFocus();
            return true;
        });

        renderSearch(results, field, d, "", null, false);

        d.setContentView(root);
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        d.setOnDismissListener(di -> {
            if (pending[0] != null) handler.removeCallbacks(pending[0]);
            generation[0]++;
        });
        d.show();
        field.requestFocus();
    }

    private void renderSearch(final LinearLayout results, final EditText field, final Dialog d,
                              final String q, final List<Item> films, final boolean searchFilms) {
        results.removeAllViews();

        if (q.isEmpty()) {
            List<String> recent = loadRecentSearches();
            if (recent.isEmpty()) {
                searchNote(results, "Type to search your apps and every film and series.");
                return;
            }
            addHeaderTo(results, "Recent searches");
            LinearLayout pills = new LinearLayout(this);
            pills.setOrientation(LinearLayout.HORIZONTAL);
            pills.setPadding(dp(12), dp(6), dp(12), dp(6));
            pills.setClipChildren(false);
            for (final String term : recent) {
                TextView p = pillButton(term, () -> {
                    field.setText(term);
                    field.setSelection(term.length());
                }, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                lp.rightMargin = dp(10);
                pills.addView(p, lp);
            }
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.addView(pills, new FrameLayout.LayoutParams(WRAP, WRAP));
            results.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
            return;
        }

        // Apps
        String lower = q.toLowerCase(Locale.UK);
        List<String> apps = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : installed.entrySet()) {
            String label = e.getValue().loadLabel(pm).toString().toLowerCase(Locale.UK);
            if (label.contains(lower)) apps.add(e.getKey());
            if (apps.size() >= 10) break;
        }
        if (!apps.isEmpty()) {
            addHeaderTo(results, "Apps");
            LinearLayout band = new LinearLayout(this);
            band.setOrientation(LinearLayout.HORIZONTAL);
            band.setPadding(dp(12), dp(4), dp(12), dp(4));
            band.setClipChildren(false);
            band.setClipToPadding(false);
            for (final String pkg : apps) {
                band.addView(buildAppTile(pkg, () -> {
                    saveRecentSearch(q);
                    d.dismiss();
                    launch(pkg);
                }), size(GRID_W, GRID_H, GRID_GAP));
            }
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.addView(band, new FrameLayout.LayoutParams(WRAP, WRAP));
            results.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        // Films and series
        if (searchFilms) {
            if (films == null) {
                searchNote(results, "Searching films and series\u2026");
            } else if (films.isEmpty()) {
                searchNote(results, apps.isEmpty() ? "No results for \"" + q + "\"." : "No films or series found.");
            } else {
                addHeaderTo(results, "Films and series");
                LinearLayout band = new LinearLayout(this);
                band.setOrientation(LinearLayout.HORIZONTAL);
                band.setPadding(dp(12), dp(4), dp(12), dp(4));
                band.setClipChildren(false);
                band.setClipToPadding(false);
                for (final Item it : films) {
                    band.addView(buildPoster(it, POSTER_W, POSTER_H, false, () -> {
                        saveRecentSearch(q);
                        openInfo(it);
                    }), size(POSTER_W, POSTER_H, 8));
                }
                HorizontalScrollView hs = new HorizontalScrollView(this);
                hs.setHorizontalScrollBarEnabled(false);
                hs.setClipChildren(false);
                hs.setClipToPadding(false);
                hs.addView(band, new FrameLayout.LayoutParams(WRAP, WRAP));
                results.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
        } else if (apps.isEmpty()) {
            searchNote(results, "No apps match \"" + q + "\".");
        }
    }

    private void searchNote(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        style(t, 16, false, SOFT_TEXT);
        t.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(24);
        lp.leftMargin = dp(12);
        parent.addView(t, lp);
    }

    private List<String> loadRecentSearches() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("recent_searches", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveRecentSearch(String q) {
        List<String> list = loadRecentSearches();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(q)) list.remove(i);
        }
        list.add(0, q);
        while (list.size() > 6) list.remove(list.size() - 1);
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        prefs.edit().putString("recent_searches", a.toString()).apply();
    }

    // ---------- Discover (data from TMDB) ----------

    private void renderDiscover() {
        if (BuildConfig.TMDB_TOKEN == null || BuildConfig.TMDB_TOKEN.isEmpty()) {
            discoverMessage("Discover needs a TMDB key",
                    "Add the TMDB_TOKEN secret on GitHub, then build again.");
            return;
        }

        long now = System.currentTimeMillis();
        boolean stale = discoverRows == null || now - discoverAt > DISCOVER_REFRESH_MS;
        if (stale && !discoverLoading && now - discoverTriedAt > 30 * 1000L) loadDiscover();

        if (discoverRows == null) {
            if (discoverError && !discoverLoading) {
                discoverMessage("Couldn't load Discover",
                        "Check the internet connection, then open Discover again in a moment.");
            } else {
                discoverMessage("Finding something good to watch\u2026", "");
            }
            return;
        }

        if (!discoverSpot.isEmpty()) buildSpotlight();

        myListSection = new LinearLayout(this);
        myListSection.setOrientation(LinearLayout.VERTICAL);
        myListSection.setClipChildren(false);
        myListSection.setClipToPadding(false);
        content.addView(myListSection, new LinearLayout.LayoutParams(MATCH, WRAP));
        refreshMyListRow();

        for (Row r : discoverRows) {
            if (r.items.isEmpty()) continue;
            addHeader(r.title);
            posterRow(content, r.items, null);
        }

        TextView credit = new TextView(this);
        style(credit, 12, false, 0x80FFFFFF);
        credit.setText("This product uses the TMDB API but is not endorsed or certified by TMDB.");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(WRAP, WRAP);
        clp.topMargin = dp(28);
        clp.leftMargin = dp(12);
        content.addView(credit, clp);
    }

    private void discoverMessage(String head, String body) {
        TextView a = new TextView(this);
        style(a, 26, true, 0xFFFFFFFF);
        a.setText(head);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(WRAP, WRAP);
        alp.topMargin = dp(40);
        alp.leftMargin = dp(12);
        content.addView(a, alp);
        if (!body.isEmpty()) {
            TextView b = new TextView(this);
            style(b, 16, false, SOFT_TEXT);
            b.setText(body);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(WRAP, WRAP);
            blp.topMargin = dp(10);
            blp.leftMargin = dp(12);
            content.addView(b, blp);
        }
    }

    private void loadDiscover() {
        discoverLoading = true;
        discoverError = false;
        discoverTriedAt = System.currentTimeMillis();
        new Thread(() -> {
            final List<Item> spot = new ArrayList<>();
            final List<Row> rows = new ArrayList<>();
            boolean ok = true;
            try {
                Set<String> seen = new HashSet<>();

                JSONArray trending = new JSONObject(tmdbGet("/trending/all/week?language=en-US"))
                        .getJSONArray("results");
                Row trendRow = new Row();
                trendRow.title = "Trending this week";
                for (int i = 0; i < trending.length(); i++) {
                    Item it = parseItem(trending.getJSONObject(i), null);
                    if (it == null || seen.contains(it.key())) continue;
                    if (spot.size() < SPOT_COUNT && !it.backdrop.isEmpty() && !it.overview.isEmpty()) {
                        spot.add(it);
                        seen.add(it.key());
                    } else if (trendRow.items.size() < ROW_MAX) {
                        trendRow.items.add(it);
                        seen.add(it.key());
                    }
                }
                rows.add(trendRow);

                rows.add(fetchRow("New and highly rated",
                        "/discover/movie?language=en-US&sort_by=popularity.desc&vote_average.gte=7&vote_count.gte=300"
                                + "&primary_release_date.gte=" + dateOffset(-15) + "&primary_release_date.lte=" + dateOffset(0),
                        "movie", seen, null));

                rows.add(fetchRow("Binge-worthy series",
                        "/discover/tv?language=en-US&sort_by=popularity.desc&vote_average.gte=8&vote_count.gte=500",
                        "tv", seen, null));

                String[] mood = moodForToday();
                rows.add(fetchRow("Tonight's mood: " + mood[1],
                        "/discover/movie?language=en-US&sort_by=popularity.desc&vote_average.gte=7&vote_count.gte=500"
                                + "&with_genres=" + mood[0],
                        "movie", seen, null));

                rows.add(fetchRow("Coming soon", "/movie/upcoming?language=en-US", "movie", seen, dateOffset(0)));
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            if (success) {
                for (Item it : spot) prefetch(TMDB_IMG + "w780" + it.backdrop);
            }
            runOnUiThread(() -> {
                discoverLoading = false;
                if (success) {
                    discoverSpot = spot;
                    discoverRows = rows;
                    discoverAt = System.currentTimeMillis();
                    spotIndex = 0;
                } else {
                    discoverError = true;
                }
                if (onDiscover) render(null, false);
            });
        }).start();
    }

    private Row fetchRow(String title, String path, String type, Set<String> seen, String minDate) throws Exception {
        JSONArray res = new JSONObject(tmdbGet(path)).getJSONArray("results");
        Row row = new Row();
        row.title = title;
        for (int i = 0; i < res.length() && row.items.size() < ROW_MAX; i++) {
            Item it = parseItem(res.getJSONObject(i), type);
            if (it == null || seen.contains(it.key())) continue;
            if (minDate != null && (it.date.isEmpty() || it.date.compareTo(minDate) < 0)) continue;
            row.items.add(it);
            seen.add(it.key());
        }
        return row;
    }

    private static Item parseItem(JSONObject j, String forcedType) {
        String type = forcedType != null ? forcedType : str(j, "media_type");
        if (!type.equals("movie") && !type.equals("tv")) return null;
        String poster = str(j, "poster_path");
        if (poster.isEmpty()) return null;
        Item it = new Item();
        it.id = j.optInt("id", 0);
        if (it.id == 0) return null;
        it.type = type;
        it.title = type.equals("movie") ? str(j, "title") : str(j, "name");
        it.poster = poster;
        it.backdrop = str(j, "backdrop_path");
        it.overview = str(j, "overview");
        it.rating = j.optDouble("vote_average", 0);
        it.date = type.equals("movie") ? str(j, "release_date") : str(j, "first_air_date");
        it.year = it.date.length() >= 4 ? it.date.substring(0, 4) : "";
        return it;
    }

    private static String dateOffset(int months) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, months);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private static String[] moodForToday() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY: return new String[]{"35", "Comedy"};
            case Calendar.TUESDAY: return new String[]{"53", "Thriller"};
            case Calendar.WEDNESDAY: return new String[]{"878", "Sci-fi"};
            case Calendar.THURSDAY: return new String[]{"80", "Crime"};
            case Calendar.FRIDAY: return new String[]{"28", "Action"};
            case Calendar.SATURDAY: return new String[]{"10751", "Family"};
            default: return new String[]{"18", "Drama"};
        }
    }

    private static String str(JSONObject j, String key) {
        if (j == null || !j.has(key) || j.isNull(key)) return "";
        return j.optString(key, "");
    }

    private static String tmdbGet(String path) throws Exception {
        return httpGet(TMDB_API + path, BuildConfig.TMDB_TOKEN);
    }

    private String meta(Item it) {
        List<String> parts = new ArrayList<>();
        if (it.rating > 0) parts.add(STAR + " " + String.format(Locale.UK, "%.1f", it.rating));
        if (!it.year.isEmpty()) parts.add(it.year);
        parts.add(it.type.equals("movie") ? "Film" : "Series");
        return TextUtils.join(SEP, parts);
    }

    // Spotlight

    private void buildSpotlight() {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(SPOT_BG);
        box.setBackground(bg);
        box.setClipToOutline(true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, dp(236));
        blp.setMargins(dp(12), dp(6), dp(12), dp(6));
        content.addView(box, blp);

        spotBackdrop = new ImageView(this);
        spotBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(spotBackdrop, new FrameLayout.LayoutParams(dp(560), MATCH, Gravity.END));

        View shadeLeft = new View(this);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{SPOT_BG, 0xB30E1118, 0x000E1118}));
        box.addView(shadeLeft, new FrameLayout.LayoutParams(dp(560), MATCH, Gravity.END));

        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000E1118, SPOT_BG}));
        box.addView(shadeBottom, new FrameLayout.LayoutParams(MATCH, dp(70), Gravity.BOTTOM));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(26), dp(20), dp(26), dp(20));
        text.setClipChildren(false);
        text.setClipToPadding(false);

        TextView label = new TextView(this);
        style(label, 13, true, 0xFFC9A8F0);
        label.setText("Tonight's pick");
        text.addView(label);

        spotTitle = new TextView(this);
        style(spotTitle, 30, true, 0xFFFFFFFF);
        spotTitle.setSingleLine(true);
        spotTitle.setEllipsize(TextUtils.TruncateAt.END);
        spotTitle.setMaxWidth(dp(480));
        spotTitle.setPadding(0, dp(2), 0, 0);
        text.addView(spotTitle);

        spotMeta = new TextView(this);
        style(spotMeta, 15, false, SOFT_TEXT);
        spotMeta.setPadding(0, dp(2), 0, 0);
        text.addView(spotMeta);

        spotDesc = new TextView(this);
        style(spotDesc, 15, false, 0xFFE6E8EE);
        spotDesc.setMaxLines(3);
        spotDesc.setEllipsize(TextUtils.TruncateAt.END);
        spotDesc.setLineSpacing(0, 1.15f);
        spotDesc.setMaxWidth(dp(440));
        spotDesc.setPadding(0, dp(8), 0, 0);
        text.addView(spotDesc);

        text.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        spotTrailer = pillButton("\u25B6  Trailer", () -> playTrailerFor(currentSpot()), this::onSpotFocus);
        spotList = pillButton("+  My list", () -> {
            toggleMyList(currentSpot());
            updateSpotListButton();
        }, this::onSpotFocus);
        spotSurprise = pillButton("Surprise me", this::surpriseMe, this::onSpotFocus);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.rightMargin = dp(10);
        buttons.addView(spotTrailer, bl);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl2.rightMargin = dp(10);
        buttons.addView(spotList, bl2);
        buttons.addView(spotSurprise);
        text.addView(buttons);

        box.addView(text, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        spotDots.clear();
        for (int i = 0; i < discoverSpot.size(); i++) {
            View dv = new View(this);
            GradientDrawable dbg = new GradientDrawable();
            dbg.setCornerRadius(dp(2));
            dbg.setColor(0x59FFFFFF);
            dv.setBackground(dbg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(4));
            dlp.leftMargin = dp(5);
            dots.addView(dv, dlp);
            spotDots.add(dv);
        }
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
        dotsLp.setMargins(0, 0, dp(24), dp(22));
        box.addView(dots, dotsLp);

        showSpot(spotIndex, false);
        handler.removeCallbacks(spotTick);
        handler.postDelayed(spotTick, SPOT_EVERY_MS);
    }

    private Item currentSpot() {
        if (discoverSpot.isEmpty()) return null;
        return discoverSpot.get(Math.max(0, Math.min(spotIndex, discoverSpot.size() - 1)));
    }

    private boolean spotlightFocused() {
        return (spotTrailer != null && spotTrailer.isFocused())
                || (spotList != null && spotList.isFocused())
                || (spotSurprise != null && spotSurprise.isFocused());
    }

    private void showSpot(int i, boolean animate) {
        if (discoverSpot.isEmpty() || spotTitle == null) return;
        int n = discoverSpot.size();
        spotIndex = ((i % n) + n) % n;
        final Item it = discoverSpot.get(spotIndex);
        spotTitle.setText(it.title);
        spotMeta.setText(meta(it));
        spotDesc.setText(it.overview);
        updateSpotListButton();

        for (int k = 0; k < spotDots.size(); k++) {
            View dv = spotDots.get(k);
            boolean active = k == spotIndex;
            ((GradientDrawable) dv.getBackground()).setColor(active ? 0xFFFFFFFF : 0x59FFFFFF);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dv.getLayoutParams();
            lp.width = dp(active ? 18 : 6);
            dv.setLayoutParams(lp);
        }

        final String url = TMDB_IMG + "w780" + it.backdrop;
        final ImageView target = spotBackdrop;
        if (animate && target != null) {
            target.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                loadImage(url, target);
                target.animate().alpha(1f).setDuration(450).start();
            }).start();
        } else if (target != null) {
            loadImage(url, target);
        }
        if (spotlightFocused()) onSpotFocus();
    }

    private void onSpotFocus() {
        Item it = currentSpot();
        if (it == null) return;
        titleView.setText(it.title);
        metaView.setText(meta(it));
        Drawable dr = spotBackdrop != null ? spotBackdrop.getDrawable() : null;
        setGlow(dr != null ? dominantColor(dr, NEUTRAL_GLOW) : NEUTRAL_GLOW);
    }

    private void updateSpotListButton() {
        Item it = currentSpot();
        if (spotList == null || it == null) return;
        spotList.setText(isInMyList(it) ? "\u2713  In My list" : "+  My list");
    }

    // Surprise me

    private void surpriseMe() {
        Toast.makeText(this, "Finding something good\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Item pick = null;
            try {
                Random r = new Random();
                String type = r.nextBoolean() ? "movie" : "tv";
                String base = "/discover/" + type + "?language=en-US&sort_by=popularity.desc"
                        + "&vote_average.gte=7.5&vote_count.gte=1000&page=";
                JSONArray res = new JSONObject(tmdbGet(base + (1 + r.nextInt(10)))).getJSONArray("results");
                if (res.length() == 0) res = new JSONObject(tmdbGet(base + 1)).getJSONArray("results");
                List<Item> options = new ArrayList<>();
                for (int i = 0; i < res.length(); i++) {
                    Item it = parseItem(res.getJSONObject(i), type);
                    if (it != null) options.add(it);
                }
                if (!options.isEmpty()) pick = options.get(r.nextInt(options.size()));
            } catch (Exception ignored) {
            }
            final Item chosen = pick;
            runOnUiThread(() -> {
                if (chosen != null) openInfo(chosen);
                else Toast.makeText(this, "Couldn't find a pick right now. Try again in a moment.",
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // My list

    private List<Item> loadMyList() {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("mylist", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.optInt("id", 0);
                it.type = str(o, "type");
                it.title = str(o, "title");
                it.poster = str(o, "poster");
                it.backdrop = str(o, "backdrop");
                it.overview = str(o, "overview");
                it.date = str(o, "date");
                it.rating = o.optDouble("rating", 0);
                it.year = str(o, "year");
                if (it.id != 0 && !it.poster.isEmpty()) out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveMyList(List<Item> list) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("type", it.type);
                o.put("title", it.title);
                o.put("poster", it.poster);
                o.put("backdrop", it.backdrop);
                o.put("overview", it.overview);
                o.put("date", it.date);
                o.put("rating", it.rating);
                o.put("year", it.year);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs.edit().putString("mylist", a.toString()).apply();
    }

    private boolean isInMyList(Item it) {
        if (it == null) return false;
        for (Item m : loadMyList()) if (m.key().equals(it.key())) return true;
        return false;
    }

    private boolean toggleMyList(Item it) {
        if (it == null) return false;
        List<Item> list = loadMyList();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(it.key())) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) list.add(0, it);
        saveMyList(list);
        refreshMyListRow();
        Toast.makeText(this, removed ? "Removed from My list" : "Added to My list", Toast.LENGTH_SHORT).show();
        return !removed;
    }

    private void refreshMyListRow() {
        if (myListSection == null) return;
        for (View v : myListTiles) {
            allTiles.remove(v);
            baseAlpha.remove(v);
        }
        myListTiles.clear();
        myListSection.removeAllViews();
        List<Item> list = loadMyList();
        if (list.isEmpty()) return;
        addHeaderTo(myListSection, "My list");
        posterRow(myListSection, list, myListTiles);
    }

    // Posters

    private void posterRow(LinearLayout parent, List<Item> items, List<View> track) {
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setPadding(dp(12), dp(4), dp(12), dp(4));
        band.setClipChildren(false);
        band.setClipToPadding(false);
        for (final Item it : items) {
            View p = register(buildPoster(it, POSTER_W, POSTER_H, true, () -> openInfo(it)));
            if (track != null) track.add(p);
            band.addView(p, size(POSTER_W, POSTER_H, 8));
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(band, new FrameLayout.LayoutParams(WRAP, WRAP));
        parent.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private View buildPoster(final Item it, int w, int h, final boolean updateHeader, final Runnable onClick) {
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF2B3140);
        t.setBackground(bg);
        t.setClipToOutline(true);

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        loadImage(TMDB_IMG + "w185" + it.poster, iv);

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), 0xFFFFFFFF);
        ring.setColor(0x00000000);

        t.setOnFocusChangeListener((v, f) -> {
            v.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f)
                    .translationZ(f ? dp(10) : 0).setDuration(150).start();
            ((FrameLayout) v).setForeground(f ? ring : null);
            if (f && updateHeader) {
                titleView.setText(it.title);
                metaView.setText(meta(it));
                Drawable dr = iv.getDrawable();
                setGlow(dr != null ? dominantColor(dr, NEUTRAL_GLOW) : NEUTRAL_GLOW);
            }
        });
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private Bitmap fetchBitmap(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = c.getInputStream()) {
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void prefetch(final String url) {
        if (imageCache.get(url) != null) return;
        imagePool.execute(() -> {
            Bitmap b = fetchBitmap(url);
            if (b != null) imageCache.put(url, b);
        });
    }

    private void loadImage(final String url, final ImageView into) {
        into.setTag(url);
        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            into.setImageBitmap(cached);
            return;
        }
        into.setImageDrawable(null);
        imagePool.execute(() -> {
            Bitmap b = fetchBitmap(url);
            if (b == null) return;
            imageCache.put(url, b);
            runOnUiThread(() -> {
                if (url.equals(into.getTag())) into.setImageBitmap(b);
            });
        });
    }

    // Info card

    private void openInfo(final Item it) {
        if (it == null) return;
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);

        FrameLayout root = new FrameLayout(this);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setCornerRadius(dp(18));
        rootBg.setColor(0xFF0F131C);
        root.setBackground(rootBg);
        root.setClipToOutline(true);

        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (!it.backdrop.isEmpty()) loadImage(TMDB_IMG + "w780" + it.backdrop, backdrop);

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20B0E14, 0xD90B0E14, 0xA60B0E14}));
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(26), dp(24), dp(26), dp(16));
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        root.addView(outer, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        outer.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(10));
        pbg.setColor(0xFF2B3140);
        poster.setBackground(pbg);
        poster.setClipToOutline(true);
        loadImage(TMDB_IMG + "w342" + it.poster, poster);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(140), dp(210));
        plp.rightMargin = dp(24);
        top.addView(poster, plp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClipChildren(false);
        top.addView(info, new LinearLayout.LayoutParams(0, dp(210), 1f));

        TextView title = new TextView(this);
        style(title, 26, true, 0xFFFFFFFF);
        title.setText(it.title);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);

        final TextView metaLine = new TextView(this);
        style(metaLine, 15, false, SOFT_TEXT);
        metaLine.setText(meta(it));
        metaLine.setPadding(0, dp(4), 0, 0);
        info.addView(metaLine);

        final TextView genres = new TextView(this);
        style(genres, 14, false, SOFT_TEXT);
        genres.setPadding(0, dp(2), 0, 0);
        info.addView(genres);

        final TextView overview = new TextView(this);
        style(overview, 15, false, 0xFFFFFFFF);
        overview.setText(it.overview.isEmpty() ? "Loading details\u2026" : it.overview);
        overview.setMaxLines(4);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(0, 1.15f);
        overview.setPadding(0, dp(10), 0, 0);
        info.addView(overview);

        final TextView cast = new TextView(this);
        style(cast, 13, false, SOFT_TEXT);
        cast.setPadding(0, dp(8), 0, 0);
        cast.setSingleLine(true);
        cast.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(cast);

        info.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        final String[] trailerKey = new String[1];
        final TextView trailerBtn = pillButton("Finding trailer\u2026", () -> {
            if (trailerKey[0] != null) {
                dialog.dismiss();
                playTrailer(trailerKey[0]);
            }
        }, null);
        final TextView[] listBtnHolder = new TextView[1];
        listBtnHolder[0] = pillButton(isInMyList(it) ? "\u2713  In My list" : "+  My list", () -> {
            boolean nowIn = toggleMyList(it);
            listBtnHolder[0].setText(nowIn ? "\u2713  In My list" : "+  My list");
            updateSpotListButton();
        }, null);
        TextView closeBtn = pillButton("Close", dialog::dismiss, null);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.rightMargin = dp(10);
        buttons.addView(trailerBtn, bl);
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl2.rightMargin = dp(10);
        buttons.addView(listBtnHolder[0], bl2);
        buttons.addView(closeBtn);
        info.addView(buttons);

        final TextView moreHeader = new TextView(this);
        style(moreHeader, 15, true, 0xA0FFFFFF);
        moreHeader.setText("More like this");
        moreHeader.setVisibility(View.GONE);
        moreHeader.setPadding(0, dp(16), 0, dp(2));
        outer.addView(moreHeader);

        final LinearLayout moreBand = new LinearLayout(this);
        moreBand.setOrientation(LinearLayout.HORIZONTAL);
        moreBand.setPadding(0, dp(4), dp(8), dp(4));
        moreBand.setClipChildren(false);
        moreBand.setClipToPadding(false);
        HorizontalScrollView moreScroll = new HorizontalScrollView(this);
        moreScroll.setHorizontalScrollBarEnabled(false);
        moreScroll.setClipChildren(false);
        moreScroll.setClipToPadding(false);
        moreScroll.addView(moreBand, new FrameLayout.LayoutParams(WRAP, WRAP));
        outer.addView(moreScroll, new LinearLayout.LayoutParams(MATCH, WRAP));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        }
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(dp(800), dp(470));
        trailerBtn.requestFocus();

        new Thread(() -> {
            try {
                String extra = it.type.equals("movie") ? "release_dates" : "content_ratings";
                JSONObject d = new JSONObject(tmdbGet("/" + it.type + "/" + it.id
                        + "?language=en-US&append_to_response=videos,credits,recommendations," + extra));

                List<String> g = new ArrayList<>();
                JSONArray ga = d.optJSONArray("genres");
                if (ga != null) for (int i = 0; i < ga.length() && i < 3; i++) g.add(str(ga.getJSONObject(i), "name"));

                String length = "";
                if (it.type.equals("movie")) {
                    int mins = d.optInt("runtime", 0);
                    if (mins > 0) length = (mins / 60) + "h " + (mins % 60) + "m";
                } else {
                    int seasons = d.optInt("number_of_seasons", 0);
                    if (seasons > 0) length = seasons + (seasons == 1 ? " season" : " seasons");
                }

                String ov = str(d, "overview");

                List<String> names = new ArrayList<>();
                JSONObject credits = d.optJSONObject("credits");
                JSONArray ca = credits != null ? credits.optJSONArray("cast") : null;
                if (ca != null) for (int i = 0; i < ca.length() && i < 3; i++) names.add(str(ca.getJSONObject(i), "name"));

                JSONObject videos = d.optJSONObject("videos");
                String key = pickTrailer(videos != null ? videos.optJSONArray("results") : null);

                String age = ageRating(d, it.type);

                final List<Item> recs = new ArrayList<>();
                JSONObject rec = d.optJSONObject("recommendations");
                JSONArray ra = rec != null ? rec.optJSONArray("results") : null;
                if (ra != null) {
                    for (int i = 0; i < ra.length() && recs.size() < 10; i++) {
                        JSONObject rj = ra.getJSONObject(i);
                        String rt = str(rj, "media_type");
                        Item ri = parseItem(rj, rt.isEmpty() ? it.type : rt);
                        if (ri != null) recs.add(ri);
                    }
                }

                final String genreText = TextUtils.join(", ", g)
                        + (length.isEmpty() ? "" : (g.isEmpty() ? "" : SEP) + length);
                final String overviewText = ov.isEmpty()
                        ? (it.overview.isEmpty() ? "No description available." : it.overview) : ov;
                final String castText = names.isEmpty() ? "" : "Starring " + TextUtils.join(", ", names);
                final String metaText = meta(it) + (age.isEmpty() ? "" : SEP + age);
                final String foundKey = key;
                if (key != null) trailerCache.put(it.key(), key);

                runOnUiThread(() -> {
                    metaLine.setText(metaText);
                    genres.setText(genreText);
                    overview.setText(overviewText);
                    cast.setText(castText);
                    trailerKey[0] = foundKey;
                    trailerBtn.setText(foundKey != null ? "\u25B6  Trailer" : "No trailer available");
                    if (!recs.isEmpty()) {
                        moreHeader.setVisibility(View.VISIBLE);
                        for (final Item r : recs) {
                            View p = buildPoster(r, 64, 96, false, () -> {
                                dialog.dismiss();
                                openInfo(r);
                            });
                            moreBand.addView(p, size(64, 96, 6));
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    overview.setText(it.overview.isEmpty()
                            ? "Couldn't load details. Check the internet connection." : it.overview);
                    trailerBtn.setText("No trailer available");
                });
            }
        }).start();
    }

    private static String pickTrailer(JSONArray va) {
        if (va == null) return null;
        try {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < va.length(); i++) {
                    JSONObject v = va.getJSONObject(i);
                    if (!"YouTube".equals(str(v, "site"))) continue;
                    String vt = str(v, "type");
                    boolean good = pass == 0 ? "Trailer".equals(vt) : ("Teaser".equals(vt) || "Trailer".equals(vt));
                    if (good) {
                        String k = str(v, "key");
                        if (!k.isEmpty()) return k;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String ageRating(JSONObject d, String type) {
        try {
            String us = "";
            if (type.equals("movie")) {
                JSONObject rd = d.optJSONObject("release_dates");
                JSONArray arr = rd != null ? rd.optJSONArray("results") : null;
                if (arr == null) return "";
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String country = str(c, "iso_3166_1");
                    JSONArray dates = c.optJSONArray("release_dates");
                    if (dates == null) continue;
                    for (int k = 0; k < dates.length(); k++) {
                        String cert = str(dates.getJSONObject(k), "certification");
                        if (cert.isEmpty()) continue;
                        if ("GB".equals(country)) return cert;
                        if ("US".equals(country) && us.isEmpty()) us = cert;
                    }
                }
            } else {
                JSONObject cr = d.optJSONObject("content_ratings");
                JSONArray arr = cr != null ? cr.optJSONArray("results") : null;
                if (arr == null) return "";
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String country = str(c, "iso_3166_1");
                    String rating = str(c, "rating");
                    if (rating.isEmpty()) continue;
                    if ("GB".equals(country)) return rating;
                    if ("US".equals(country) && us.isEmpty()) us = rating;
                }
            }
            return us;
        } catch (Exception e) {
            return "";
        }
    }

    private void playTrailerFor(final Item it) {
        if (it == null) return;
        String cached = trailerCache.get(it.key());
        if (cached != null) {
            playTrailer(cached);
            return;
        }
        new Thread(() -> {
            String key = null;
            try {
                JSONObject d = new JSONObject(tmdbGet("/" + it.type + "/" + it.id + "/videos?language=en-US"));
                key = pickTrailer(d.optJSONArray("results"));
            } catch (Exception ignored) {
            }
            final String k = key;
            runOnUiThread(() -> {
                if (k != null) {
                    trailerCache.put(it.key(), k);
                    playTrailer(k);
                } else {
                    Toast.makeText(this, "No trailer available for this one.", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private TextView pillButton(String label, final Runnable onClick, final Runnable onFocus) {
        final TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setPadding(dp(20), dp(9), dp(20), dp(10));
        b.setFocusable(true);
        b.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0x26FFFFFF);
        b.setBackground(bg);
        b.setTextColor(0xFFFFFFFF);
        b.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x26FFFFFF);
            b.setTextColor(f ? DARK : 0xFFFFFFFF);
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
            if (f && onFocus != null) onFocus.run();
        });
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void playTrailer(String key) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage(YOUTUBE_APP);
        try {
            startActivity(i);
        } catch (Exception e) {
            i.setPackage(null);
            startSafe(i);
        }
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
        String[] opts = {"Arrange apps", "Weather location", "Shield settings",
                "Open Google home screen", "How to restore", "Cancel"};
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
                    } else if (which == 3) {
                        openGoogleHome();
                    } else if (which == 4) {
                        showRestoreHelp(false);
                    }
                })
                .show();
    }

    private void openGoogleHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setPackage(GOOGLE_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            showRestoreHelp(true);
        }
    }

    private void showRestoreHelp(boolean googleIsOff) {
        String intro = googleIsOff
                ? "Google's home screen is switched off, so it can't be opened from here. To switch it back on:\n\n"
                : "To go back to Google's original home screen:\n\n";
        String steps = "1. On the Mac, open Terminal and run:\n"
                + "   cd ~/Downloads/platform-tools\n"
                + "   ./adb connect 192.168.1.197:5555\n\n"
                + "2. Then run these three lines:\n"
                + "   ./adb shell pm enable com.google.android.tvlauncher\n"
                + "   ./adb shell pm enable com.google.android.tvrecommendations\n"
                + "   ./adb shell pm enable com.google.android.tungsten.setupwraith\n\n"
                + "3. Press Home and choose Google's home screen, then Always.\n\n"
                + "Switch Google's home screen back on before uninstalling Sangha Home.\n\n"
                + "Full guide: " + GUIDE_URL;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("How to restore")
                .setMessage(intro + steps)
                .setPositiveButton("Close", null)
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
                JSONObject o = new JSONObject(httpGet("https://ipwho.is/", null));
                if (o.optBoolean("success", false)) {
                    String city = str(o, "city");
                    where = city.isEmpty() ? str(o, "country") : city;
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
                JSONObject o = new JSONObject(httpGet(url, null));
                JSONArray res = o.optJSONArray("results");
                final List<String> names = new ArrayList<>();
                final List<String> shortNames = new ArrayList<>();
                final List<double[]> coords = new ArrayList<>();
                if (res != null) {
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject r = res.getJSONObject(i);
                        String name = str(r, "name");
                        String admin = str(r, "admin1");
                        String country = str(r, "country");
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
                JSONObject o = new JSONObject(httpGet(url, null));
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

    private static String httpGet(String address, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
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
