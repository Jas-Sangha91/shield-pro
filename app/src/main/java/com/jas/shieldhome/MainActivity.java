package com.jas.shieldhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import com.jas.shieldhome.Layout.Folder;
import com.jas.shieldhome.Tmdb.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jas.shieldhome.Ui.COLUMNS;
import static com.jas.shieldhome.Ui.DARK;
import static com.jas.shieldhome.Ui.DOCK_GAP;
import static com.jas.shieldhome.Ui.DOCK_W;
import static com.jas.shieldhome.Ui.FOLDER_COLUMNS;
import static com.jas.shieldhome.Ui.FOLDER_H;
import static com.jas.shieldhome.Ui.FOLDER_W;
import static com.jas.shieldhome.Ui.GRID_GAP;
import static com.jas.shieldhome.Ui.GRID_H;
import static com.jas.shieldhome.Ui.GRID_W;
import static com.jas.shieldhome.Ui.ICON_IDLE;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.NEUTRAL_GLOW;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.VPN_OFF;
import static com.jas.shieldhome.Ui.VPN_ON;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.dominantColor;
import static com.jas.shieldhome.Ui.timeOfDayColors;

/** The home screen itself: top bar, tabs, the Home and Apps pages, and app tiles. */
public class MainActivity extends Activity {

    static final int PAGE_HOME = 0, PAGE_DISCOVER = 1, PAGE_APPS = 2;
    private static final long IDLE_AFTER_MS = 20 * 1000L;

    // Shared with the other parts of the app
    PackageManager pm;
    SharedPreferences prefs;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Map<String, ResolveInfo> installed = new LinkedHashMap<>();
    final Images images = new Images();
    Layout layout;
    DiscoverPage discover;
    SearchScreen search;
    WeatherClock weather;
    VpnStatus vpn;
    Menus menus;
    Wallpaper wallpaper;

    LinearLayout content;
    TextView titleView, metaView, weatherView, placeView, vpnCityView, clockPlaceView;
    TextClock timeClock, dateClock;
    GradientDrawable vpnDotBg;
    int page = PAGE_HOME;

