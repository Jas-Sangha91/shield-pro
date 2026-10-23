package com.jas.shieldhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    // ===== Apps in the dock, in order. Edit this list to change the dock. =====
    private static final String[] DOCK = {
            "com.netflix.ninja",              // Netflix
            "com.google.android.youtube.tv",  // YouTube
            "com.disney.disneyplus",          // Disney+
            "com.plexapp.android",            // Plex
            "com.nvidia.bbciplayer"           // BBC iPlayer
    };

    private static final int COLUMNS = 5;
    private static final int TILE_W = 144, TILE_H = 81, GAP = 10;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private PackageManager pm;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView titleView;
    private View firstTile;
    private String lastSignature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<ResolveInfo> apps = loadApps();
        StringBuilder sig = new StringBuilder();
        for (ResolveInfo ri : apps) sig.append(ri.activityInfo.packageName).append(';');
        if (!sig.toString().equals(lastSignature)) {
            lastSignature = sig.toString();
            populate(apps);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Pressing Home while already here jumps back to the start of the dock.
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (firstTile != null) firstTile.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // This is the home screen, so Back does nothing.
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2130, 0xFF07080C}));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(40), dp(24), dp(40), dp(60));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("HH:mm");
        time.setFormat24Hour("HH:mm");
        style(time, 34, true, 0xFFFFFFFF);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        style(date, 14, false, 0xA0FFFFFF);
        clock.addView(time);
        clock.addView(date);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(28), dp(48), 0);
        root.addView(clock, clp);

        setContentView(root);
    }

    private void populate(List<ResolveInfo> apps) {
        content.removeAllViews();
        firstTile = null;

        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : apps) byPkg.put(ri.activityInfo.packageName, ri);

        // Big name of the selected app, Apple TV style
        titleView = new TextView(this);
        style(titleView, 40, true, 0xFFFFFFFF);
        titleView.setText(" ");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tlp.topMargin = dp(140);
        tlp.bottomMargin = dp(14);
        tlp.leftMargin = dp(8);
        content.addView(titleView, tlp);

        // Dock
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(dp(16), dp(10), dp(16), dp(10));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        GradientDrawable band = new GradientDrawable();
        band.setColor(0x1FFFFFFF);
        band.setCornerRadius(dp(24));
        dock.setBackground(band);

        Set<String> inDock = new HashSet<>();
        for (String pkg : DOCK) {
            ResolveInfo ri = byPkg.get(pkg);
            if (ri == null) continue;
            inDock.add(pkg);
            View t = appTile(ri);
            dock.addView(t, tileParams());
            if (firstTile == null) firstTile = t;
        }
        if (dock.getChildCount() > 0) {
            content.addView(dock, new LinearLayout.LayoutParams(WRAP, WRAP));
        }

        // All other apps
        TextView header = new TextView(this);
        style(header, 16, true, 0x99FFFFFF);
        header.setText("All apps");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hlp.topMargin = dp(36);
        hlp.bottomMargin = dp(6);
        hlp.leftMargin = dp(16);
        content.addView(header, hlp);

        List<View> tiles = new ArrayList<>();
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (inDock.contains(pkg) || pkg.equals(getPackageName())) continue;
            tiles.add(appTile(ri));
        }
        tiles.add(tile("Settings", null, null, () -> {
            Intent i = new Intent(Settings.ACTION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) { }
        }));

        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(16), 0, dp(16), 0);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                content.addView(row, new LinearLayout.LayoutParams(WRAP, WRAP));
            }
            row.addView(tiles.get(i), tileParams());
        }

        if (firstTile == null && !tiles.isEmpty()) firstTile = tiles.get(0);
        if (firstTile != null) firstTile.requestFocus();
    }

    private List<ResolveInfo> loadApps() {
        Map<String, ResolveInfo> map = new LinkedHashMap<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(tv, 0)) {
            map.putIfAbsent(ri.activityInfo.packageName, ri);
        }
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phone, 0)) {
            map.putIfAbsent(ri.activityInfo.packageName, ri);
        }
        List<ResolveInfo> out = new ArrayList<>(map.values());
        final Map<ResolveInfo, String> labels = new LinkedHashMap<>();
        for (ResolveInfo ri : out) labels.put(ri, ri.loadLabel(pm).toString());
        Collections.sort(out, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        return out;
    }

    private View appTile(ResolveInfo ri) {
        final String pkg = ri.activityInfo.packageName;
        String label = ri.loadLabel(pm).toString();
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        Drawable icon = ri.loadIcon(pm);
        return tile(label, banner, icon, () -> launch(pkg));
    }

    private View tile(final String label, Drawable banner, Drawable icon, final Runnable onClick) {
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
        ring.setStroke(dp(3), 0xFFFFFFFF);
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

    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        lp.setMargins(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        return lp;
    }

    private void launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
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
