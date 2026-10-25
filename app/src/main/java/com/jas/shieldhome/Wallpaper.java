package com.jas.shieldhome;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.jas.shieldhome.Tmdb.Item;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jas.shieldhome.Tmdb.TMDB_IMG;
import static com.jas.shieldhome.Tmdb.hasToken;
import static com.jas.shieldhome.Tmdb.meta;
import static com.jas.shieldhome.Tmdb.parseItem;
import static com.jas.shieldhome.Tmdb.tmdbGet;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.WRAP;

/**
 * Artwork behind the screen, Netflix style.
 * Home: artwork rotates every 30 seconds (your languages plus big international hits), with a caption.
 * Discover: the artwork follows whichever poster is highlighted. Apps: plain background.
 */
final class Wallpaper {

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;
    private static final long FOLLOW_FADE_MS = 700L;
    private static final long FOLLOW_DELAY_MS = 350L;

    private static final int BG = 0xFF0A0D14;
    private static final float ART_HEIGHT = 0.72f;

    private static final int CAP_TITLE = 0xF2F1F1F1;
    private static final int CAP_DETAILS = 0xBFF1F1F1;
    private static final int CAP_HINT = 0x80F1F1F1;
    private static final String GAP = "  \u00B7  ";

    // Where Home artwork comes from: {request, type}
    private static final String[][] SOURCES = {
            {"/trending/all/week?language=en-US&page=1", null},
            {"/trending/all/week?language=en-US&page=2", null},
            {"/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=hi&vote_count.gte=20", "movie"},
            {"/discover/tv?language=en-US&sort_by=popularity.desc&with_original_language=hi&vote_count.gte=10", "tv"},
            {"/discover/movie?language=en-US&sort_by=popularity.desc&with_original_language=pa&vote_count.gte=3", "movie"}};

    private final MainActivity a;
    private final FrameLayout root;
    private final ImageView[] layers = new ImageView[2];
    private final TextView caption;
    private int front = 0;
    private boolean hasImage = false;
    private boolean running = false;
    private int lastPage = MainActivity.PAGE_HOME;

    // Home rotation
    private final List<Item> items = new ArrayList<>();
    private int index = -1;
    private Item shown = null;
    private long loadedAt = 0;
    private boolean loading = false;

