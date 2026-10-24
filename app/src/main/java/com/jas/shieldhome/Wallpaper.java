package com.jas.shieldhome;

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
import static com.jas.shieldhome.Ui.DOCK_GAP;
import static com.jas.shieldhome.Ui.DOCK_W;
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.WRAP;

/**
 * Home wallpaper, Netflix style: sharp trending artwork across the top of the screen,
 * fading into a dark background before the lower rows. Darker on the left and top
 * so the tabs, clock and app names stay readable. A one-line caption sits on the right,
 * between the dock and the Watch row; press Play on the remote to open its info card. Home only.
 */
final class Wallpaper {

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;

    // The dark colour the artwork fades into
    private static final int BG = 0xFF0A0D14;

    // How much of the screen height the artwork covers (0.72 = 72%)
    private static final float ART_HEIGHT = 0.72f;

    // Caption colours: title brightest, details softer, hint dimmest (no pure white, which is harsh on TVs)
    private static final int CAP_TITLE = 0xF2F1F1F1;
    private static final int CAP_DETAILS = 0xBFF1F1F1;
    private static final int CAP_HINT = 0x80F1F1F1;
    private static final String GAP = "  \u00B7  ";

    private final MainActivity a;
    private final FrameLayout root;
    private final ImageView[] layers = new ImageView[2];
    private final TextView caption;
    private int front = 0;

    private final List<Item> items = new ArrayList<>();
    private int index = -1;
    private Item shown = null;
    private long loadedAt = 0;
    private boolean loading = false;
    private boolean running = false;
    private boolean onPage = true;
    private boolean hasImage = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!showing()) return;
            if (items.isEmpty() || System.currentTimeMillis() - loadedAt > REFRESH_MS) load();
            else next();
            a.handler.postDelayed(this, EVERY_MS);
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

        // A light dim over the whole picture, so the dock tiles stand out
        View dim = new View(a);
        dim.setBackgroundColor(0x330A0D14);
        art.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Darker on the left, where the tabs, weather and app names are
        View left = new View(a);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF20A0D14, 0xA60A0D14, 0x1A0A0D14, 0x000A0D14}));
        art.addView(left, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Darker along the top, behind the tabs and clock
        View top = new View(a);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB30A0D14, 0x000A0D14}));
        art.addView(top, new FrameLayout.LayoutParams(MATCH, artHeight / 3, Gravity.TOP));

        // Fades the bottom of the picture smoothly into the dark background
        View bottom = new View(a);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000A0D14, 0x990A0D14, BG}));
        art.addView(bottom, new FrameLayout.LayoutParams(MATCH, artHeight / 2, Gravity.BOTTOM));

        // One-line caption on the right, between the dock and the Watch row (position set in placeCaption)
        caption = new TextView(a);
        a.style(caption, 13, false, CAP_DETAILS);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setMaxWidth(a.dp(560));
        caption.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        caption.setAlpha(0f);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        clp.setMargins(0, a.dp(320), a.dp(48), 0);
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
    }

    /**
     * Called whenever the page changes. The wallpaper only shows on Home,
     * whatever value is passed in.
     */
    void setShownOnPage(boolean ignored) {
        boolean show = a.page == MainActivity.PAGE_HOME;
        if (onPage == show) return;
        onPage = show;
        update();
    }

    /** True while a picture is actually on screen (used to soften the colour glow). */
    boolean isShowingPicture() {
        return showing() && hasImage;
    }

    /** The film or series currently on the wallpaper, or null if it isn't showing. */
    Item currentItem() {
        return isShowingPicture() ? shown : null;
    }

    private boolean showing() {
        return running && onPage && isOn() && hasToken();
    }

    private void update() {
        a.handler.removeCallbacks(tick);
        if (!showing()) {
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
            try {
                for (int pageNo = 1; pageNo <= 2; pageNo++) {
                    JSONArray res = new org.json.JSONObject(
                            tmdbGet("/trending/all/week?language=en-US&page=" + pageNo)).getJSONArray("results");
                    for (int i = 0; i < res.length(); i++) {
                        Item it = parseItem(res.getJSONObject(i), null);
                        if (it == null || it.backdrop.isEmpty() || !seen.add(it.key())) continue;
                        found.add(it);
                    }
                }
            } catch (Exception ignored) {
            }
            Collections.shuffle(found);
            a.runOnUiThread(() -> {
                loading = false;
                if (found.isEmpty()) return;
                items.clear();
                items.addAll(found);
                loadedAt = System.currentTimeMillis();
                index = -1;
                next();
            });
        }).start();
    }

    private void next() {
        if (items.isEmpty()) return;
        index = (index + 1) % items.size();
        final Item it = items.get(index);
        // Full-quality image, no blur
        a.images.fetchInto(TMDB_IMG + "w1280" + it.backdrop, b -> {
            if (b == null || !showing()) return;
            ImageView back = layers[1 - front];
            ImageView old = layers[front];
            back.setImageBitmap(b);
            back.animate().alpha(1f).setDuration(FADE_MS).start();
            old.animate().alpha(0f).setDuration(FADE_MS).start();
            front = 1 - front;
            shown = it;

            caption.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                placeCaption();
                caption.setText(captionFor(it));
                caption.animate().alpha(1f).setDuration(700).start();
            }).start();

            if (!hasImage) {
                hasImage = true;
                root.animate().alpha(1f).setDuration(600).start();
            }
        });
    }

    /**
     * Puts the caption just below the dock, on the right. Works out the dock's height
     * the same way the Home page does, so it follows if you add or remove dock apps.
     */
    private void placeCaption() {
        int dockCount = a.layout != null ? a.layout.dock.size() : 0;
        int topDp;
        if (dockCount == 0) {
            topDp = 176 + 14;
        } else {
            int avail = a.getResources().getConfiguration().screenWidthDp - 64 - 24;
            int w = Math.min(DOCK_W, avail / dockCount - 2 * DOCK_GAP);
            if (w < 110) w = 110;
            int h = w * 9 / 16;
            // header + content padding + dock margin + band padding + tile + band padding, then a small gap
            topDp = 176 + 10 + 4 + 10 + DOCK_GAP + h + DOCK_GAP + 10 + 8;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) caption.getLayoutParams();
        lp.topMargin = a.dp(topDp);
        caption.setLayoutParams(lp);
    }

    /** "Title · ★ 8.6 · 2025 · Series · Play for info", with the title bold and the hint dimmest. */
    private CharSequence captionFor(Item it) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(it.title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(CAP_TITLE), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append(GAP).append(meta(it).replace(SEP, GAP));

        start = sb.length();
        sb.append(GAP).append("Play for info");
        sb.setSpan(new ForegroundColorSpan(CAP_HINT), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
