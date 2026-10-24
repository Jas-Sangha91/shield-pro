package com.jas.shieldhome;

import android.app.Dialog;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Ui.DARK;
import static com.jas.shieldhome.Ui.DOT;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;

/**
 * "Where to watch" on the info card: UK streaming services from TMDB.
 * Streaming data comes from JustWatch (credited at the bottom of Discover).
 */
final class WhereToWatch {

    // TMDB's categories, in the order we show them. Blank labels aren't shown on the pill.
    private static final String[][] KINDS = {
            {"flatrate", ""},
            {"free", "Free"},
            {"ads", "Free with ads"},
            {"rent", "Rent"},
            {"buy", "Buy"}};
    private static final int MAX = 8;

    private final MainActivity a;
    private final Dialog dialog;
    private final LinearLayout root;
    private final LinearLayout row;

    WhereToWatch(MainActivity a, Dialog dialog) {
        this.a = a;
        this.dialog = dialog;

        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        TextView title = new TextView(a);
        a.style(title, 15, true, 0xA0FFFFFF);
        title.setText("Where to watch");
        title.setPadding(0, a.dp(14), 0, a.dp(2));
        root.addView(title, new LinearLayout.LayoutParams(WRAP, WRAP));

        row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, a.dp(4), a.dp(8), a.dp(4));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(row, new FrameLayout.LayoutParams(WRAP, WRAP));
        root.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));

        note("Checking\u2026");
    }

    View view() {
        return root;
    }

    void failed() {
        row.removeAllViews();
        note("Couldn't check right now.");
    }

    /** Fills the row from TMDB's "watch/providers" data. */
    void show(JSONObject providers) {
        row.removeAllViews();
        JSONObject gb = null;
        if (providers != null) {
            JSONObject results = providers.optJSONObject("results");
            if (results != null) gb = results.optJSONObject("GB");
        }
        if (gb == null) {
            note("Not available to stream in the UK right now.");
            return;
        }

        // Collect services, one entry per app, best category first
        List<String[]> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String[] kind : KINDS) {
            JSONArray list = gb.optJSONArray(kind[0]);
            if (list == null) continue;
            for (int i = 0; i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                if (p == null) continue;
                String name = str(p, "provider_name");
                if (name.isEmpty()) continue;
                String pkg = findApp(name);
                String id = pkg != null ? pkg : name.toLowerCase(Locale.UK);
                if (!seen.add(id)) continue;
                found.add(new String[]{name, kind[1], str(p, "logo_path"), pkg});
            }
        }
        if (found.isEmpty()) {
            note("Not available to stream in the UK right now.");
            return;
        }

        // Installed apps first, then the rest
        int shown = 0;
        for (String[] f : found) {
            if (f[3] != null && shown < MAX) {
                addPill(f[0], f[1], f[2], f[3]);
                shown++;
            }
        }
        for (String[] f : found) {
            if (f[3] == null && shown < MAX) {
                addPill(f[0], f[1], f[2], null);
                shown++;
            }
        }
    }

    private void note(String text) {
        TextView t = new TextView(a);
        a.style(t, 14, false, SOFT_TEXT);
        t.setText(text);
        row.addView(t, new LinearLayout.LayoutParams(WRAP, WRAP));
    }

    private void addPill(String name, String kind, String logo, final String pkg) {
        final boolean installed = pkg != null;
        final LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(a.dp(8), a.dp(6), a.dp(14), a.dp(6));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(20));
        bg.setColor(0x26FFFFFF);
        pill.setBackground(bg);

        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable lbg = new GradientDrawable();
        lbg.setCornerRadius(a.dp(6));
        lbg.setColor(0xFF2B3140);
        iv.setBackground(lbg);
        iv.setClipToOutline(true);
        if (!logo.isEmpty()) a.images.load(TMDB_IMG + "w92" + logo, iv);
        pill.addView(iv, new LinearLayout.LayoutParams(a.dp(24), a.dp(24)));

        final TextView label = new TextView(a);
        a.style(label, 14, false, 0xFFFFFFFF);
        label.setSingleLine(true);
        label.setText(kind.isEmpty() ? name : name + "  " + DOT + "  " + kind);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
        llp.leftMargin = a.dp(8);
        pill.addView(label, llp);

        if (installed) {
            pill.setFocusable(true);
            pill.setClickable(true);
            pill.setOnFocusChangeListener((v, f) -> {
                bg.setColor(f ? 0xFFFFFFFF : 0x26FFFFFF);
                label.setTextColor(f ? DARK : 0xFFFFFFFF);
                v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
            });
            pill.setOnClickListener(v -> {
                dialog.dismiss();
                a.launch(pkg);
            });
        } else {
            // Dimmed means the app isn't installed
            pill.setAlpha(0.45f);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.rightMargin = a.dp(10);
        row.addView(pill, lp);
    }

    /** Finds the installed app for a streaming service, or null if it isn't installed. */
    private String findApp(String provider) {
        String p = provider.toLowerCase(Locale.UK);
        if (p.contains("netflix")) return installedOrNull("com.netflix.ninja");
        if (p.contains("amazon") || p.contains("prime video")) return installedOrNull("com.amazon.amazonvideo.livingroom");
        if (p.contains("disney")) return installedOrNull("com.disney.disneyplus");
        if (p.contains("youtube")) return installedOrNull("com.google.android.youtube.tv");

        String want = squash(provider);
        if (want.isEmpty()) return null;
        for (Map.Entry<String, ResolveInfo> e : a.installed.entrySet()) {
            String have = squash(e.getValue().loadLabel(a.pm).toString());
            if (have.isEmpty()) continue;
            if (have.equals(want)) return e.getKey();
            if (have.length() >= 4 && want.length() >= 4 && (want.contains(have) || have.contains(want))) {
                return e.getKey();
            }
        }
        return null;
    }

    private String installedOrNull(String pkg) {
        return a.installed.containsKey(pkg) ? pkg : null;
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.UK)
                .replace("+", "plus")
                .replace("with ads", "")
                .replaceAll("[^a-z0-9]", "");
    }
}
