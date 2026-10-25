package com.jas.shieldhome;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.jas.shieldhome.Tmdb.Item;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.jas.shieldhome.Ui.WRAP;
import static com.jas.shieldhome.Ui.str;

/**
 * Which languages the launcher shows: English, Hindi and Punjabi, plus big international hits
 * (titles with at least HIT_VOTES ratings on TMDB, which are the most likely to have a dub).
 * International titles get a small language label so you know to check the audio.
 */
final class Languages {

    private Languages() {
    }

    // Original languages always shown (ISO 639-1 codes)
    static final String[] MINE = {"en", "hi", "pa"};

    // Titles in other languages are shown only if this many people have rated them on TMDB
    static final int HIT_VOTES = 1000;

    // Remembers each title's original language, so labels can be shown later
    private static final Map<String, String> known = Collections.synchronizedMap(new HashMap<>());

    static boolean isMine(String code) {
        if (code == null || code.isEmpty()) return true;
        for (String m : MINE) if (m.equals(code)) return true;
        return false;
    }

    /** True if a raw TMDB result should be shown. */
    static boolean keep(JSONObject raw) {
        if (raw == null) return false;
        String code = str(raw, "original_language");
        if (isMine(code)) return true;
        return raw.optInt("vote_count", 0) >= HIT_VOTES;
    }

    /** Remembers the original language of a title. */
    static void remember(Item it, JSONObject raw) {
        if (it == null || raw == null) return;
        String code = str(raw, "original_language");
        if (!code.isEmpty()) known.put(it.key(), code);
    }

    /** "Korean", "Japanese" and so on, or null for English, Hindi, Punjabi or unknown. */
    static String foreignName(Item it) {
        if (it == null) return null;
        return foreignNameFor(known.get(it.key()));
    }

    static String foreignNameFor(String code) {
        if (code == null || isMine(code)) return null;
        if (code.equals("cn")) return "Cantonese";
        if (code.equals("zh")) return "Mandarin";
        String name = new Locale(code).getDisplayLanguage(Locale.ENGLISH);
        if (name == null || name.isEmpty() || name.equalsIgnoreCase(code)) return code.toUpperCase(Locale.ENGLISH);
        return name;
    }

    /** Adds a small language label to the bottom left of a poster, for international titles. */
    static void tag(MainActivity a, View poster, Item it) {
        String name = foreignName(it);
        if (name == null || !(poster instanceof FrameLayout)) return;
        TextView t = new TextView(a);
        a.style(t, 10, true, 0xFFFFFFFF);
        t.setText(name);
        t.setPadding(a.dp(6), a.dp(2), a.dp(6), a.dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(6));
        bg.setColor(0xCC000000);
        t.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
        lp.setMargins(a.dp(5), 0, 0, a.dp(5));
        ((FrameLayout) poster).addView(t, lp);
    }
}
