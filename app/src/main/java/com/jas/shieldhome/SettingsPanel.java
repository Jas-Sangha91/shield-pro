package com.jas.shieldhome;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.jas.shieldhome.Ui.DARK;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.WRAP;

/**
 * Cog → the settings panel: a dark panel that slides in from the right, with icons,
 * current values and grouped sections. Back closes it.
 */
final class SettingsPanel {

    private static final int PANEL_W_DP = 420;
    private static final int PANEL_BG = 0xFF151A22;
    private static final int ICON = 0xCCFFFFFF;
    private static final int SUB = 0x8CFFFFFF;
    private static final int SUB_FOCUSED = 0xFF4A4F58;
    private static final String BACKUP_FILE = "sangha-home-backup.json";

    private final MainActivity a;
    private Dialog dialog;

    SettingsPanel(MainActivity a) {
        this.a = a;
    }

    void open() {
        if (dialog != null && dialog.isShowing()) return;

        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0x8C05070A);

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(PANEL_BG);
        panel.setPadding(a.dp(20), a.dp(28), a.dp(20), a.dp(20));
        panel.setClipChildren(false);

        TextView heading = new TextView(a);
        a.style(heading, 24, true, 0xFFFFFFFF);
        heading.setText("Settings");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        hlp.leftMargin = a.dp(14);
        hlp.bottomMargin = a.dp(4);
        panel.addView(heading, hlp);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        sv.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        panel.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        section(list, "Sangha Home");
        final View first = row(list, Icons.SUN, "Weather location", "Shown under the clock", weatherValue(), false,
                () -> {
                    close();
                    a.weather.askWeatherPlace();
                });
        row(list, Icons.CLOCK, "Clock time zone", "This screen only", clockValue(), false,
                () -> {
                    close();
                    a.weather.askClockZone();
                });

        section(list, "Screensaver");
        boolean ours = screensaverIsOurs();
        row(list, Icons.ARTWORK, "Screensaver",
                ours ? "Artwork with title logos" : "OK to choose Sangha Home artwork",
                ours ? "Sangha Home" : "Other", true, this::openScreensaver);

        section(list, "Backup");
        row(list, Icons.BACKUP, "Backup and restore", backupSubtitle(), null, true,
                () -> {
                    close();
                    new Backup(a).openMenu();
                });

        section(list, "Shield");
        row(list, Icons.TV, "Shield settings", "Restart, sleep, network and more", null, true,
                () -> {
                    close();
                    a.startSafe(new Intent(Settings.ACTION_SETTINGS));
                });
        row(list, Icons.INFO, "About", "Credits and version", null, true,
                () -> {
                    close();
                    new About(a).show();
                });

        root.addView(panel, new FrameLayout.LayoutParams(a.dp(PANEL_W_DP), MATCH, Gravity.END));

        dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(MATCH, MATCH);
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();

        // Slide in from the right
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(180).start();
        panel.setTranslationX(a.dp(PANEL_W_DP));
        panel.animate().translationX(0f).setDuration(220).start();
        first.post(first::requestFocus);
    }

    private void close() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    // ---------- Rows ----------

    private void section(LinearLayout list, String text) {
        TextView s = new TextView(a);
        a.style(s, 13, true, 0x73FFFFFF);
        s.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = a.dp(18);
        lp.bottomMargin = a.dp(4);
        lp.leftMargin = a.dp(14);
        list.addView(s, lp);
    }

    private View row(LinearLayout list, int iconKind, String title, String subtitle, String value,
                     boolean chevron, final Runnable onClick) {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10));
        r.setFocusable(true);
        r.setClickable(true);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(14));
        bg.setColor(0x00000000);
        r.setBackground(bg);

        final Drawable icon = Icons.make(iconKind, a.dp(20));
        icon.setTint(ICON);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(a.dp(20), a.dp(20));
        ilp.rightMargin = a.dp(16);
        r.addView(iv, ilp);

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView t = new TextView(a);
        a.style(t, 17, false, 0xFFFFFFFF);
        t.setText(title);
        t.setSingleLine(true);
        texts.addView(t, new LinearLayout.LayoutParams(WRAP, WRAP));

        final TextView sub = subtitle == null ? null : new TextView(a);
        if (sub != null) {
            a.style(sub, 13, false, SUB);
            sub.setText(subtitle);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(sub, new LinearLayout.LayoutParams(WRAP, WRAP));
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));

        final TextView v = value == null ? null : new TextView(a);
        if (v != null) {
            a.style(v, 14, false, SUB);
            v.setText(value);
            v.setSingleLine(true);
            v.setEllipsize(TextUtils.TruncateAt.END);
            v.setMaxWidth(a.dp(140));
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(WRAP, WRAP);
            vlp.leftMargin = a.dp(12);
            r.addView(v, vlp);
        }

        final Drawable chev = chevron ? Icons.make(Icons.CHEVRON, a.dp(14)) : null;
        if (chev != null) {
            chev.setTint(SUB);
            ImageView cv = new ImageView(a);
            cv.setImageDrawable(chev);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(a.dp(14), a.dp(14));
            clp.leftMargin = a.dp(10);
            r.addView(cv, clp);
        }

        r.setOnFocusChangeListener((view, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x00000000);
            t.setTextColor(f ? DARK : 0xFFFFFFFF);
            icon.setTint(f ? DARK : ICON);
            if (sub != null) sub.setTextColor(f ? SUB_FOCUSED : SUB);
            if (v != null) v.setTextColor(f ? SUB_FOCUSED : SUB);
            if (chev != null) chev.setTint(f ? SUB_FOCUSED : SUB);
        });
        r.setOnClickListener(view -> onClick.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = a.dp(2);
        list.addView(r, lp);
        return r;
    }

    // ---------- Current values ----------

    private String weatherValue() {
        return a.prefs.contains("wx_lat") ? a.prefs.getString("wx_name", "Set") : "Not set";
    }

    private String clockValue() {
        return a.prefs.contains("clock_tz") ? a.prefs.getString("clock_name", "Custom") : "Shield's time";
    }

    private boolean screensaverIsOurs() {
        try {
            String c = Settings.Secure.getString(a.getContentResolver(), "screensaver_components");
            return c != null && c.contains(a.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void openScreensaver() {
        close();
        Intent i = new Intent(Settings.ACTION_DREAM_SETTINGS);
        if (i.resolveActivity(a.pm) == null) i = new Intent(Settings.ACTION_SETTINGS);
        a.startSafe(i);
    }

    private String backupSubtitle() {
        try {
            File dir = a.getExternalFilesDir(null);
            File f = dir == null ? null : new File(dir, BACKUP_FILE);
            if (f == null || !f.exists()) return "No backup yet";
            return "Last backup " + new SimpleDateFormat("d MMMM", Locale.UK).format(new Date(f.lastModified()));
        } catch (Exception e) {
            return "Save or restore your layout";
        }
    }
}