    // Discover follow
    private Item pendingFollow = null;
    private String followKey = null;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!rotating()) return;
            if (items.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
            else next();
            a.handler.postDelayed(this, EVERY_MS);
        }
    };

    private final Runnable followRun = new Runnable() {
        @Override
        public void run() {
            final Item it = pendingFollow;
            if (it == null || !following()) return;
            if (it.key().equals(followKey)) return;
            a.images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
                if (b == null || !following() || pendingFollow != it) return;
                followKey = it.key();
                crossfade(b, FOLLOW_FADE_MS);
                hasImage = true;
                root.animate().alpha(1f).setDuration(500).start();
            });
        }
    };

    Wallpaper(MainActivity a) {
        this.a = a;
        root = new FrameLayout(a);
        root.setAlpha(0f);
        root.setBackgroundColor(BG);

        int artHeight = Math.round(a.getResources().getDisplayMetrics().heightPixels * ART_HEIGHT);
        FrameLayout art = new FrameLayout(a);
        root.addView(art, new FrameLayout.LayoutParams(MATCH, artHeight, Gravity.TOP));

        for (int i = 0; i < 2; i++) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAlpha(0f);
            art.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            layers[i] = iv;
        }

        View dim = new View(a);
        dim.setBackgroundColor(0x330A0D14);
        art.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        View left = new View(a);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20A0D14, 0xA60A0D14, 0x1A0A0D14, 0x000A0D14}));
        art.addView(left, new FrameLayout.LayoutParams(MATCH, MATCH));

        View top = new View(a);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD90A0D14, 0x660A0D14, 0x000A0D14}));
        art.addView(top, new FrameLayout.LayoutParams(MATCH, Math.round(artHeight * 0.45f), Gravity.TOP));

        View clockShade = new View(a);
        GradientDrawable patch = new GradientDrawable();
        patch.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        patch.setGradientCenter(0.88f, 0.12f);
        patch.setGradientRadius(a.dp(340));
        patch.setColors(new int[]{0xCC0A0D14, 0x660A0D14, 0x000A0D14});
        clockShade.setBackground(patch);
        art.addView(clockShade, new FrameLayout.LayoutParams(MATCH, MATCH));

        View bottom = new View(a);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000A0D14, 0x990A0D14, BG}));
        art.addView(bottom, new FrameLayout.LayoutParams(MATCH, artHeight / 2, Gravity.BOTTOM));

        caption = new TextView(a);
        a.style(caption, 13, false, CAP_DETAILS);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setMaxWidth(a.dp(560));
        caption.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        caption.setAlpha(0f);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START);
        clp.setMargins(a.dp(48), a.dp(98), 0, 0);
        root.addView(caption, clp);
    }

    View view() {
        return root;
    }

    boolean isOn() {
        return a.prefs.getBoolean("wallpaper_on", true);
    }

    void setOn(boolean on) {
        a.prefs.edit().putBoolean("wallpaper_on", on).apply();
        update();
    }

    void start() {
        running = true;
        update();
    }

    void stop() {
        running = false;
        a.handler.removeCallbacks(tick);
        a.handler.removeCallbacks(followRun);
    }

    void setShownOnPage(boolean ignored) {
        if (a.page == lastPage) return;
        lastPage = a.page;
        if (a.page == MainActivity.PAGE_DISCOVER) {
            pendingFollow = null;
            followKey = null;
        }
        update();
        if (rotating() && !items.isEmpty()) next();
    }

    void follow(Item it) {
        if (it == null || it.backdrop == null || it.backdrop.isEmpty()) return;
        pendingFollow = it;
        a.handler.removeCallbacks(followRun);
        a.handler.postDelayed(followRun, FOLLOW_DELAY_MS);
    }

    boolean isShowingPicture() {
        return (rotating() && hasImage) || (following() && followKey != null);
    }

    Item currentItem() {
        return rotating() && hasImage ? shown : null;
    }

    private boolean rotating() {
        return running && a.page == MainActivity.PAGE_HOME && isOn() && hasToken();
    }

    private boolean following() {
        return running && a.page == MainActivity.PAGE_DISCOVER && hasToken();
    }

    private void update() {
        a.handler.removeCallbacks(tick);

        if (following()) {
            caption.animate().alpha(0f).setDuration(300).start();
            root.animate().alpha(followKey != null ? 1f : 0f).setDuration(400).start();
            return;
        }

        if (!rotating()) {
            a.handler.removeCallbacks(followRun);
            root.animate().alpha(0f).setDuration(400).start();
            return;
        }

        if (hasImage) root.animate().alpha(1f).setDuration(400).start();
        if (items.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
        else if (!hasImage) next();
        a.handler.postDelayed(tick, EVERY_MS);
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            final List<Item> found = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String[] s : SOURCES) {
                try {
                    JSONArray res = new JSONObject(tmdbGet(s[0])).getJSONArray("results");
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject raw = res.getJSONObject(i);
                        if (!Languages.keep(raw)) continue;
                        Item it = parseItem(raw, s[1]);
                        if (it == null || it.backdrop.isEmpty() || !seen.add(it.key())) continue;
                        Languages.remember(it, raw);
                        found.add(it);
                    }
                } catch (Exception ignored) {
                }
            }
            Collections.shuffle(found);
            a.runOnUiThread(() -> {
                loading = false;
                if (found.isEmpty()) return;
                items.clear();
                items.addAll(found);
                loadedAt = System.currentTimeMillis();
                index = -1;
                if (rotating()) next();
            });
        }).start();
    }

    private void next() {
        if (items.isEmpty()) return;
        index = (index + 1) % items.size();
        final Item it = items.get(index);
        a.images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
            if (b == null || !rotating()) return;
            crossfade(b, FADE_MS);
            shown = it;

            caption.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                caption.setText(captionFor(it));
                if (rotating()) caption.animate().alpha(1f).setDuration(700).start();
            }).start();

            if (!hasImage) hasImage = true;
            root.animate().alpha(1f).setDuration(600).start();
        });
    }

    private void crossfade(Bitmap b, long ms) {
        ImageView back = layers[1 - front];
        ImageView old = layers[front];
        back.setImageBitmap(b);
        back.animate().alpha(1f).setDuration(ms).start();
        old.animate().alpha(0f).setDuration(ms).start();
        front = 1 - front;
    }

    /** "Title · ★ 8.6 · 2025 · Series · Korean · Play for info". */
    private CharSequence captionFor(Item it) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(it.title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(CAP_TITLE), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append(GAP).append(meta(it).replace(SEP, GAP));
        String lang = Languages.foreignName(it);
        if (lang != null) sb.append(GAP).append(lang);

        start = sb.length();
        sb.append(GAP).append("Play for info");
        sb.setSpan(new ForegroundColorSpan(CAP_HINT), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