    // Screen state
    private ScrollView scroll;
    private LinearLayout weatherRow;
    private TextView hintView;
    private TextView tabHome, tabDiscover, tabApps;
    private GradientDrawable baseBg, glow;
    private int glowColor = 0x00000000;
    private ValueAnimator glowAnim;
    private final Map<String, View> tileByPkg = new HashMap<>();
    private final Map<String, Integer> colorByPkg = new HashMap<>();
    private final List<View> allTiles = new ArrayList<>();
    private final Map<View, Float> baseAlpha = new HashMap<>();
    private View firstTile;
    private String installedSignature = "";
    private String movingPkg = null;
    private boolean swallowBackUp = false;
    private boolean swallowPlayUp = false;
    private boolean idle = false;
    private List<String> backupDock = new ArrayList<>();
    private List<String> backupGrid = new ArrayList<>();
    private BroadcastReceiver packageReceiver;

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            weather.refreshIfDue();
            if (baseBg != null) baseBg.setColors(timeOfDayColors());
            handler.postDelayed(this, 5 * 60 * 1000L);
        }
    };

    private final Runnable vpnTick = new Runnable() {
        @Override
        public void run() {
            vpn.update(false);
            handler.postDelayed(this, VpnStatus.EVERY_MS);
        }
    };

    private final Runnable idleRunnable = new Runnable() {
        @Override
        public void run() {
            goIdle();
        }
    };

    // Runs shortly after an app is installed, updated or removed
    private final Runnable packageRefresh = new Runnable() {
        @Override
        public void run() {
            if (movingPkg == null) refreshInstalled();
        }
    };

    // ---------- Lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        layout = new Layout(prefs, installed);
        discover = new DiscoverPage(this);
        search = new SearchScreen(this);
        weather = new WeatherClock(this);
        vpn = new VpnStatus(this);
        menus = new Menus(this);
        wallpaper = new Wallpaper(this);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        baseBg.setColors(timeOfDayColors());
        refreshInstalled();
        registerPackageReceiver();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
        vpn.register();
        handler.removeCallbacks(vpnTick);
        handler.post(vpnTick);
        discover.stopTicker();
        if (page == PAGE_DISCOVER) discover.startTicker();
        wallpaper.start();
        resetIdle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(vpnTick);
        handler.removeCallbacks(idleRunnable);
        handler.removeCallbacks(packageRefresh);
        discover.stopTicker();
        wallpaper.stop();
        vpn.unregister();
        unregisterPackageReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (movingPkg != null) finishMove(false);
        if (page != PAGE_HOME) {
            page = PAGE_HOME;
            styleTabs();
            render(null);
        }
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (page != PAGE_HOME) {
            showPage(PAGE_HOME);
            if (tabHome != null) tabHome.requestFocus();
        }
        // Otherwise this is the home screen, so Back does nothing.
    }

    private static boolean isPlayKey(int code) {
        return code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) resetIdle();
        if (swallowBackUp && e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            swallowBackUp = false;
            return true;
        }

        // Play/Pause on Home opens the info card for the film or series on the wallpaper
        if (isPlayKey(e.getKeyCode())) {
            if (e.getAction() == KeyEvent.ACTION_UP && swallowPlayUp) {
                swallowPlayUp = false;
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0
                    && movingPkg == null && page != PAGE_DISCOVER) {
                Item it = wallpaper.currentItem();
                if (it != null) {
                    swallowPlayUp = true;
                    discover.openInfo(it);
                    return true;
                }
            }
        }

        if (movingPkg != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            boolean inDock = layout.dock.contains(movingPkg);
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

    // ---------- Watching for app installs and updates ----------

    private void registerPackageReceiver() {
        if (packageReceiver != null) return;
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                handler.removeCallbacks(packageRefresh);
                handler.postDelayed(packageRefresh, 1500);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        try {
            registerReceiver(packageReceiver, f);
        } catch (Exception e) {
            packageReceiver = null;
        }
    }

    private void unregisterPackageReceiver() {
        if (packageReceiver == null) return;
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
        packageReceiver = null;
    }

    // ---------- Building the screen ----------

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        baseBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, timeOfDayColors());
        root.setBackground(baseBg);

        // Artwork wallpaper (Home only), behind everything else
        root.addView(wallpaper.view(), new FrameLayout.LayoutParams(MATCH, MATCH));

        // Soft glow that takes the colour of the selected item
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.15f, 0.2f);
        glow.setGradientRadius(dp(620));
        glow.setColors(new int[]{0x00000000, 0x00000000});
        View glowView = new View(this);
        glowView.setBackground(glow);
        root.addView(glowView, new FrameLayout.LayoutParams(MATCH, MATCH));

        // The column clips its children, so scrolled content never draws over the header
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        FrameLayout header = new FrameLayout(this);
        header.setPadding(dp(40), dp(22), dp(48), dp(4));
        header.setClipChildren(false);
        header.setClipToPadding(false);
        column.addView(header, new LinearLayout.LayoutParams(MATCH, dp(176)));

        // Top left: search, tabs, then weather (Home only)
        LinearLayout topLeft = new LinearLayout(this);
        topLeft.setOrientation(LinearLayout.VERTICAL);
        topLeft.setClipChildren(false);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setClipChildren(false);
        FrameLayout searchBtn = roundButton(R.drawable.ic_search, () -> search.open());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(30), dp(30));
        slp.rightMargin = dp(12);
        tabs.addView(searchBtn, slp);
        tabHome = makeTab("Home", PAGE_HOME);
        tabDiscover = makeTab("Discover", PAGE_DISCOVER);
        tabApps = makeTab("Apps", PAGE_APPS);
        LinearLayout.LayoutParams tlp1 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp1.rightMargin = dp(8);
        tabs.addView(tabHome, tlp1);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp2.rightMargin = dp(8);
        tabs.addView(tabDiscover, tlp2);
        tabs.addView(tabApps, new LinearLayout.LayoutParams(WRAP, WRAP));
        topLeft.addView(tabs, new LinearLayout.LayoutParams(WRAP, WRAP));

        // Weather: one line; highlight it and press OK for the full forecast
        weatherRow = new LinearLayout(this);
        weatherRow.setOrientation(LinearLayout.HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherRow.setPadding(dp(8), dp(4), dp(12), dp(4));
        final GradientDrawable weatherBg = new GradientDrawable();
        weatherBg.setCornerRadius(dp(14));
        weatherBg.setColor(0x00000000);
        weatherRow.setBackground(weatherBg);
        weatherRow.setFocusable(true);
        weatherRow.setClickable(true);
        weatherRow.setOnFocusChangeListener((v, f) -> {
            weatherBg.setColor(f ? 0x33FFFFFF : 0x00000000);
            if (f) topRowFocused();
        });
        weatherRow.setOnClickListener(v -> weather.openForecast());
        weatherView = new TextView(this);
        style(weatherView, 18, false, 0xFFFFFFFF);
        placeView = new TextView(this);
        style(placeView, 16, false, SOFT_TEXT);
        placeView.setPadding(dp(10), 0, 0, 0);
        weatherRow.addView(weatherView, new LinearLayout.LayoutParams(WRAP, WRAP));
        weatherRow.addView(placeView, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        wlp.topMargin = dp(6);
        topLeft.addView(weatherRow, wlp);

        header.addView(topLeft, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START));

        // Bottom left: selected item name, details line (Discover and Apps) and moving hint.
        // Fixed widths so long names fit and only very long ones get "..." at the end.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(this);
        style(titleView, 40, true, 0xFFFFFFFF);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setText(" ");
        metaView = new TextView(this);
        style(metaView, 16, false, SOFT_TEXT);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setVisibility(View.GONE);
        hintView = new TextView(this);
        style(hintView, 16, false, 0xFFFFD54F);
        hintView.setSingleLine(true);
        hintView.setEllipsize(TextUtils.TruncateAt.END);
        hintView.setText("Moving: use the arrows, then OK to drop it. Back cancels.");
        hintView.setVisibility(View.GONE);
        left.addView(titleView, new LinearLayout.LayoutParams(dp(620), WRAP));
        left.addView(metaView, new LinearLayout.LayoutParams(dp(620), WRAP));
        left.addView(hintView, new LinearLayout.LayoutParams(dp(620), WRAP));
        header.addView(left, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START));

        // Top right: VPN city (only when connected), VPN button, settings cog, clock
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setClipChildren(false);

        vpnCityView = new TextView(this);
        style(vpnCityView, 15, false, SOFT_TEXT);
        vpnCityView.setSingleLine(true);
        vpnCityView.setEllipsize(TextUtils.TruncateAt.END);
        vpnCityView.setMaxEms(12);
        vpnCityView.setVisibility(View.GONE);
        LinearLayout.LayoutParams vclp = new LinearLayout.LayoutParams(WRAP, WRAP);
        vclp.topMargin = dp(13);
        vclp.rightMargin = dp(8);
        right.addView(vpnCityView, vclp);

        FrameLayout vpnBtn = roundButton(R.drawable.ic_shield, () -> vpn.openApp());
        vpnDotBg = new GradientDrawable();
        vpnDotBg.setShape(GradientDrawable.OVAL);
        vpnDotBg.setColor(VPN_OFF);
        vpnDotBg.setStroke(dp(2), DARK);
        View vpnDot = new View(this);
        vpnDot.setBackground(vpnDotBg);
        vpnBtn.addView(vpnDot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.BOTTOM | Gravity.END));

        FrameLayout cogBtn = roundButton(R.drawable.ic_cog, this::openSettingsMenu);

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
        timeClock = new TextClock(this);
        timeClock.setFormat12Hour("HH:mm");
        timeClock.setFormat24Hour("HH:mm");
        timeClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        timeClock.setTextColor(0xFFFFFFFF);
        timeClock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeClock.setGravity(Gravity.END);
        timeClock.setIncludeFontPadding(false);
        dateClock = new TextClock(this);
        dateClock.setFormat12Hour("EEEE d MMMM");
        dateClock.setFormat24Hour("EEEE d MMMM");
        style(dateClock, 18, false, SOFT_TEXT);
        dateClock.setGravity(Gravity.END);
        dateClock.setPadding(0, dp(4), 0, 0);
        clockPlaceView = new TextView(this);
        style(clockPlaceView, 14, false, SOFT_TEXT);
        clockPlaceView.setGravity(Gravity.END);
        clockPlaceView.setSingleLine(true);
        clockPlaceView.setPadding(0, dp(2), 0, 0);
        clockPlaceView.setVisibility(View.GONE);
        clock.addView(timeClock, new LinearLayout.LayoutParams(WRAP, WRAP));
        clock.addView(dateClock, new LinearLayout.LayoutParams(WRAP, WRAP));
        clock.addView(clockPlaceView, new LinearLayout.LayoutParams(WRAP, WRAP));
        right.addView(clock, new LinearLayout.LayoutParams(WRAP, WRAP));

        header.addView(right, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END));

        // Scrolling area underneath the header
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(28));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(10), dp(32), dp(60));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        column.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        setContentView(root);
        styleTabs();
        weather.showPlaceholder();
        weather.applyClockZone();
    }

    private TextView makeTab(final String label, final int target) {
        final TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setPadding(dp(16), dp(5), dp(16), dp(6));
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setOnFocusChangeListener((v, f) -> {
            styleTabs();
            if (f) topRowFocused();
        });
        tv.setOnClickListener(v -> showPage(target));
        return tv;
    }

    private void styleTabs() {
        if (tabHome == null || tabDiscover == null || tabApps == null) return;
        styleTab(tabHome, page == PAGE_HOME);
        styleTab(tabDiscover, page == PAGE_DISCOVER);
        styleTab(tabApps, page == PAGE_APPS);
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

    private FrameLayout roundButton(int iconRes, final Runnable onClick) {
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
            if (f) topRowFocused();
        });
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** The top row (search, tabs, weather, VPN, cog) leaves the big title blank. */
    private void topRowFocused() {
        titleView.setText(" ");
        metaView.setText("");
        setGlow(NEUTRAL_GLOW);
    }

    void setGlow(int color) {
        // Much softer over the wallpaper, so the artwork keeps its own colours
        int strength = (wallpaper != null && wallpaper.isShowingPicture()) ? 0x1F000000 : 0x55000000;
        int target = (color & 0x00FFFFFF) | strength;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofArgb(glowColor, target);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(an -> {
            glowColor = (int) an.getAnimatedValue();
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

    // ---------- Installed apps ----------

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
        Collections.sort(names, (x, y) -> labels.get(x).compareToIgnoreCase(labels.get(y)));

        // Include where each app is installed, which changes when an app updates,
        // so updated apps get fresh names and banners
        StringBuilder sb = new StringBuilder();
        for (String p : names) {
            String dir = found.get(p).activityInfo.applicationInfo.sourceDir;
            sb.append(p).append('@').append(dir).append(';');
        }
        String sig = sb.toString();
        if (sig.equals(installedSignature)) return;
        installedSignature = sig;

        installed.clear();
        colorByPkg.clear();
        for (String p : names) installed.put(p, found.get(p));
        layout.load();
        render(null, page == PAGE_HOME);
    }

    // ---------- Pages ----------

    private void showPage(int target) {
        if (page == target) return;
        page = target;
        movingPkg = null;
        styleTabs();
        if (scroll != null) scroll.scrollTo(0, 0);
        render(null, false);
    }

    void saveAndRender(String focusKey) {
        layout.save();
        render(focusKey);
    }

    void render(String focusPkg) {
        render(focusPkg, true);
    }

    void render(String focusPkg, boolean takeFocus) {
        discover.clearViews();
        content.removeAllViews();
        tileByPkg.clear();
        allTiles.clear();
        baseAlpha.clear();
        idle = false;
        firstTile = null;

        hintView.setVisibility(movingPkg != null ? View.VISIBLE : View.GONE);
        metaView.setText("");
        metaView.setVisibility(page == PAGE_HOME ? View.GONE : View.VISIBLE);

        // Weather and wallpaper on Home only (the weather can only be selected on Home)
        if (weatherRow != null) {
            weatherRow.animate().alpha(page == PAGE_HOME ? 1f : 0f).setDuration(250).start();
            weatherRow.setFocusable(page == PAGE_HOME);
        }
        wallpaper.setShownOnPage(page == PAGE_HOME);

        if (page == PAGE_DISCOVER) {
            discover.render();
            return;
        }

        if (page == PAGE_APPS) renderApps();
        else renderHome();

        if (takeFocus) {
            View target = focusPkg != null ? tileByPkg.get(focusPkg) : null;
            if (target == null) target = firstTile;
            if (target != null) target.requestFocus();
        }
    }

    private void renderHome() {
        // Dock (tiles shrink a little if there are lots of apps, so it always fits)
        if (!layout.dock.isEmpty()) {
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
            int w = Math.min(DOCK_W, avail / layout.dock.size() - 2 * DOCK_GAP);
            if (w < 110) w = 110;
            int h = w * 9 / 16;
            for (String p : layout.dock) band.addView(appTile(p), size(w, h, DOCK_GAP));

            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.addView(band, new FrameLayout.LayoutParams(WRAP, WRAP));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(WRAP, WRAP);
            dlp.topMargin = dp(4);
            content.addView(hs, dlp);
        }

        // Sections (each can hold apps and folders)
        addGroupSection("Watch", Layout.WATCH);
        addGroupSection("Play", Layout.PLAY);
        addGroupSection("Tools", Layout.TOOLS);
    }

    private void addGroupSection(String title, String group) {
        List<String> keys = layout.keysInGroup(group);
        if (keys.isEmpty()) return;
        addHeader(title);
        List<View> tiles = new ArrayList<>();
        for (String k : keys) {
            Folder f = layout.folderForKey(k);
            if (f != null) tiles.add(folderTile(f));
            else if (installed.containsKey(k)) tiles.add(appTile(k));
        }
        addRows(content, tiles, COLUMNS, GRID_W, GRID_H, GRID_GAP);
    }

    private void renderApps() {
        addHeader("All apps (" + installed.size() + ")");
        TextView hint = new TextView(this);
        style(hint, 14, false, SOFT_TEXT);
        hint.setText("A tick means the app is on Home. Hold OK on an app to add it to Home, add it to the dock, or remove it from Home.");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hlp.leftMargin = dp(12);
        hlp.bottomMargin = dp(6);
        content.addView(hint, hlp);

        List<View> tiles = new ArrayList<>();
        for (String pkg : installed.keySet()) tiles.add(appsPageTile(pkg));
        addRows(content, tiles, COLUMNS, GRID_W, GRID_H, GRID_GAP);
    }

    private String homeStatus(String pkg) {
        if (!layout.isOnHome(pkg)) return "Not on Home";
        if (layout.dock.contains(pkg)) return "On Home" + SEP + "Dock";
        Folder f = layout.folderOf(pkg);
        if (f != null) return "On Home" + SEP + Layout.groupName(f.group) + SEP + f.name + " folder";
        return "On Home" + SEP + Layout.groupName(layout.groupOf(pkg));
    }

    // ---------- Layout helpers ----------

    void addHeader(String text) {
        addHeaderTo(content, text);
    }

    void addHeaderTo(LinearLayout parent, String text) {
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

    /** Tiles are registered so they can dim when the screen is idle. */
    View register(View t) {
        allTiles.add(t);
        baseAlpha.put(t, 1f);
        return t;
    }

    void unregister(View t) {
        allTiles.remove(t);
        baseAlpha.remove(t);
    }

    // ---------- Tiles ----------

    private View appTile(final String pkg) {
        View t = buildAppTile(pkg, () -> launch(pkg));
        t.setOnLongClickListener(v -> {
            menus.appMenu(pkg);
            return true;
        });
        tileByPkg.put(pkg, t);
        if (firstTile == null) firstTile = t;
        return register(t);
    }

    private View appsPageTile(final String pkg) {
        FrameLayout t = (FrameLayout) buildAppTile(pkg, () -> launch(pkg));

        if (layout.isOnHome(pkg)) {
            TextView tick = new TextView(this);
            tick.setText("\u2713");
            style(tick, 11, true, DARK);
            tick.setGravity(Gravity.CENTER);
            GradientDrawable tb = new GradientDrawable();
            tb.setShape(GradientDrawable.OVAL);
            tb.setColor(VPN_ON);
            tick.setBackground(tb);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.END);
            lp.setMargins(0, dp(5), dp(5), 0);
            t.addView(tick, lp);
        }

        final View.OnFocusChangeListener base = t.getOnFocusChangeListener();
        t.setOnFocusChangeListener((v, f) -> {
            if (base != null) base.onFocusChange(v, f);
            if (f) metaView.setText(homeStatus(pkg));
        });
        t.setOnLongClickListener(v -> {
            menus.appsPageMenu(pkg);
            return true;
        });
        tileByPkg.put(pkg, t);
        if (firstTile == null) firstTile = t;
        return register(t);
    }

    View buildAppTile(String pkg, Runnable onClick) {
        ResolveInfo ri = installed.get(pkg);
        String label = ri.loadLabel(pm).toString();
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        return tile(label, banner, ri.loadIcon(pm), pkg.equals(movingPkg), pkg, onClick);
    }

    private View folderTile(final Folder f) {
        final String key = Layout.keyOf(f);
        FrameLayout t = (FrameLayout) tile(f.name, null, null, key.equals(movingPkg), null, () -> openFolder(f));
        t.removeAllViews();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        LinearLayout row1 = new LinearLayout(this);
        LinearLayout row2 = new LinearLayout(this);
        int shown = 0;
        for (String p : f.apps) {
            if (shown >= 6) break;
            ResolveInfo ri = installed.get(p);
            if (ri == null) continue;
            Drawable d = ri.activityInfo.loadBanner(pm);
            if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
            if (d == null) d = ri.loadIcon(pm);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(18));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            (shown < 3 ? row1 : row2).addView(iv, lp);
            shown++;
        }
        col.addView(row1, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(row2, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView label = new TextView(this);
        style(label, 13, false, 0xFFFFFFFF);
        label.setText(f.name + " (" + f.apps.size() + ")");
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(6), dp(4), dp(6), 0);
        col.addView(label, new LinearLayout.LayoutParams(MATCH, WRAP));

        t.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.setOnLongClickListener(v -> {
            menus.folderMenu(f);
            return true;
        });
        tileByPkg.put(key, t);
        if (firstTile == null) firstTile = t;
        return register(t);
    }

    void openFolder(final Folder f) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(12), dp(8), dp(12));
        box.setClipChildren(false);
        box.setClipToPadding(false);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        final AlertDialog[] holder = new AlertDialog[1];
        List<View> views = new ArrayList<>();
        for (final String p : new ArrayList<>(f.apps)) {
            if (!installed.containsKey(p)) continue;
            View t = buildAppTile(p, () -> {
                if (holder[0] != null) holder[0].dismiss();
                launch(p);
            });
            t.setOnLongClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                menus.appMenu(p);
                return true;
            });
            views.add(t);
        }
        addRows(box, views, FOLDER_COLUMNS, FOLDER_W, FOLDER_H, GRID_GAP);

        holder[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(f.name)
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

    TextView pillButton(String label, final Runnable onClick, final Runnable onFocus) {
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

    // ---------- Moving apps and folders ----------

    void startMove(String key) {
        movingPkg = key;
        backupDock = new ArrayList<>(layout.dock);
        backupGrid = new ArrayList<>(layout.grid);
        render(key);
    }

    private void finishMove(boolean keep) {
        if (!keep) {
            layout.dock.clear();
            layout.dock.addAll(backupDock);
            layout.grid.clear();
            layout.grid.addAll(backupGrid);
        }
        String p = movingPkg;
        movingPkg = null;
        if (keep) layout.save();
        render(p);
    }

    private void moveBy(int delta) {
        if (layout.moveBy(movingPkg, delta)) render(movingPkg);
    }

    // ---------- Idle dimming ----------

    private void resetIdle() {
        handler.removeCallbacks(idleRunnable);
        handler.postDelayed(idleRunnable, IDLE_AFTER_MS);
        if (idle) wake();
    }

    private void goIdle() {
        if (movingPkg != null || !hasWindowFocus()) return;
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
            Float al = baseAlpha.get(t);
            t.animate().alpha(al == null ? 1f : al).setDuration(200).start();
        }
        if (f != null && allTiles.contains(f)) f.animate().scaleX(1.12f).scaleY(1.12f).setDuration(200).start();
    }

    // ---------- Settings menu ----------

    private void openSettingsMenu() {
        final boolean wallpaperOn = wallpaper.isOn();
        String[] opts = {
                "Shield settings",
                "Clock time zone",
                "Weather location",
                "Home wallpaper: " + (wallpaperOn ? "On" : "Off"),
                "Backup and restore",
                "About"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Settings")
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        startSafe(new Intent(Settings.ACTION_SETTINGS));
                    } else if (which == 1) {
                        weather.askClockZone();
                    } else if (which == 2) {
                        weather.askWeatherPlace();
                    } else if (which == 3) {
                        wallpaper.setOn(!wallpaperOn);
                    } else if (which == 4) {
                        new Backup(this).openMenu();
                    } else if (which == 5) {
                        new About(this).show();
                    }
                })
                .show();
    }

    /** After a restore, reload everything from the restored settings. */
    void reloadAfterRestore() {
        installedSignature = "";
        page = PAGE_HOME;
        styleTabs();
        refreshInstalled();
        weather.showPlaceholder();
        weather.fetch();
        weather.applyClockZone();
        wallpaper.start();
    }

    // ---------- Small helpers ----------

    void launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) startSafe(i);
    }

    void startSafe(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    LinearLayout.LayoutParams size(int w, int h, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(h));
        lp.setMargins(dp(gap), dp(gap), dp(gap), dp(gap));
        return lp;
    }

    void style(TextView tv, int sp, boolean bold, int color) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
