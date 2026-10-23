package com.jas.shieldhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/** Shared colours, sizes and small helpers used across Sangha Home. */
final class Ui {

    private Ui() {
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // Colours
    static final int NEUTRAL_GLOW = 0xFF3A4A6B;
    static final int VPN_ON = 0xFF7BD88F;
    static final int VPN_OFF = 0xFFFFB74D;
    static final int SOFT_TEXT = 0xB0FFFFFF;
    static final int ICON_IDLE = 0xFFC9CDD6;
    static final int DARK = 0xFF141A26;
    static final int SPOT_BG = 0xFF0E1118;

    // Sizes (in dp)
    static final int COLUMNS = 5;
    static final int DOCK_W = 176, DOCK_GAP = 7;
    static final int GRID_W = 152, GRID_H = 86, GRID_GAP = 8;
    static final int FOLDER_COLUMNS = 4;
    static final int FOLDER_W = 128, FOLDER_H = 72;
    static final int POSTER_W = 100, POSTER_H = 150;

    // Text symbols
    static final String DEG = "\u00B0";
    static final String DOT = "\u00B7";
    static final String STAR = "\u2605";
    static final String SEP = "   \u00B7   ";

    /** Background colours that change with the time of day. */
    static int[] timeOfDayColors() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 10) return new int[]{0xFF1F2B3D, 0xFF080B12};   // morning
        if (h >= 10 && h < 17) return new int[]{0xFF1B2130, 0xFF07080C};  // day
        if (h >= 17 && h < 21) return new int[]{0xFF2A1F33, 0xFF0A070E};  // evening
        return new int[]{0xFF10141F, 0xFF030407};                          // night
    }

    /** The strongest colour in an image, used for the glow behind the selected item. */
    static int dominantColor(Drawable src, int fallback) {
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

    /** Reads a text value from JSON, or "" if it's missing. */
    static String str(JSONObject j, String key) {
        if (j == null || !j.has(key) || j.isNull(key)) return "";
        return j.optString(key, "");
    }

    /** Downloads a web address as text. Pass a token for services that need one, otherwise null. */
    static String httpGet(String address, String bearer) throws Exception {
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
}
