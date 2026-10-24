package com.jas.shieldhome;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import static com.jas.shieldhome.Ui.MATCH;
import static com.jas.shieldhome.Ui.SEP;
import static com.jas.shieldhome.Ui.WRAP;

/**
 * Home wallpaper, Netflix style: sharp trending artwork across the top of the screen,
 * fading into a dark background before the lower rows. Darker on the left and top
 * so the tabs, clock and app names stay readable. A small caption top right says
 * what it is; press Play on the remote to open its info card. Home only.
 */
final class Wallpaper {

    private static final long EVERY_MS = 30 * 1000L;
    private static final long REFRESH_MS = 3 * 60 * 60 * 1000L;
    private static final long FADE_MS = 1500L;

    // The dark colour the artwork fades into
    private static final int BG = 0xFF0A0D14;

    // How much of the screen height the artwork covers (0.72 = 72%)
    private static final float ART_HEIGHT = 0.72f;

    private final MainActivity a;
    private final FrameLayout root;
    private final ImageView[] layers = new ImageView[2];
    private final LinearLayout caption;
    private final TextView captionTitle, captionMeta;
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

        // Caption, top right under the clock: what the artwork is from
        caption = new LinearLayout(a);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setGravity(Gravity.END);
        caption.setAlpha(0f);
        captionTitle = new TextView(a);
        a.style(captionTitle, 16, true, 0xE6FFFFFF);
        captionTitle.setSingleLine(true);
        captionTitle.setEllipsize(TextUtils.TruncateAt.END);
        captionTitle.setMaxWidth(a.dp(420));
        captionTitle.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        captionMeta = new TextView(a);
        a.style(captionMeta, 12, false, 0xB3FFFFFF);
        captionMeta.setSingleLine(true);
        captionMeta.setPadding(0, a.dp(2), 0, 0);
        captionMeta.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        caption.addView(captionTitle, new LinearLayout.LayoutParams(WRAP, WRAP));
        caption.addView(captionMeta, new LinearLayout.LayoutParams(WRAP, WRAP));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        clp.setMargins(0, a.dp(136), a.dp(48), 0);
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
                captionTitle.setText(it.title);
                captionMeta.setText(meta(it) + SEP + "Press Play for info");
                caption.animate().alpha(1f).setDuration(700).start();
            }).start();

            if (!hasImage) {
                hasImage = true;
                root.animate().alpha(1f).setDuration(600).start();
            }
        });
    }
}
