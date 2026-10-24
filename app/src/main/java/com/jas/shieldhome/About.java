package com.jas.shieldhome;

import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.jas.shieldhome.Ui.SOFT_TEXT;
import static com.jas.shieldhome.Ui.WRAP;

/**
 * Cog → About: credits for the services Sangha Home uses.
 * TMDB asks for its logo and notice in an About or Credits section.
 * The logo is the file app/src/main/res/drawable/tmdb_logo.png. If it isn't there yet,
 * the About box still works, just without the logo.
 */
final class About {

    private final MainActivity a;

    About(MainActivity a) {
        this.a = a;
    }

    void show() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(24), a.dp(12), a.dp(24), a.dp(8));

        int logo = a.getResources().getIdentifier("tmdb_logo", "drawable", a.getPackageName());
        if (logo != 0) {
            ImageView iv = new ImageView(a);
            iv.setImageResource(logo);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_START);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, a.dp(28));
            lp.bottomMargin = a.dp(14);
            box.addView(iv, lp);
        }

        addText(box, "Film and series details and images come from TMDB. "
                + "This product uses the TMDB API but is not endorsed or certified by TMDB.", 15, 0xFFFFFFFF);
        addText(box, "Where to watch: streaming data from JustWatch.", 14, SOFT_TEXT);
        addText(box, "Weather and place search by Open-Meteo.com.", 14, SOFT_TEXT);
        addText(box, "VPN location by ipwho.is.", 14, SOFT_TEXT);

        String when = installedDate();
        if (!when.isEmpty()) addText(box, "This version was installed on " + when + ".", 13, SOFT_TEXT);

        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("About Sangha Home")
                .setView(box)
                .setPositiveButton("Close", null)
                .show();
    }

    private void addText(LinearLayout box, String text, int sp, int color) {
        TextView t = new TextView(a);
        a.style(t, sp, false, color);
        t.setText(text);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.bottomMargin = a.dp(10);
        box.addView(t, lp);
    }

    private String installedDate() {
        try {
            PackageInfo info = a.pm.getPackageInfo(a.getPackageName(), 0);
            return new SimpleDateFormat("d MMMM yyyy 'at' HH:mm", Locale.UK).format(new Date(info.lastUpdateTime));
        } catch (Exception e) {
            return "";
        }
    }
}
